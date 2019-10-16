/*******************************************************************************
 * Copyright (c) 29 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation (Andrew Johnson) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/**
 * Package class.
 * Do not call any methods other than
 * length() - not necessarily accurate
 * read(byte[])
 * read(byte[], int, int)
 * close()
 */
class CompressedRandomAccessFile extends RandomAccessFile
{
    SeekableStream ss;
    public CompressedRandomAccessFile(File file) throws IOException
    {
        super(file, "r"); //$NON-NLS-1$
        FileChannel ch = getChannel();
        ss = new SeekableStream(new Supplier<InputStream>()
        {
            public InputStream get()
            {
                try
                {
                    InputStream is = Channels.newInputStream(ch);
                    return new GZIPInputStream(new SeekableStream.UnclosableInputStream(is));
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
        }, ch, (int)Math.min(length() / 1000000, 1000000));
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
                long estimate = (long) (filel * 4.0);
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
}
