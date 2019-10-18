/*******************************************************************************
 * Copyright (c) 2019 Netflix and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Netflix (Jason Koch) - refactors for increased concurrency and performance
 *    IBM Corporation (Andrew Johnson) - tidy EOF processing and compressed dumps
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class BufferingRafPositionInputStream implements IPositionInputStream, Closeable, AutoCloseable
{
    private final RandomAccessFile raf;
    private long channelPosition = 0;
    private byte[] buffer, throwaway;
    private final int readLength;
    private int bufferPosition = 0;
    private int bufferLength = 0;

    public BufferingRafPositionInputStream(final File file, final long offset, final int readLength) throws IOException
    {
        RandomAccessFile raf1 = new RandomAccessFile(file, "r"); //$NON-NLS-1$
        boolean gzip = CompressedRandomAccessFile.isGZIP(raf1);
        if (gzip)
        {
            raf1.close();
            raf = new CompressedRandomAccessFile(file);
        }
        else
        {
            raf = raf1;
        }
        this.readLength = readLength;
        this.buffer = new byte[readLength * 2];
        this.throwaway = new byte[readLength * 2];
        seek(offset);
    }

    public void close() throws IOException
    {
        raf.close();
    }

    public int read() throws IOException
    {
        if (!ensureAvailable(1))
            return -1;
        return 0xFF & ((int)buffer[bufferPosition++]);
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
        int copied = 0;
        while (copied < len)
        {
            while (bufferPosition == bufferLength)
            {
                bufferPosition = 0;
                bufferLength = 0;
                int read = raf.read(buffer, 0, readLength);
                // Check for short return
                if (read <= 0)
                {
                    return copied > 0 ? copied : -1;
                }
                bufferLength = read;
                channelPosition += bufferLength;
            }
            // copy the least of: what is remaining to be copied or the amount of room left in the buffer
            int toCopy = Math.min((len - copied), bufferLength - bufferPosition);
            System.arraycopy(buffer, bufferPosition, b, off, toCopy);
            bufferPosition += toCopy;
            off += toCopy;
            copied += toCopy;
        }
        return copied;
    }

    public long skip(long n) throws IOException
    {
        if (n <= 0)
            return 0;
        // assume the majority of skips are pretty close

        // if inside current buffer, just advance it
        if ((bufferLength - bufferPosition) >= n)
        {
            bufferPosition += n;
            return n;
        }

        // if it is a long way, just seek
        if (n > readLength)
        {
            /* 
             * If n is too big then this would allow a seek
             * beyond the end of the file.
             */ 
            seek(position() + n);
            return n;
        }

        // if it is only a buffer or so away, read through
        long sk = 0;
        while (n > sk)
        {
            int rd = read(throwaway, 0, (int)Math.min(throwaway.length, n - sk));
            if (rd <= 0)
                break;
            sk += rd;
        }
        return sk;
    }

    public boolean markSupported()
    {
        return false;
    }

    public void mark(int readLimit)
    {
        throw new UnsupportedOperationException(Messages.IPositionInputStream_mark);
    }

    public void reset()
    {
        throw new UnsupportedOperationException(Messages.IPositionInputStream_reset);
    }

    public int skipBytes(long n) throws IOException
    {
        return (int) skip(n);
    }

    public void readFully(byte b[]) throws IOException
    {
        int r = read(b, 0, b.length);
        if (r < b.length)
            throw new EOFException();
    }

    public void readFully(byte b[], int off, int len) throws IOException
    {
        int r = read(b, off, len);
        if (r < len)
            throw new EOFException();
    }

    public long position()
    {
        // since we are working on bytes that have already been read from the channel,
        // the channel will be further ahead than the reader
        return channelPosition - (bufferLength - bufferPosition);
    }

    // //////////////////////////////////////////////////////////////
    // DataInput implementations
    // //////////////////////////////////////////////////////////////

    public int readUnsignedByte() throws IOException
    {
        int ch = read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    private boolean ensureAvailable(int required) throws IOException
    {
        // required must not be greater than half the buffer size
        // this is on the caller to be sure of - private method
        // so all callers are internal

        // if we have a read that straddles boundaries,
        // copy the bytes into the start of the current buffer,
        // copy the next read into this buffer as well
        // then do a read, without swapping buffers

        while ((bufferLength - bufferPosition) < required)
        {
            int toKeep = bufferLength - bufferPosition;

            System.arraycopy(buffer, bufferPosition, buffer, 0, toKeep);
            bufferPosition = 0;
            bufferLength = toKeep;

            int amountRead = raf.read(buffer, bufferLength, readLength - bufferLength);
            if (amountRead <= 0)
                break;
            bufferLength += amountRead;
            channelPosition += amountRead;
        }
        return bufferLength - bufferPosition >= required;
    }

    public int readInt() throws IOException
    {
        if (!ensureAvailable(4))
            throw new EOFException();
        int ch1 = buffer[bufferPosition] & 0xff;
        int ch2 = buffer[bufferPosition + 1] & 0xff;
        int ch3 = buffer[bufferPosition + 2] & 0xff;
        int ch4 = buffer[bufferPosition + 3] & 0xff;

        int result = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0)); 

        bufferPosition += 4;

        return result;
    }

    public long readLong() throws IOException
    {
        if (!ensureAvailable(8))
            throw new EOFException();
        long result = (((long) buffer[bufferPosition] << 56)
                        + ((long) (buffer[bufferPosition + 1] & 0xff) << 48)
                        + ((long) (buffer[bufferPosition + 2] & 0xff) << 40)
                        + ((long) (buffer[bufferPosition + 3] & 0xff) << 32)
                        + ((long) (buffer[bufferPosition + 4] & 0xff) << 24)
                        + ((buffer[bufferPosition + 5] & 0xff) << 16)
                        + ((buffer[bufferPosition + 6] & 0xff) << 8)
                        + ((buffer[bufferPosition + 7] & 0xff) << 0));
        bufferPosition += 8;
        return result;
    }

    public boolean readBoolean() throws IOException
    {
        int ch = read();
        if (ch < 0)
            throw new EOFException();
        return (ch != 0);
    }

    public byte readByte() throws IOException
    {
        int ch = read();
        if (ch < 0)
            throw new EOFException();
        return (byte) (ch);
    }

    public char readChar() throws IOException
    {
        int ch1 = read();
        int ch2 = read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    public double readDouble() throws IOException
    {
        return Double.longBitsToDouble(readLong());
    }

    public float readFloat() throws IOException
    {
        return Float.intBitsToFloat(readInt());
    }

    public String readLine() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public short readShort() throws IOException
    {
        int ch1 = read();
        int ch2 = read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (short) ((ch1 << 8) + (ch2 << 0));
    }

    public int readUnsignedShort() throws IOException
    {
        int ch1 = read();
        int ch2 = read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 8) + (ch2 << 0);
    }

    // //////////////////////////////////////////////////////////////
    // additions
    // //////////////////////////////////////////////////////////////

    protected int readIntArray(int[] a) throws IOException
    {
        for(int i = 0; i < a.length; i++)
        {
            a[i] = readInt();
        }
        return a.length;
    }

    protected int readLongArray(long[] a) throws IOException
    {
        for(int i = 0; i < a.length; i++)
        {
            a[i] = readLong();
        }
        return a.length;
    }

    public long readUnsignedInt() throws IOException
    {
        return (0x0FFFFFFFFL & readInt());
    }

    public int skipBytes(int n) throws IOException
    {
        return (int) skip(n);
    }

    public void seek(long pos) throws IOException
    {
        raf.seek(pos);
        channelPosition = pos;
        bufferLength = 0;
        bufferPosition = 0;
    }

    public String readUTF() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public long readID(int idSize) throws IOException
    {
        return idSize == 4 ? (0x0FFFFFFFFL & readInt()) : readLong();
    }

}
