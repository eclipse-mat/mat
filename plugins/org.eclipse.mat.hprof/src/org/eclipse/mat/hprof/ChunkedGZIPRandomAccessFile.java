/*******************************************************************************
 * Copyright (c) 2020 SAP SE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP SE (Ralf Schmelter) - Initial implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * This class can be used to get random access to chunked gzipped hprof files like the
 * openjdk can create them. As described in https://tools.ietf.org/html/rfc1952, a gzip
 * file consists of a number of "members". The openjdk gzipped dumps have a maximum size
 * of data compressed in each "member". This makes it possible to start decompression
 * at each start of a member. In case of the openjdk dumps the maximum size of uncompressed
 * data in a "member" is stored in a comment in the first member. This is used to detect
 * those files (without having to uncompress the whole file).
 *
 * @author Ralf Schmelter
 */
public class ChunkedGZIPRandomAccessFile extends RandomAccessFile
{
    // A comparator which compares chunks by their file offset.
    private static FileOffsetComparator fileOffsetComp = new FileOffsetComparator();

    // A comparator which compares chunks by their offset.
    private static OffsetComparator offsetComp = new OffsetComparator();

    // The size to use when reading from the random access file.
    private static final int READ_SIZE = 65536;

    // The global cache for offset mappings. We don't want to create mappings if not needed,
    // since we don't want to throw away the information we already gathered while reading
    // from the file. Note that we only use 8 bytes per chunk, so this generally don't uses
    // much memory.
    private static final HashMap<File, StoredOffsetMapping> cachedOffsets = new HashMap<>();

    // The last used buffer.
    private Buffer last;

    // The length of the random access file.
    private final long fileSize;

    // The last modification time when we opened the file.
    private final long modTime;

    // The file name we used.
    private final File file;

    // The maximum size of a buffer cache.
    private final int cacheSize;

    // The maximum numbers of buffers to cache.
    private final int maxCachedBuffers;

    // A buffer used to read from the file.
    private final byte[] in;

    // A sorted list of the buffers we know so far.
    private final ArrayList<Buffer> buffers;

    // The inflater to use.
    private final Inflater inf;

    // The head of the list which contains the buffers with cached data.
    private final Buffer cacheHead;

    // The number of cached buffers in the list.
    private int cachedBuffers;

    // The current position
    private long pos;

    /**
     * Creates the file.
     *
     * @param file The file name.
     * @param bufferSize The maximum size of uncompressed chunks.
     * @param maxCachedBuffers The maximum number of buffers to cache.
     * @throws FileNotFoundException When the file could not be found.
     * @throws IOException On other IO errors.
     */
    private ChunkedGZIPRandomAccessFile(File file, int bufferSize, int maxCachedBuffers) throws FileNotFoundException, IOException
    {
        super(file, "r"); //$NON-NLS-1$

        this.file = file;
        this.last = null;
        this.pos = 0;
        this.fileSize = super.length();
        this.modTime = file.lastModified();
        this.cacheSize = bufferSize;
        this.maxCachedBuffers = maxCachedBuffers;
        this.cachedBuffers = 0;
        this.in = new byte[READ_SIZE];
        this.buffers = new ArrayList<>();
        this.inf = new Inflater(true);
        this.cacheHead = new Buffer(-1, -1);
        this.cacheHead.setNext(cacheHead);
        this.cacheHead.setPrev(cacheHead);
        this.buffers.add(new Buffer(0, 0));
    }

    /**
     * Creates the file.
     *
     * @param file The file name.
     * @param mapping The offset mapping used from a previous use.
     * @param maxCachedBuffers The maximum number of buffers to cache.
     * @throws FileNotFoundException When the file could not be found.
     * @throws IOException On other IO errors.
     */
    private ChunkedGZIPRandomAccessFile(File file, StoredOffsetMapping mapping, int maxCachedBuffers)
                    throws FileNotFoundException, IOException
    {
        super(file, "r"); //$NON-NLS-1$

        this.file = file;
        this.last = null;
        this.pos = 0;
        this.fileSize = super.length();
        this.modTime = file.lastModified();
        this.cacheSize = mapping.bufferSize;
        this.maxCachedBuffers = maxCachedBuffers;
        this.cachedBuffers = 0;
        this.in = new byte[READ_SIZE];
        this.inf = new Inflater(true);
        this.cacheHead = new Buffer(-1, -1);
        this.cacheHead.setNext(cacheHead);
        this.cacheHead.setPrev(cacheHead);

        ArrayList<Buffer> previousBuffers = mapping.getBuffers();

        if (previousBuffers != null)
        {
            this.buffers = previousBuffers;
        }
        else
        {
            this.buffers = new ArrayList<>();
            this.buffers.add(new Buffer(0, 0));
        }
    }

    @Override
    public void seek(long pos) throws IOException
    {
        if (pos < 0)
        {
            throw new IOException();
        }

        this.pos = pos;
    }

    @Override
    public long getFilePointer()
    {
        return pos;
    }

    /**
     * Unknown length is Long.MAX_VALUE
     */
    @Override
    public long length()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int result = read(b, 0, 1);

        if (result == 1)
        {
            return b[0] & 0xff;
        }

        return -1;
    }

    @Override
    public int read(byte buf[]) throws IOException
    {
       return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte buf[], int off, int len) throws IOException
    {
        int result = read(pos, buf, off, len);

        if (result > 0)
        {
            pos += result;
        }

        return result;
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            reuseMapping(file, new StoredOffsetMapping(buffers, cacheSize, fileSize, modTime));
        }
        finally
        {
            super.close();
        }
    }

    /**
     * Returns an estimation of the last physical position we read from.
     *
     * @return The last physical position.
     */
    public synchronized long getLastPhysicalReadPosition()
    {
        if (last != null)
        {
            return last.fileOffset;
        }

        return 0;
    }

    /**
     * Reads bytes from the gzip file.
     *
     * @param offset The offset from which to start the read.
     * @param b The array to read into.
     * @param off The offset in the array to use.
     * @param len The number of bytes to read at most.
     * @return The number of bytes read or -1 if we are at the end of the file.
     * @throws IOException On error.
     */
    public synchronized int read(long offset, byte[] b, int off, int len) throws IOException
    {
        Buffer buf = last;

        while (buf == null || (buf.getOffset() > offset) || (buf.getOffset() + buf.getCacheLen() <= offset))
        {
            int pos = Collections.binarySearch(buffers, new Buffer(0, offset), offsetComp);
            buf = buffers.get(pos >= 0 ? pos : -pos - 2);

            if (buf.getFileOffset() >= fileSize)
            {
                return -1;
            }

            if (buf.getCache() != null)
            {
                // If already loaded, move to front of the cache list.
                last = buf;

                if (cacheHead.getNext() != buf)
                {
                    remove(buf);
                    addFirst(buf);
                }
            }
            else
            {
                try
                {
                    // Note that the load will also add the following buffer to the list,
                    // so the while loop will eventually terminate.
                    loadBuffer(buf);

                    // Check if the buffer is empty, since we are at the end.
                    if (buf.getCacheLen() == 0)
                    {
                        return -1;
                    }
                }
                catch (DataFormatException e)
                {
                    throw new IOException(e);
                }
            }
        }

        int copyOffset = (int) (offset - buf.getOffset());
        int toCopyMax = buf.getCacheLen() - copyOffset;
        int toCopy = Math.min(toCopyMax, len);

        if (toCopy <= 0)
        {
            return -1;
        }

        System.arraycopy(buf.getCache(), copyOffset, b, off, toCopy);

        return toCopy;
    }

    /**
     * Checks if the given file is a chunked gzipped file.
     *
     * @param raf The file to check.
     * @return <code>true</code> if the file is a chunked gzip file.
     * @throws IOException On error.
     */
    public static boolean isChunkedGZIPFile(RandomAccessFile raf) throws IOException
    {
        return getChunkSize(raf) > 0;
    }

    /**
     * Returns the chunk size found in the gzipped file or -1.
     *
     * @param raf The file.
     * @return The chunk size or -1.
     * @throws IOException On error.
     */
    private static int getChunkSize(RandomAccessFile raf) throws IOException
    {
        long oldPos = raf.getFilePointer();

        try
        {
            raf.seek(0);
            int header = raf.readInt();

            if ((header >>> 8) != 0x1f8b08)
            {
                // Not gzip with deflate.
                return -1;
            }

            if ((header & 16) == 0)
            {
                // No comment
                return -1;
            }

            raf.readInt(); // timestamp
            raf.readChar(); // Extra flags and os.

            if ((header & 4) != 0)
            {
                // Skip extra flags.
                raf.skipBytes(raf.read() + (raf.read() << 256));
            }

            // Skip name
            if ((header & 8) != 0)
            {
                // Wait for the last 0.
                while (raf.read() != 0);
            }

            // Read the comment.
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int b;

            while ((b = raf.read()) > 0)
            {
                bos.write(b);
            }

            // Check if the block size is included in the comment.
            String comment = bos.toString("UTF-8"); //$NON-NLS-1$
            String expectedPrefix = "HPROF BLOCKSIZE="; //$NON-NLS-1$

            if (comment.startsWith(expectedPrefix))
            {
                String chunkSizeStr = comment.substring(expectedPrefix.length()).split(" ")[0]; //$NON-NLS-1$

                try
                {
                    int chunkSize = Integer.parseInt(chunkSizeStr);

                    if (chunkSize > 0)
                    {
                        return chunkSize;
                    }
                }
                catch (NumberFormatException e)
                {
                    // Could not parse.
                }
            }

            return -1;
        }
        finally
        {
            raf.seek(oldPos);
        }
    }

    /**
     * Returns the random access file for the given file or <code>null</code> if not
     * supported for the file.
     *
     * @param raf The random access file.
     * @param file The file name.
     * @return The random access file or <code>null</code>.
     */
    public static synchronized ChunkedGZIPRandomAccessFile get(RandomAccessFile raf, File file)
            throws IOException
    {
        // Maybe move these to a preference.
        int cacheSizeInMB = 5;
        int maxStoredMappings = 5;
        StoredOffsetMapping mapping = cachedOffsets.get(file.getAbsoluteFile());

        // Remove the oldest mapping if we exceed the limit.
        while (cachedOffsets.size() > Math.max(1, maxStoredMappings))
        {
            long oldestTime = Long.MAX_VALUE;
            File oldestFile = null;

            for (File f: cachedOffsets.keySet())
            {
                long entryTime = cachedOffsets.get(f).getCreationDate();

                if (entryTime < oldestTime)
                {
                    oldestFile = f;
                    oldestTime = entryTime;
                }
            }

            if (oldestFile != null)
            {
                cachedOffsets.remove(oldestFile);
            }
        }

        int chunkSize = getChunkSize(raf);

        if (chunkSize > 0)
        {
            long nrOfChunks = Math.max(1, Math.min(1000, cacheSizeInMB * 1024L * 1024L / chunkSize));
            long fileSize = file.length();
            long modTime = file.lastModified();

            if ((mapping != null) && (mapping.getFileSize() == fileSize) &&
                (mapping.getBufferSize() == chunkSize) &&  (mapping.getLastModTime() == modTime))
            {
                return new ChunkedGZIPRandomAccessFile(file, mapping, (int) nrOfChunks);
            }
            else
            {
                cachedOffsets.remove(file.getAbsoluteFile());
                return new ChunkedGZIPRandomAccessFile(file, chunkSize, (int) nrOfChunks);
            }
        }

        return null;
    }

    /**
     * Forget all cached version of the file.
     *
     * @param file The file to forget.
     */
    public static synchronized void forget(File file)
    {
        cachedOffsets.remove(file.getAbsoluteFile());
    }

    /**
     * Compressed a file to a chunked gzipped file.
     *
     * @param toCompress The file to gzip.
     * @param compressed The gzipped file.
     * @throws IOException On error.
     */
    public static void compressFileChunked(File toCompress, File compressed) throws IOException
    {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        CRC32 crc = new CRC32();
        boolean finished = false;
        int chunkSize = 1024 * 1024;
        String comment = "HPROF BLOCKSIZE=" + chunkSize; //$NON-NLS-1$
        boolean writtenComment = false;
        byte[] readBuf = new byte[chunkSize];
        byte[] defaultHeader = new byte[] {
           (byte) 0x1f, (byte) 0x8b, (byte) 8, 0, 0, 0, 0, 0, 0, 0
        };

        try (InputStream is = new BufferedInputStream(new FileInputStream(toCompress), 64 * 1024);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(compressed), 64 * 1024))
        {
            while (!finished)
            {
                def.reset();
                crc.reset();

                // GZipOutputStream does not supports adding a comment, so we have to create
                // the gzip format by hand.
                if (writtenComment)
                {
                    os.write(defaultHeader);
                }
                else
                {
                    os.write(defaultHeader, 0, 3);
                    os.write(16); // We have a comment.
                    os.write(defaultHeader, 4, 6);
                    os.write(comment.getBytes(StandardCharsets.US_ASCII));
                    os.write(0); // Zero terminate comment
                    writtenComment = true;
                }

                int left = chunkSize;
                DeflaterOutputStream dos = new DeflaterOutputStream(os, def, 65536);

                while (left > 0)
                {
                    int read = is.read(readBuf, 0, left);

                    if (read <= 0)
                    {
                        finished = true;
                        break;
                    }

                    dos.write(readBuf, 0, read);
                    crc.update(readBuf, 0, read);
                    left -= read;
                }

                dos.finish();

                int crcVal = (int) crc.getValue();
                writeInt(crcVal, os);
                writeInt(chunkSize - left, os);
            }
        }
        finally
        {
            def.finish();
        }
    }

    /**
     * Writes an integer in little endian format.
     *
     * @param val The value to write.
     * @param os The stream to write to.
     * @throws IOException When writing failed.
     */
    private static void writeInt(int val, OutputStream os) throws IOException
    {
        os.write((byte) (val & 0xff));
        os.write((byte) ((val >> 8) & 0xff));
        os.write((byte) ((val >> 16) & 0xff));
        os.write((byte) ((val >> 24) & 0xff));
    }

    /**
     * Reuses the mapping of the file if it contains more buffer than the currently best one.
     *
     * @param file The file to reuse.
     * @param mapping The mapping to reuse
     */
    private static synchronized void reuseMapping(File file, StoredOffsetMapping mapping)
    {
        StoredOffsetMapping oldMapping = cachedOffsets.get(file.getAbsoluteFile());

        if ((oldMapping == null) || oldMapping.shouldBeReplacedBy(mapping))
        {
            cachedOffsets.put(file.getAbsoluteFile(), mapping);
        }
    }

    // Loads the content of a buffer. If this is the first time the buffer is
    // loaded, the next buffer is added too (but not loaded).
    private void loadBuffer(Buffer buf) throws IOException, DataFormatException
    {
        // If we have used all caches, take a cache from the least recently used cached buffer.
        if (cachedBuffers >= maxCachedBuffers)
        {
            Buffer toRemove = cacheHead.getPrev();
            remove(toRemove);
            buf.setCache(toRemove.getCache());
            toRemove.setCache(null);
        }
        else
        {
            // Otherwise allocate a new cache.
            buf.setCache(new byte[cacheSize]);
            cachedBuffers += 1;
        }

        // Move to front of LRU list.
        last = buf;
        addFirst(buf);

        // Fill in the cache
        inf.reset();
        super.seek(buf.getFileOffset());

        int read = super.read(in, 0, READ_SIZE);

        if (read == -1)
        {
            // We are at the end.
            buf.setCacheLen(0);
            return;
        }

        int inCount = Math.max(4, read);
        int outCount = 0;

        // Skip header, but check at least a little
        if (read < 4)
        {
            // We must read at least 4 bytes.
            super.readFully(in, read, 4 - read);
            read = 4;
        }

        if ((in[0] != 0x1f) || ((in[1] & 0xff) != 0x8b))
        {
            throw new IOException("Missing gzip id"); //$NON-NLS-1$
        }

        if (in[2] != 8)
        {
            throw new IOException("Only supports deflate"); //$NON-NLS-1$
        }

        int off = 10;

        // Extras
        if ((in[3] & 4) != 0)
        {
            int len = (in[off + 1] & 0xff) * 256 + (in[off] & 0xff);
            off += 2 + len;
        }

        // Name
        if ((in[3] & 8) != 0)
        {
            int len = 0;

            while (in[off + len] != 0)
            {
                ++len;
            }

            off += len + 1;
        }

        // Comment
        if ((in[3] & 16) != 0)
        {
            int len = 0;

            while (in[off + len] != 0)
            {
                ++len;
            }

            off += len + 1;
        }

        // Header CRC
        if ((in[3] & 2) != 0)
        {
            off += 2;
        }

        inf.setInput(in, off, read - off);
        outCount = inf.inflate(buf.getCache(), 0, buf.getCache().length);

        while (!inf.finished())
        {
            if (inf.needsInput())
            {
                read = super.read(in, 0, READ_SIZE);
                inf.setInput(in, 0, read);
                inCount += read;
            }

            outCount += inf.inflate(buf.getCache(), outCount, buf.getCache().length - outCount);
        }

        // Add the following buffer too if needed.
        if ((inf.getRemaining() != 0) || (inCount + buf.getFileOffset() + 8 != fileSize))
        {
            long nextFileOffset = inCount - inf.getRemaining() + buf.getFileOffset() + 8 /* CRC */;
            long nextOffset = outCount + buf.getOffset();

            Buffer nextChunk = new Buffer(nextFileOffset, nextOffset);
            int pos = Collections.binarySearch(buffers, nextChunk, fileOffsetComp);

            if (pos < 0)
            {
                buffers.add(-pos - 1, nextChunk);
            }
        }

        buf.setCacheLen(outCount);
    }

    // Adds the buffer to the front of the LRU list.
    private void addFirst(Buffer buf)
    {
        assert buf.getNext() == null;
        assert buf.getPrev() == null;
        assert buf.getCache() != null;

        if (cacheHead.getPrev() == cacheHead)
        {
            cacheHead.setPrev(buf);
        }

        cacheHead.getNext().setPrev(buf);
        buf.setNext(cacheHead.getNext());
        buf.setPrev(cacheHead);
        cacheHead.setNext(buf);
    }

    // Removes the buffer from the LRU list.
    private void remove(Buffer buf)
    {
        assert buf.getPrev() != null;
        assert buf.getNext() != null;
        assert buf.getCache() != null;
        assert cacheHead.getPrev() != cacheHead;

        buf.getPrev().setNext(buf.getNext());
        buf.getNext().setPrev(buf.getPrev());
        buf.setNext(null);
        buf.setPrev(null);
    }

    // Represents a gzipped buffer. The gzipped hprof file consists of a list of these buffers.
    private static class Buffer
    {
        // Since only a few buffers will have content, we factor it out and only allocated
        // it for buffers with content.
        private static class BufferContent
        {
            public byte[] cache;
            public int cacheLen;
            public Buffer next;
            public Buffer prev;
        }

        private final long fileOffset;
        private final long offset;
        private BufferContent content;

        public Buffer(long fileOffset, long offset)
        {
            this.fileOffset = fileOffset;
            this.offset = offset;
            this.content = null;
        }

        private void removeContentIfPossible()
        {
            if ((content.next == null) && (content.prev == null) && (content.cache == null)) {
                content = null;
            }
        }

        public Buffer getNext()
        {
            if (content != null)
            {
                return content.next;
            }

            return null;
        }

        public void setNext(Buffer next)
        {
            if (next == null)
            {
                if (content != null)
                {
                    content.next = null;
                    removeContentIfPossible();
                }

                return;
            }

            if (content == null)
            {
                content = new BufferContent();
            }

            content.next = next;
        }

        public Buffer getPrev()
        {
            if (content != null)
            {
                return content.prev;
            }

            return null;
        }

        public void setPrev(Buffer prev)
        {
            if (prev == null)
            {
                if (content != null) {
                    content.prev = null;
                    removeContentIfPossible();
                }

                return;
            }

            if (content == null)
            {
                content = new BufferContent();
            }

            content.prev = prev;
        }

        public byte[] getCache()
        {
            if (content != null)
            {
                return content.cache;
            }

            return null;
        }

        public void setCache(byte[] cache)
        {
            if (cache == null)
            {
                if (content != null)
                {
                    content.cache = null;
                    removeContentIfPossible();
                }

                return;
            }

            if (content == null)
            {
                content = new BufferContent();
            }

            content.cache = cache;
        }

        public int getCacheLen()
        {
            if (content != null)
            {
                return content.cacheLen;
            }

            return 0;
        }

        public void setCacheLen(int cacheLen)
        {
            if ((cacheLen == 0) && (content == null)) {
                return;
            }

            content.cacheLen = cacheLen;
        }

        public long getFileOffset()
        {
            return fileOffset;
        }

        public long getOffset()
        {
            return offset;
        }
    }

    // This is used to get a memory saving version of the offset mapping used.
    private static class StoredOffsetMapping
    {
        private final int[] lengths;
        private final int[] fileLengths;
        private final int bufferSize;
        private final long fileSize;
        private final long lastModTime;
        private final long creationDate;

        public StoredOffsetMapping(List<Buffer> buffers, int bufferSize,
                        long fileSize, long lastModTime)
        {
            this.bufferSize = bufferSize;
            this.fileSize = fileSize;
            this.lastModTime = lastModTime;
            this.creationDate = System.currentTimeMillis();

            long lastFileOffset = 0;
            long lastOffset = 0;
            long overflowCheck = 0;
            int[] lengths = new int[buffers.size()];
            int[] fileLengths = new int[buffers.size()];

            for (int i = 0; i < lengths.length; ++i)
            {
                Buffer b = buffers.get(i);
                long fileChunkSize = b.getFileOffset() - lastFileOffset;
                long chunkSize = b.getOffset() - lastOffset;
                overflowCheck |= fileChunkSize | chunkSize;
                lastFileOffset = b.getFileOffset();
                lastOffset = b.getOffset();
                fileLengths[i] = (int) fileChunkSize;
                lengths[i] = (int) chunkSize;
            }

            if (overflowCheck > Integer.MAX_VALUE)
            {
                // Cannot be compressed. This should normally never happen as long
                // as the chunk sizes are lower than ~2GB, since even the most
                // incompressible data is not much blown up by gzip.
                this.fileLengths = null;
                this.lengths = null;
            }
            else
            {
                this.fileLengths = fileLengths;
                this.lengths = lengths;
            }
        }

        /**
         * Returns the time stamp the mapping was created.
         *
         * @return the time stamp the mapping was created.
         */
        public long getCreationDate()
        {
            return creationDate;
        }

        /**
         * Returns <code>true</code> if this mapping should be replaced by the other mapping.
         *
         * @param other The other mapping.
         * @return <code>true</code> if this mapping should be replaced.
         */
        public boolean shouldBeReplacedBy(StoredOffsetMapping other)
        {
            if ((other.fileSize != fileSize) || (other.lastModTime != lastModTime))
            {
                return true;  // File has changed.
            }

            // Replace if the other mapping has more information.
            return lengths.length < other.lengths.length;
        }

        /**
         * Returns the buffer size used.
         *
         * @return The buffer size used.
         */
        public int getBufferSize()
        {
            return bufferSize;
        }

        /**
         * Returns the size of the file for which this was created.
         *
         * @return The file size.
         */
        public long getFileSize()
        {
            return fileSize;
        }

        /**
         * Returns the last modification time of the file for which this is created.
         *
         * @return The last modification time.
         */
        public long getLastModTime()
        {
            return lastModTime;
        }

        /**
         * Returns the buffers or <code>null</code> if they could not be compressed.
         *
         * @return The buffers or <code>null</code>.
         */
        public ArrayList<Buffer> getBuffers()
        {
            if (lengths == null)
            {
                return null;
            }

            ArrayList<Buffer> result = new ArrayList<ChunkedGZIPRandomAccessFile.Buffer>(lengths.length);
            long lastFileOffset = 0;
            long lastOffset = 0;

            for (int i = 0; i < lengths.length; ++i)
            {
                int fileSize = fileLengths[i];
                int size = lengths[i];
                lastFileOffset += fileSize;
                lastOffset += size;
                result.add(new Buffer(lastFileOffset, lastOffset));
            }

            return result;
        }
    }

    // Compares chunks by file offset.
    private static class FileOffsetComparator implements Comparator<Buffer>
    {

        @Override
        public int compare(Buffer x, Buffer y)
        {
            return Long.compare(x.getFileOffset(), y.getFileOffset());
        }
    }

    // Compares chunks by offset.
    private static class OffsetComparator implements Comparator<Buffer>
    {

        @Override
        public int compare(Buffer x, Buffer y)
        {
            return Long.compare(x.getOffset(), y.getOffset());
        }
    }
}
