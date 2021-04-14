/*******************************************************************************
 * Copyright (c) 2019,2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.nio.channels.SeekableByteChannel;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.util.MessageUtil;

/**
 * Used to wrap an non-seekable stream to make it seekable.
 * Constructed with a supplier of the non-seekable stream (which could be a decompression
 * stream or decryption stream). Multiple instances of this stream are then constructed
 * and each starts at the beginning.
 * Optionally supplied underlying seekable {@link RandomAccessInputStream} stream.
 * This is used if the supplier does
 * not provided a fresh underlying stream each  time. A seek is used to move the underlying
 * stream to the correct position when switching between unseekable streams. In this
 * case the supplier of non-seekable streams should wrap the innermost stream from
 * the underlying seekable stream with a {@link UnclosableInputStream} so that the
 * non-seekable streams can be closed (to release resources) without closing the
 * underlying seekable.
 */
public class SeekableStream extends InputStream implements Closeable, AutoCloseable
{
    /**
     * Used to wrap and indicate a underlying stream which has a position and has seek().
     * {@link FilterInputStream#close()} is called when {@link SeekableStream#close()}
     * is called.
     */
    public abstract static class RandomAccessInputStream extends FilterInputStream
    {
        /**
         * A wrapper stream for a random access stream.
         * @param in
         */
        protected RandomAccessInputStream(InputStream in)
        {
            super(in);
        }

        /**
         * Get the current position in the stream.
         * @return
         * @throws IOException
         */
        abstract long position() throws IOException;

        /**
         * Set the current position.
         * @param newpos
         * @throws IOException
         */
        abstract void seek(long newpos) throws IOException;
    }

    /**
     * Wraps an {@link InputStream} so that
     * close does not propagate to the underlying stream.
     */
    public static class UnclosableInputStream extends FilterInputStream
    {

        public UnclosableInputStream(InputStream in)
        {
            super(in);
        }

        public void close()
        {
            // Do nothing
        }
    }

    /**
     * Internal class to hold position of a decompression stream.
     * Uses a {@link java.lang.ref.SoftReference} to hold the stream
     * when not in use so that the stream can be freed when memory is short.
     */
    static class PosStream extends FilterInputStream implements Comparable<PosStream>
    {
        protected PosStream(InputStream in, long seq1)
        {
            super(in);
            seq = seq1;
            savedStream = new SoftReference<InputStream>(in);
        }

        /**
         * Dummy for searching in TreeSet
         * @param pos
         * @param seq
         */
        protected PosStream(long pos, long seq1)
        {
            super(null);
            this.pos = pos;
            seq = seq1;
        }

        /**
         * Create a copy of the PosStream.
         * Requires the underlying stream can have its state duplicated
         * in another copy.
         * @param seq
         * @return the copy or null if no copy is possible
         * @throws IOException
         */
        protected PosStream copy(long seq) throws IOException
        {
            if (in instanceof GZIPInputStream2)
            {
                PosStream ret = new PosStream(new GZIPInputStream2((GZIPInputStream2)in), seq);
                ret.basepos = basepos;
                ret.pos = pos;
                return ret;
            }
            return null;
        }

        /** Position in stream */
        private long pos;
        /**
         * Sequence number. Unique,
         * allows more than one PosStream with the same
         * position in the TreeSet.
         */
        long seq;
        /** The non-seekable stream */
        //InputStream is;
        /** Position in the base stream when not in use. */
        long basepos;
        boolean eof;
        /** Used to save the stream when not in active use */
        SoftReference<InputStream> savedStream;
        /** previously used PosStream - used for LRU */
        PosStream prev;
        /** more recently used PosStream - used for LRU */
        PosStream next;
        /** Used to tweek LRU */
        long decay;

        @Override
        public int read() throws IOException
        {
            int r = super.read();
            if (r != -1)
                ++pos;
            else
                eof = true;
            return r;
        }

        @Override
        public int read(byte buf[], int off, int len) throws IOException
        {
            int r = super.read(buf, off, len);
            if (r != -1)
                pos += r;
            else
                eof = true;
            return r;
        }

        @Override
        public int compareTo(PosStream o)
        {
            if (pos < o.pos)
                return -1;
            else if (pos > o.pos)
                return 1;
            else
                return Long.compare(seq, o.seq);
        }

        public boolean equals(Object o)
        {
            return o instanceof PosStream && compareTo((PosStream)o) == 0;
        }

        public int hashCode()
        {
            return (int)pos ^ (int)seq;
        }

        public String toString()
        {
            return super.toString() + " " + pos + " (" + seq + ")" + (eof?"EOF" : "");  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }

        long position()
        {
            return pos;
        }

        void position(long pos)
        {
            this.pos = pos;
        }

        /**
         * Sets the stream available or not available for use.
         * @param state true if required to be active
         * @return if now active ({@link #read()} etc. can work,
         * false if the SoftReference has been cleared and the stream
         * cannot be used.
         */
        boolean setActive(boolean state)
        {
            if (state)
            {
                if (savedStream == null)
                    return false;
                in = savedStream.get();
                return in != null;
            }
            else
            {
                //in = null;
                //return false;
                return true;
            }
        }

        /**
         * Has the SoftReference been cleared
         * @return true if it has been cleared and is no longer usable.
         */
        boolean isCleared()
        {
            return savedStream.get() == null;
        }

        @Override
        public void close() throws IOException
        {
            if (setActive(true))
            {
                super.close();
            }
        }

        @Override
        protected void finalize() throws Throwable
        {
            try
            {
                close();
            }
            finally
            {
                super.finalize();
            }
        }
    }

    /** Supplier of the non-seekable streams */
    Supplier<InputStream> genstream;
    /** A sequence number to ensure that the PosStreams are different */
    long nextseq = 0;
    /** Used to see how often to clean up the cache */
    int cleanup;
    /** Keeps the different unseekable streams in order of position */
    TreeSet<PosStream> ts = new TreeSet<PosStream>();
    /** Oldest used PosStream */
    PosStream tail = new PosStream(0, -2);
    /** Latest used PosStream */
    PosStream head = new PosStream(Long.MAX_VALUE, -1);
    {
        // Initialize sentinels
        tail.next = head;
        head.prev = tail;
    }
    /** The current stream used for reading */
    PosStream current;
    /** How many streams to keep active */
    int cachesize;
    /** Estimate of uncompressed length */
    long estlen;
    /** The last position if the current stream has been closed */
    long lastpos;
    /** Total seek time */
    long totalseek;
    /**
     * The underlying random access stream which all of the decompression
     * streams use. When switching streams seek() will be used to switch
     * the position of this stream.
     */
    RandomAccessInputStream underlying;
    /**
     * Alternative underlying channel with position and seek.
     */
    SeekableByteChannel underlyingChannel;
    private final boolean verbose = Platform.inDebugMode() && HprofPlugin.getDefault().isDebugging()
                    && Boolean.parseBoolean(Platform.getDebugOption("org.eclipse.mat.hprof/debug/gzip")); //$NON-NLS-1$
    /**
     * Reopening the file and reading to location 0 with GZip2
     * has a cost of seeking roughly this far.
     */
    private static final long READ0COST = 80000;
    /**
     * Creates a seekable stream out of a non-seekable stream.
     *
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource,
     *            {@link #underlying}.
     *            The streams are closed when no longer needed.
     *            Wrap the innermost stream with {@link UnclosableStream}
     *            to avoid closing the underlying stream when one of the
     *            non-seekable streams is closed by this class.
     * @param underlying
     *            The underlying seekable stream.
     *            Avoids having to open a file multiple times if a seek can be used instead.
     * @param cachesize number of seekable streams to use
     * @param estlen estimate of length
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, RandomAccessInputStream underlying, int cachesize, long estlen) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        this.estlen = estlen;
        this.underlying = underlying;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Slowly reduce the clean up rate as the cache gets bigger
     * to stop it taking too long.
     * @return
     */
    private int initcleanup()
    {
        return (int)Math.pow(cachesize + 1, 0.75);
    }

    /**
     * Creates a seekable stream out of a non-seekable stream.
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource.
     *            The streams are closed when no longer needed.
     *            Wrap the innermost stream with {@link UnclosableStream}
     *            to avoid closing the underlying stream when one of the
     *            non-seekable streams is closed by this class.
     * @param underlying
     *            The underlying seekable stream.
     *            Avoids having to open a file multiple times if a seek can be used instead.
     * @param cachesize number of seekable streams to use
     * @param estlen estimate of uncompressed length
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, SeekableByteChannel underlying, int cachesize, long estlen) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        this.estlen = estlen;
        this.underlyingChannel = underlying;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Creates a seekable stream out of a non-seekable stream.
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource.
     *            The streams are closed when no longer needed.
     * @param cachesize number of seekable streams to use
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, int cachesize) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Get the position of the underlying stream.
     * Used when switching between non-seekable streams.
     * @return
     * @throws IOException
     */
    long underlyingPosition() throws IOException
    {
        if (underlying != null)
            return underlying.position();
        else if (underlyingChannel != null)
            return underlyingChannel.position();
        else
            return -1;
    }

    /**
     * Set the position of the underlying stream.
     * Used when switching between non-seekable streams.
     * @param pos
     * @throws IOException
     */
    void underlyingPosition(long pos) throws IOException
    {
        if (underlying != null)
            underlying.seek(pos);
        else if (underlyingChannel != null)
            underlyingChannel.position(pos);
    }

    /**
     * Close the underlying stream.
     * @return true if a stream was closed
     * @throws IOException
     */
    boolean underlyingClose() throws IOException
    {
        if (underlying != null)
        {
            underlying.close();
            return true;
        }
        else if (underlyingChannel != null)
        {
            underlyingChannel.close();
            return true;
        }
        else
            return false;
    }

    /**
     * Close a non-seekable stream
     * @param pos
     * @throws IOException
     */
    void streamClose(PosStream pos) throws IOException
    {
        pos.close();
    }

   boolean underlying()
    {
        if (underlying != null)
        {
            return true;
        }
        else if (underlyingChannel != null)
        {
            return true;
        }
        else
            return false;
    }

    /**
     * Add a currently unused PosStream to
     * the cache.
     * Maintains the LRU for the cache.
     * @param toAdd
     * @return If added (i.e. doesn't already exist)
     */
    boolean add(PosStream toAdd)
    {
        if (ts.add(toAdd))
        {
            toAdd.prev = head.prev;
            toAdd.next = head;
            toAdd.decay = 0;
            head.prev.next = toAdd;
            head.prev = toAdd;
            return true;
        }
        else
        {
            // Move existing to front
            PosStream existing = ts.floor(toAdd);
            if (existing != null)
            {
                // Move to front of LRU
                existing.prev.next = existing.next;
                existing.next.prev = existing.prev;
                existing.prev = head.prev;
                existing.next = head;
                existing.decay = 0;
                head.prev.next = existing;
                head.prev = existing;
            }
            return false;
        }
    }

    /**
     * Removes a PosStream from the cache, ready
     * for use or discard.
     * Maintains the LRU data for the cache.
     * @param toRemove
     * @return True if the item was in the cache.
     */
    boolean remove(PosStream toRemove)
    {
        if (ts.remove(toRemove))
        {
            toRemove.prev.next = toRemove.next;
            toRemove.next.prev = toRemove.prev;
            toRemove.prev = null;
            toRemove.next = null;
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Remove an entry in the decompression stream cache.
     * @throws IOException
     */
    void clearEntry() throws IOException
    {
        // clear an entry
        if (ts.isEmpty())
            return;
        if (nextseq % cleanup == 0)
        {
            clearClosest();
            // Occasionally make some extra space
            clearClosest();
            return;
        }
        /*
         * Find a PosStream which has not been used for a while
         * and is not too big.
         */
        int iterations = 10;
        if (iterations <= 1)
        {
            PosStream toremove = tail.next;
            if (remove(toremove))
            {
                streamClose(toremove);
                return;
            }
        }
        PosStream test = tail.next;
        PosStream toremove = null;
        int factor = 10;
        // A PosStream covering a gap this or smaller is okay
        long okaygap;
        if (lastpos > 0)
            okaygap = lastpos / cachesize * factor;
        else if (estlen > 0)
            okaygap = estlen / cachesize * factor;
        else
            okaygap = 0;
        long bestgap = Long.MAX_VALUE;
        long firstgap = 0;
        for (int i = 0; i < iterations && test != head && bestgap > okaygap; ++i, test = test.next)
        {
            PosStream prev = ts.lower(test);
            long gap = prev == null ? test.pos + READ0COST : test.pos - prev.pos;
            /*
             * A simple cost-sensitive cache replacement algorithm.
             * If this item gets skipped over multiple times,
             * make it more likely to be evicted.
             */
            gap -= test.decay;
            if (gap < bestgap)
            {
                bestgap = gap;
                toremove = test;
            }
            if (i == 0)
                firstgap = gap;
            //System.out.println("Gap "+i+" "+test.pos+" "+gap+" "+bestgap);
        }
        if (toremove != null)
        {
            // Update decay for skipped over items
            // Heuristic: big gaps cost more to redo and might be more likely
            long adjust = (long)((double)bestgap * bestgap / firstgap);
            for (PosStream skipped = tail.next; skipped != toremove; skipped = skipped.next)
            {
                skipped.decay += adjust;
            }
            remove(toremove);
            streamClose(toremove);
        }
    }

    /**
     * Remove an entry in the decompression stream cache.
     * @throws IOException
     */
    void clearClosest() throws IOException
    {
        if (ts.size() == 0)
            return;
        // Find the smallest gap in positions between two streams
        long pos = -READ0COST * 0;
        PosStream best = null;
        long bestgap = Long.MAX_VALUE;
        long gaptotal = 0L;
        long gapstotal = 0L;
        int n = 0;
        for (Iterator<PosStream> ip = ts.iterator(); ip.hasNext(); )
        {
            PosStream p = ip.next();
            // Also remove PosStream which have SoftReferences which have been cleared
            if (p.isCleared())
            {
                p.prev.next = p.next;
                p.next.prev = p.prev;
                p.prev = null;
                p.next = null;
                ip.remove();
                continue;
            }
            long gap = p.position() - pos;
            if (gap < bestgap)
            {
                best = p;
                bestgap = gap;
            }
            gaptotal += gap;
            gapstotal += gap * gap;
            ++n;
            pos = p.position();
        }
        if (best != null)
        {
            //System.out.println("Removed "+best.position()+","+best.seq+","+bestgap);
            remove(best);
            streamClose(best);
        }
    }

    void dump()
    {
        if (ts.size() == 0)
            return;
        // Find the smallest gap in positions between two streams
        long pos = -READ0COST;
        PosStream best = null;
        long bestgap = Long.MAX_VALUE;
        long gaptotal = 0L;
        long gapstotal = 0L;
        int n = 0;
        for (Iterator<PosStream> ip = ts.iterator(); ip.hasNext(); )
        {
            PosStream p = ip.next();
            // Also remove PosStream which have SoftReferences which have been cleared
            if (p.isCleared())
            {
                long gap = p.position() - pos;
                System.out.println(n+","+p.position()+","+p.seq+","+gap+","+p.decay+",cleared"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                continue;
            }
            long gap = p.position() - pos;
            System.out.println(n+","+p.position()+","+p.seq+","+gap+","+p.decay); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            if (gap < bestgap)
            {
                best = p;
                bestgap = gap;
            }
            gaptotal += gap;
            gapstotal += gap * gap;
            ++n;
            pos = p.position();
        }
        if (verbose && n > 0 && bestgap < 64)
            System.out.println("n="+n+" avg="+((float)gaptotal/n)+" rms="+Math.sqrt((double)gapstotal/n)+" smallest="+bestgap+" pos="+best.pos); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    /**
     * Move to a position in the seekable stream. Locates the stream closest
     * before the seek point. Skip to the seek point. If no underlying stream is
     * before the seek point, create another and discard an existing stream if
     * required.
     *
     * @param pos
     * @throws IOException
     */
    public void seek(long pos) throws IOException
    {
        long then = System.currentTimeMillis();
        if (current != null)
        {
            // Remember the position in the underlying stream
            current.basepos = underlyingPosition();
            // Add the current stream to the list so it can be searched for
            current.setActive(false);
            if (!add(current))
            {
                // Exact match already exists
                streamClose(current);
                current = null;
            }
        }
        // Create a PosStream so we can search for an existing close one
        PosStream dummy = new PosStream(pos, nextseq);
        PosStream found;
        do
        {
            found = ts.floor(dummy);
        }
        while (found != null && remove(found) && !found.setActive(true));
        if (found != null)
        {
            PosStream copy = found.copy(nextseq);
            if (copy != null)
            {
                ++nextseq;
                // Put the copy back, and use the original
                copy.setActive(false);
                // Should always succeed
                add(copy);
                // Only do the cleanup after we have removed the
                // PosStream we want to use - don't want clean up closing it.
                if (ts.size() > cachesize)
                {
                    clearEntry();
                }
            }
            underlyingPosition(found.basepos);
        }
        else
        {
            // New entry
            if (ts.size() > cachesize)
            {
                clearEntry();
            }
            underlyingPosition(0);
            try
            {
                // Ask the caller to generate a new stream
                found = new PosStream(genstream.get(), nextseq++);
            }
            catch (UncheckedIOException e)
            {
                // Switch back to the current
                if (current != null)
                {
                    remove(current);
                    underlyingPosition(current.basepos);
                    if (!current.setActive(true))
                    {
                        current = null;
                    }
                }
                throw e.getCause();
            }
        }
        current = found;
        long toSkip = pos - found.position();
        long toSkip1 = toSkip;
        if (ts.size() >= cachesize && cachesize > 0)
        {
            // If a very large skip
            int factor = 10;
            if (lastpos > 0 && toSkip / factor > lastpos / cachesize ||
                estlen > 0 && toSkip / factor > estlen / cachesize ||
                lastpos == 0 && estlen == 0 && toSkip / factor > 500L*1024*1024*1024 / cachesize)
            {
                // make some space for an intermediate position
                clearEntry();
            }
        }
        while (toSkip > 0)
        {
            // Skip in 1GB chunks so we can save intermediate readers
            long skipNow;
            if (ts.size() < cachesize)
            {
                int space = cachesize - ts.size();
                if (lastpos > 0)
                    skipNow = Math.min(toSkip, lastpos / (cachesize + 1));
                else if (estlen > 0)
                    skipNow = Math.min(toSkip, estlen / (cachesize + 1));
                else
                    skipNow = Math.min(toSkip, 1L << 30);
                int fewleft = 2;
                if (space <= fewleft)
                {
                    skipNow = (toSkip + space) / (space + 1);
                }
            }
            else
            {
                skipNow = toSkip;
            }
            long skipped = skip(skipNow);
            if (skipped == 0)
            {
                // skip can read to the end of the stream and close current
                if (current == null)
                    throw new EOFException(Long.toString(position()));
                else
                    throw new IOException(Long.toString(position()));
            }
            // Skip should normally call read() which will update pos
            toSkip -= skipped;
            // Possibly create some readers for intermediate places
            // current is null if we have skipped beyond the end of the stream
            if (toSkip > 0 && ts.size() < cachesize && current != null)
            {
                current.basepos = underlyingPosition();
                PosStream extra = current.copy(nextseq);
                if (extra != null)
                {
                    ++nextseq;
                    extra.setActive(false);
                    // Should always succeed
                    add(extra);
                }
            }
        }
        long now = System.currentTimeMillis();
        totalseek += now - then;
        if (verbose && now - then > 1000)
        {
            dump();
            System.out.println(MessageUtil.format("Slow gzip seek to offset {0} from {1} by {2} cache size {3} took {4}ms", pos, pos - toSkip1, toSkip1, ts.size(), (now-then))); //$NON-NLS-1$
        }
    }

    public long position()
    {
        return current != null ? current.pos : lastpos;
    }

    @Override
    public int read() throws IOException
    {
        if (current == null)
            return -1;
        int r = current.read();
        if (current.position() > estlen && estlen > 0)
            estlen = current.position();
        if (r < 0)
        {
            // Indicate at end of stream
            lastpos = current.position();
            estlen = lastpos;
            // Come to the end of the stream, so remove the underlying stream
            streamClose(current);
            current = null;
        }
        return r;
    }

    //@Override
    public int read(byte buffer[], int offset, int length) throws IOException
    {
        if (current == null)
            return -1;
        int r = current.read(buffer, offset, length);
        if (current.position() > estlen && estlen > 0)
            estlen = current.position();
        if (r < 0)
        {
            // Indicate at end of stream
            lastpos = current.position();
            estlen = lastpos;
            // Come to the end of the stream, so remove the underlying stream
            streamClose(current);
            current = null;
        }
        return r;
    }

    /**
     * Closes and cleans up the {@link SeekableStream}, including
     * the non-seekable streams.
     * Does not close the underlying stream or channel, this should be
     * done explicitly.
     */
    public void close() throws IOException
    {
        for (Iterator<PosStream>it = ts.iterator(); it.hasNext(); )
        {
            PosStream ps = it.next();
            // We can close the streams now as the
            // underlying stream is about to be closed too.
            streamClose(ps);
            ps.prev.next = ps.next;
            ps.next.prev = ps.prev;
            ps.prev = null;
            ps.next = null;
            it.remove();
        }
        if (current != null)
        {
            // Indicate at end of stream
            lastpos = current.position();
            estlen = lastpos;
            streamClose(current);
        }
        current = null;
        // No need to close the underlying streams, that is the job of the caller
        if (verbose)
            System.out.println("Total seek time "+totalseek+"ms"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public String toString()
    {
        long pos = current != null ? current.position() : lastpos;
        return this.getClass().getName() + " " + pos + " " + ts; //$NON-NLS-1$ //$NON-NLS-2$
    }

}
