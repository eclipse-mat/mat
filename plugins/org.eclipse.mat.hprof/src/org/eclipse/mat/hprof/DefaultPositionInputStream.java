/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.mat.parser.io.BufferedRandomAccessInputStream;
import org.eclipse.mat.parser.io.SimpleBufferedRandomAccessInputStream;

public class DefaultPositionInputStream extends FilterInputStream implements DataInput, IPositionInputStream
{
    private final byte[] readBuffer = new byte[32];
    private long position = 0L;

    public DefaultPositionInputStream(InputStream in)
    {
        super(in);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#read()
     */
    public int read() throws IOException
    {
        int res = super.read();
        if (res != -1)
            position++;
        return res;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException
    {
        int res = super.read(b, off, len);
        if (res != -1)
            position += res;
        return res;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#skip(long)
     */
    public long skip(long n) throws IOException
    {
        long res = super.skip(n);
        position += res;
        return res;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#markSupported()
     */
    public boolean markSupported()
    {
        return false;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#mark(int)
     */
    public void mark(int readLimit)
    {
        throw new UnsupportedOperationException(Messages.IPositionInputStream_mark);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#reset()
     */
    public void reset()
    {
        throw new UnsupportedOperationException(Messages.IPositionInputStream_reset);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#skipBytes(int)
     */
    public int skipBytes(int n) throws IOException
    {
        int total = 0;
        int cur = 0;

        while ((total < n) && ((cur = (int) skip(n - total)) > 0))
        {
            total += cur;
        }

        return total;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#skipBytes(long)
     */
    public int skipBytes(long n) throws IOException
    {
        long total = 0;
        long cur = 0;

        while ((total < n) && ((cur = skip(n - total)) > 0))
        {
            total += cur;
        }

        return (int)total;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readFully(byte[])
     */
    public void readFully(byte b[]) throws IOException
    {
        readFully(b, 0, b.length);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readFully(byte[], int, int)
     */
    public void readFully(byte b[], int off, int len) throws IOException
    {
        int n = 0;
        while (n < len)
        {
            int count = read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#position()
     */
    public long position()
    {
        return position;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#seek(long)
     */
    public void seek(long pos) throws IOException
    {
        if (in instanceof BufferedRandomAccessInputStream)
        {
            position = pos;
            ((BufferedRandomAccessInputStream) in).seek(pos);
        }
        else if (in instanceof SimpleBufferedRandomAccessInputStream)
        {
            position = pos;
            ((SimpleBufferedRandomAccessInputStream) in).seek(pos);
        }
        else
        {
            throw new UnsupportedOperationException(Messages.IPositionInputStream_seek);
        }
    }

    public void seek(int pos) throws IOException
    {
        if (in instanceof BufferedRandomAccessInputStream)
        {
            position = pos;
            ((BufferedRandomAccessInputStream) in).seek(pos);
        }
        else if (in instanceof SimpleBufferedRandomAccessInputStream)
        {
            position = pos;
            ((SimpleBufferedRandomAccessInputStream) in).seek(pos);
        }
        else
        {
            throw new UnsupportedOperationException(Messages.IPositionInputStream_seek);
        }
    }

    // //////////////////////////////////////////////////////////////
    // DataInput implementations
    // //////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readUnsignedByte()
     */
    public int readUnsignedByte() throws IOException
    {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        position++;
        return ch;
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readInt()
     */
    public int readInt() throws IOException
    {
        readFully(readBuffer, 0, 4);
        return readInt(readBuffer, 0);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readLong()
     */
    public long readLong() throws IOException
    {
        readFully(readBuffer, 0, 8);
        return readLong(readBuffer, 0);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readBoolean()
     */
    public boolean readBoolean() throws IOException
    {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        position++;
        return (ch != 0);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readByte()
     */
    public byte readByte() throws IOException
    {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        position++;
        return (byte) (ch);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readChar()
     */
    public char readChar() throws IOException
    {
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        position += 2;
        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readDouble()
     */
    public double readDouble() throws IOException
    {
        return Double.longBitsToDouble(readLong());
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readFloat()
     */
    public float readFloat() throws IOException
    {
        return Float.intBitsToFloat(readInt());
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readLine()
     */
    public String readLine() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readShort()
     */
    public short readShort() throws IOException
    {
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        position += 2;
        return (short) ((ch1 << 8) + (ch2 << 0));
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readUTF()
     */
    public String readUTF() throws IOException
    {
        return DataInputStream.readUTF(this);
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readUnsignedShort()
     */
    public int readUnsignedShort() throws IOException
    {
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        position += 2;
        return (ch1 << 8) + (ch2 << 0);
    }

    // //////////////////////////////////////////////////////////////
    // additions
    // //////////////////////////////////////////////////////////////

    protected int readIntArray(int[] a) throws IOException
    {
        int len = a.length * 4;
        byte[] b = len > readBuffer.length ? new byte[len] : readBuffer;

        if (read(b, 0, len) != len)
            throw new IOException();

        for (int ii = 0; ii < a.length; ii++)
            a[ii] = readInt(b, ii * 4);

        return a.length;
    }

    public static int readInt(byte[] b, int offset) throws IOException
    {
        int ch1 = b[offset] & 0xff;
        int ch2 = b[offset + 1] & 0xff;
        int ch3 = b[offset + 2] & 0xff;
        int ch4 = b[offset + 3] & 0xff;
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    protected int readLongArray(long[] a) throws IOException
    {
        int len = a.length * 8;
        byte[] b = len > readBuffer.length ? new byte[len] : readBuffer;

        if (read(b, 0, len) != len)
            throw new IOException();

        for (int ii = 0; ii < a.length; ii++)
            a[ii] = readLong(b, ii * 8);

        return a.length;
    }

    public static long readLong(byte[] b, int offset)
    {
        return (((long) b[offset] << 56) //
                        + ((long) (b[offset + 1] & 255) << 48) //
                        + ((long) (b[offset + 2] & 255) << 40) //
                        + ((long) (b[offset + 3] & 255) << 32) //
                        + ((long) (b[offset + 4] & 255) << 24) //
                        + ((b[offset + 5] & 255) << 16) //
                        + ((b[offset + 6] & 255) << 8) //
        + ((b[offset + 7] & 255) << 0));
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readUnsignedInt()
     */
    public long readUnsignedInt() throws IOException
    {
        return (0x0FFFFFFFFL & readInt());
    }

    /* (non-Javadoc)
     * @see org.eclipse.mat.parser.io.PositionInputStream#readID(int)
     */
    public long readID(int idSize) throws IOException
    {
        return idSize == 4 ? (0x0FFFFFFFFL & readInt()) : readLong();
    }

}
