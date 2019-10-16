/*******************************************************************************
 * Copyright (c) 2019 Netflix and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Netflix (Jason Koch) - refactors for increased concurrency and performance
 *    IBM Corporation (Andrew Johnson) - tidy exception cases
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;

public class ByteArrayPositionInputStream implements IPositionInputStream, Closeable, AutoCloseable
{
    /*
     * Can throw EOFExceptions.
     * If you use it wrong, it will throw ArrayIndexOutOfBoundsException.
     */
    private final byte[] bytes;
    private int position = 0;
    private final int idSize;

    public ByteArrayPositionInputStream(final byte[] bytes, final int idSize)
    {
        this.bytes = bytes;
        this.idSize = idSize;
    }

    public void close() throws IOException
    {
        position = bytes.length;
    }

    public int read()
    {
        if (position >= bytes.length)
            return -1;
        return 0xFF & ((int) bytes[position++]);
    }

    public int read(byte[] b, int off, int len)
    {
        if (len < 0)
            throw new IllegalArgumentException();
        if (len == 0)
            return 0;
        if (position >= bytes.length)
            return -1;
        if (position > bytes.length - len)
            len = bytes.length - position;
        System.arraycopy(bytes, position, b, off, len);
        position += len;
        return len;
    }

    public long skip(long n)
    {
        if (n <= 0)
            return 0;
        if (position >= bytes.length)
            return 0;
        if (position > bytes.length - n)
            n = bytes.length - position;
        position += n;
        return n;
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

    public int skipBytes(long n)
    {
        return (int) skip(n);
    }

    public void readFully(byte b[]) throws EOFException
    {
        int r = read(b, 0, b.length);
        if (r < b.length)
            throw new EOFException();
    }

    public void readFully(byte b[], int off, int len) throws EOFException
    {
        int r = read(b, off, len);
        if (r < len)
            throw new EOFException();
    }

    public long position()
    {
        return position;
    }

    // //////////////////////////////////////////////////////////////
    // DataInput implementations
    // //////////////////////////////////////////////////////////////

    public int readUnsignedByte() throws EOFException
    {
        int ch = read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    public int readInt() throws EOFException
    {
        if (position > bytes.length - 4)
            throw new EOFException();
        int ch1 = bytes[position] & 0xff;
        int ch2 = bytes[position + 1] & 0xff;
        int ch3 = bytes[position + 2] & 0xff;
        int ch4 = bytes[position + 3] & 0xff;

        int result = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        position += 4;
        return result;
    }

    public long readLong() throws EOFException
    {
        if (position > bytes.length - 4)
            throw new EOFException();
        long result = (((long) bytes[position] << 56)
                        + ((long) (bytes[position + 1] & 0xff) << 48)
                        + ((long) (bytes[position + 2] & 0xff) << 40)
                        + ((long) (bytes[position + 3] & 0xff) << 32)
                        + ((long) (bytes[position + 4] & 0xff) << 24)
                        + ((bytes[position + 5] & 0xff) << 16)
                        + ((bytes[position + 6] & 0xff) << 8)
                        + ((bytes[position + 7] & 0xff) << 0));
        position += 8;
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

    protected int readIntArray(int[] a) throws EOFException
    {
        for(int i = 0; i < a.length; i++) {
            a[i] = readInt();
        }
        return a.length;
    }

    protected int readLongArray(long[] a) throws EOFException
    {
        for(int i = 0; i < a.length; i++) {
            a[i] = readLong();
        }
        return a.length;
    }

    public long readUnsignedInt() throws EOFException
    {
        return (0x0FFFFFFFFL & readInt());
    }

    public int skipBytes(int n)
    {
        if (n <= 0)
            return 0;
        if (position >= bytes.length)
            return 0;
        if (position > bytes.length - n)
            n = bytes.length - position;
        position += n;
        return n;
    }

    public void seek(long pos) throws IOException
    {
        if (pos < 0 || pos > Integer.MAX_VALUE)
            throw new IOException();
        position = (int) pos;
    }

    public String readUTF()
    {
        throw new UnsupportedOperationException();
    }

    public long readID(int idSize) throws IOException
    {
        return idSize == 4 ? (0x0FFFFFFFFL & readInt()) : readLong();
    }

}
