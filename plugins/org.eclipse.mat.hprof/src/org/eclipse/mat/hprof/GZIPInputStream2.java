/*******************************************************************************
 * Copyright (c) 2020,2021 IBM Corporation and others.
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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipException;

import io.nayuki.deflate.InflaterInputStream;

/**
 * A stream for reading Gzip compressed files.
 * Uses a modified {@link io.nayuki.deflate.InflaterInputStream} to allow the
 * state of the stream to be duplicated, aiding code for random access to Gzip files.
 */
public class GZIPInputStream2 extends FilterInputStream
{
    private static final int FLG_FHCRC = 1 << 1;
    private static final int FLG_FCOMMENT = 1 << 4;
    private static final int FLG_FNAME = 1 << 3;
    private static final int FLG_FEXTRA = 1 << 2;
    InputStream is;
    long uncompressedLen;
    long uncompressedLocationAtHeader;
    CRC32 crc;
    boolean checkcrc;
    String comment;
    String filename;
    public GZIPInputStream2(GZIPInputStream2 gs) throws IOException
    {
        super(new InflaterInputStream((InflaterInputStream)gs.in));
        is = gs.is;
        crc = gs.crc.clone();
        uncompressedLen = gs.uncompressedLen;
        uncompressedLocationAtHeader = gs.uncompressedLocationAtHeader;
    }

    public GZIPInputStream2(InputStream is) throws IOException
    {
        super(new InflaterInputStream(is, false));
        this.is = is;
        crc = new CRC32();
        readHeader(is);
    }

    private InputStream readHeader(InputStream is) throws IOException
    {
        int b0 = is.read();
        return readHeader(is, b0);
    }
    private InputStream readHeader(InputStream is, int b0) throws IOException
    {;
        uncompressedLocationAtHeader += uncompressedLen;
        uncompressedLen = 0;
        crc.reset();
        crc.update(b0);
        if (b0 != 0x1f)
            throw new ZipException(Messages.GZIPInputStream2_NotAGzip);
        int b1 = is.read();
        crc.update(b1);
        if (b1 != 0x8b)
            throw new ZipException(Messages.GZIPInputStream2_NotAGzip);
        int b2 = is.read();
        crc.update(b2);
        if (b2 != 0x8)
            throw new ZipException(Messages.GZIPInputStream2_NotDeflate);
        // Flags
        int b3 = is.read();
        if (b3 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b3);
        if ((b3 & 0xe0) != 0x0)
            throw new ZipException(Messages.GZIPInputStream2_BadHeaderFlag);
        int b4 = is.read();
        if (b4 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b4);
        int b5 = is.read();
        if (b5 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b5);
        int b6 = is.read();
        if (b6 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b6);
        int b7 = is.read();
        if (b7 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b7);
        int mtime = (b4 & 0xff) | (b5 & 0xff) << 8 | (b6 & 0xff) << 16 | (b7 & 0xff) << 24;
        long mjtime = mtime * 1000L;
        // Extra flags
        int b8 = is.read();
        if (b8 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b8);
        // OS
        int b9 = is.read();
        if (b9 < 0)
            throw new ZipException(Messages.GZIPInputStream2_TruncatedHeader);
        crc.update(b9);
        // Extra
        if ((b3 & FLG_FEXTRA) != 0)
        {
            int l1 = is.read();
            if (l1 < 0)
                throw new ZipException(Messages.GZIPInputStream2_TruncatedExtra);
            crc.update(l1);
            int l2 = is.read();
            if (l2 < 0)
                throw new ZipException(Messages.GZIPInputStream2_TruncatedExtra);
            crc.update(l2);
            int l = (l2 & 0xff) << 8 | (l1 & 0xff);
            byte[] b = new byte[l];
            int r = is.read(b);
            if (r < l)
                throw new ZipException(Messages.GZIPInputStream2_TruncatedExtra);
            crc.update(b);
        }
        // Name
        if ((b3 & FLG_FNAME) != 0)
        {
            int b;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while ((b = is.read()) != 0) {
                if (b == -1)
                    throw new ZipException(Messages.GZIPInputStream2_TruncatedName);
                crc.update(b);
                bos.write(b);
            }
            crc.update(0);
            filename = new String(bos.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        // Comment
        if ((b3 & FLG_FCOMMENT) != 0)
        {
            int b;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while ((b = is.read()) != 0) {
                if (b == -1)
                    throw new ZipException(Messages.GZIPInputStream2_TruncatedComment);
                crc.update(b);
                bos.write(b);
            }
            crc.update(0);
            comment = new String(bos.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        // CRC16
        if ((b3 & FLG_FHCRC) != 0)
        {
            int c0 = is.read();
            if (c0 == -1)
                throw new ZipException(Messages.GZIPInputStream2_TruncatedHeaderCRC);
            int c1 = is.read();
            if (c1 == -1)
                throw new ZipException(Messages.GZIPInputStream2_TruncatedHeaderCRC);
            int crcv = c0 & 0xff | (c1 & 0xff) << 8;
            if ((crc.getValue() & 0xffff) != crcv)
                throw new ZipException(Messages.GZIPInputStream2_BadHeaderCRC);
        }
        crc.reset();
        return is;
    }

    @Override
    public int read() throws IOException
    {
        int r;
        do {
            r = super.read();
            if (r == -1)
            {
                // handle chunked zip
                // Ensure we can read the data after the zipped part
                ((InflaterInputStream)in).detach();
                checkTrailer(in);
                int b0 = in.read();
                // Real EOF
                if (b0 <= 0)
                    break;
                // New chunk
                readHeader(in, b0);
                ((InflaterInputStream)in).attach();
            }
        } while (r < 0);
        if (r >= 0)
        {
            crc.update(r);
            ++uncompressedLen;
        }
        return r;
    }

    @Override
    public int read(byte buf[], int off, int len) throws IOException
    {
        int r;
        do
        {
            r = super.read(buf, off, len);
            // Possible bug in InflaterInputStream - might return 0
            if (r == -1)
            {
                // Handle chunked zip
                // Ensure we can read the data after the zipped part
                ((InflaterInputStream)in).detach();
                checkTrailer(in);
                int b0 = in.read();
                // Real EOF
                if (b0 <= 0)
                    break;
                // New chunk
                readHeader(in, b0);
                ((InflaterInputStream)in).attach();
            }
        }
        while (r <= 0);

        if (r != -1)
        {
            crc.update(buf, off, r);
            uncompressedLen += r;
        }
        return r;
    }

    public long skip2(long n) throws IOException
    {
        long skipped = 0;
        byte buf[] = null;
        while (n > 0)
        {
            int toskip = (int)Math.min(n, 16384);
            if (buf == null)
                buf = new byte[toskip];
            long r = read(buf, 0, toskip);
            if (r == -1)
                break;
            skipped += r;
            n -= r;
        }
        return skipped;
    }

    void checkTrailer(InputStream is) throws IOException
    {
        // CRC32
        int b0 = is.read();
        if (b0 < 0)
            throw new EOFException();
        int b1 = is.read();
        if (b1 < 0)
            throw new EOFException();
        int b2 = is.read();
        if (b2 < 0)
            throw new EOFException();
        int b3 = is.read();
        if (b3 < 0)
            throw new EOFException();
        // Unsigned
        long crc32 = b0 & 0xff | (b1 & 0xff) << 8 | (b2 & 0xff) << 16 | (b3 & 0xffL) << 24;
        long crc32v = crc.getValue();
        if (crc32v != crc32)
            throw new ZipException(Messages.GZIPInputStream2_BadTrailerCRC);
        // uncompressed length
        int b4 = is.read();
        if (b4 < 0)
            throw new EOFException();
        int b5 = is.read();
        if (b5 < 0)
            throw new EOFException();
        int b6 = is.read();
        if (b6 < 0)
            throw new EOFException();
        int b7 = is.read();
        if (b7 < 0)
            throw new EOFException();
        // Unsigned
        long len32 = b4 & 0xff | (b5 & 0xff) << 8 | (b6 & 0xff) << 16 | (b7 & 0xffL) << 24;
        if (len32 != (uncompressedLen & 0xffffffffL))
            throw new ZipException(Messages.GZIPInputStream2_BadTrailerLength);
    }

    public String comment()
    {
        return comment;
    }

    @Override
    public void close() throws IOException
    {
        super.close();
    }

    /**
     * A CRC32 implementation - which
     * allows the state to be cloned.
     */
    private static class CRC32 implements Cloneable
    {
        int value;
        private static final int table[] = new int[256];
        static
        {
            for (int i = 0; i < 256; ++i)
            {
                int v = i;
                for (int j = 0; j < 8; ++j)
                {
                    if ((v & 1) != 0)
                        v = 0xedb88320 ^ (v >>> 1);
                    else
                        v >>>= 1;
                }
                table[i] = v;
            }
        }
        public CRC32()
        {
            reset();
        }
        @Override
        public CRC32 clone()
        {
            CRC32 c = new CRC32();
            c.value = value;
            return c;
        }
        public void reset()
        {
            value = 0xffffffff;
        }
        public void update(int b)
        {
            value = table[(value ^ (b & 0xff)) & 0xff] ^ (value >>> 8);
        }
        public void update(byte b[], int offset, int len)
        {
            for (int i = 0; i < len; ++i)
            {
                update(b[offset + i]);
            }
        }
        public void update(byte b[])
        {
            update(b, 0, b.length);
        }
        public long getValue()
        {
            return (value ^ 0xffffffff) & 0xffffffffL;
        }
    }
}
