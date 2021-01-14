/*******************************************************************************
 * Copyright (c) 2019,2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation (Andrew Johnson) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/**
 * Creates an unzipped view of a Gzipped file.
 * Probably quite slow for random access, okay for streaming access.
 * Package class.
 * Do not call any methods other than
 * {@link #seek(long)}
 * {@link #getFilePointer()}
 * {@link #length()} - not necessarily accurate
 * {@link #read(byte[])}
 * {@link #read(byte[], int, int)}
 * {@link #close()}
 */
class CompressedRandomAccessFile extends RandomAccessFile
{
    SeekableStream ss;
    /**
     * Create an unzipped view of the gzipped file, using multiple
     * gzipped readers to obtain the uncompressed data.
     * @param file
     * @param hint for random access
     * @param length estimate
     * @throws IOException
     */
    public CompressedRandomAccessFile(File file, boolean random, long length) throws IOException
    {
        super(file, "r"); //$NON-NLS-1$
        FileChannel ch = getChannel();
        // length of file on disk - don't find length after decompression as expensive
        // and don't know it yet
        long len = ch.size();
        // Each SeekableSteam input decompressor probably uses 64kB.
        long decompSize = 65536;
        int cacheSize = (int)Math.min(Math.min(len / 100000, 1000) + len / 1000000, 1000000);
        // Also limit the cache according to memory
        // Limit to 1/4 spare memory
        long required = cacheSize * decompSize * 4;
        long maxFree = checkMemSpace(required);
        if (required > maxFree)
            cacheSize = (int)(maxFree / decompSize / 4);
        ss = new SeekableStream(new Supplier<InputStream>()
        {
            public InputStream get()
            {
                try
                {
                    /*
                     * Create a stream view of the channel.
                     * Important - changing position via channel
                     * must change position of input stream, so
                     * no buffering.
                     * Add mark support.
                     */
                    InputStream is = new FilterInputStream(Channels.newInputStream(ch)) {
                        long mark_pos;
                        @Override
                        public boolean markSupported()
                        {
                            return true;
                        }
                        public void mark(int n)
                        {
                            try
                            {
                                mark_pos = ch.position();
                            }
                            catch (IOException e)
                            {
                                mark_pos = -1;
                            }
                        }
                        public void reset() throws IOException
                        {
                            ch.position(mark_pos);
                        }
                    };
                    InputStream is2 = new SeekableStream.UnclosableInputStream(is);
                    // GZIPInputStream2 can save positions mid stream
                    // GZIPInputStream is faster for linear access
                    return random ? new GZIPInputStream2(is2) : new GZIPInputStream(is2);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
        }, ch, cacheSize, length);
    }
    @Override
    public void seek(long pos) throws IOException
    {
        ss.seek(pos);
    }
    @Override
    public long getFilePointer()
    {
        return ss.position();
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
    public int read(byte buf[]) throws IOException
    {
        return ss.read(buf);
    }
    @Override
    public int read(byte buf[], int off, int len) throws IOException
    {
        return ss.read(buf, off, len);
    }
    public void close() throws IOException
    {
        ss.close();
        super.close();
    }
    /**
     * Estimate the length of a file, including GZip without
     * decompressing the whole file.
     * @param f
     * @return
     * @throws IOException
     */
    public static long estimatedLength(File f) throws IOException
    {
        try (RandomAccessFile ra = new RandomAccessFile(f, "r")) //$NON-NLS-1$
        {
            return estimatedLength(ra);
        }
    }
    /**
     * Estimate the length of the uncompressed version of the Gzipped file.
     * Gzip only has the least significant 32-bits of the size.
     * Estimate the size as 5.0 times the compressed size, but with the
     * same least significant 32-bits.
     * @param ra The file
     * @return the estimated uncompresed size
     * @throws IOException
     */
    static long estimatedLength(RandomAccessFile ra) throws IOException
    {
        long filel = ra.length();
        long pos = ra.getFilePointer();
        try
        {
            boolean gzip = isGZIP(ra);
            if (gzip)
            {
                ra.seek(filel - 4);
                int r1 = ra.read();
                int r2 = ra.read();
                int r3 = ra.read();
                int r4 = ra.read();
                // Least significant 32 bits of original length
                long len32 = ((long) (r4 & 0xff) << 24) + ((r3 & 0xff) << 16) + ((r2 & 0xff) << 8) + (r1 & 0xff);
                // Estimated decompression factor
                long estimate = (long) (filel * 5.0);
                // Now insert least significant 32 bits
                long e1 = (estimate & ~0xffffffffL) + len32;
                // and choose the closest value with those bits
                long e2 = e1 + 0x100000000L;
                long e3 = e1 - 0x100000000L;
                long best = e1;
                if (Math.abs(e2 - estimate) < Math.abs(best - estimate))
                    best = e2;
                if (e3 >= 0 && Math.abs(e3 - estimate) < Math.abs(best - estimate))
                    best = e3;
                return best;
            }
            else
            {
                return filel;
            }
        }
        finally
        {
            ra.seek(pos);
        }
    }
    static boolean isGZIP(RandomAccessFile ra) throws IOException
    {
        long pos = ra.getFilePointer();
        try
        {
            if (pos != 0)
                ra.seek(0);
            int b1 = ra.read();
            int b2 = ra.read();
            int b3 = ra.read();
            boolean gzip = b1 == 0x1f && b2 == 0x8b && b3 == 0x08;
            return gzip;
        }
        finally
        {
            ra.seek(pos);
        }
    }

    /**
     * Check whether there is at least the requested amount of
     * memory available.
     * @param requested
     * @return memory available 
     */
    static long checkMemSpace(long requested)
    {
        Runtime runtime = Runtime.getRuntime();
        long maxFree = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        if (maxFree < requested)
        {
            runtime.gc();
            maxFree = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
            return maxFree;
        }
        return maxFree;
    }
}
