/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.io;

import java.io.EOFException;
import java.io.IOException;

import org.eclipse.mat.parser.internal.Messages;

public class ByteArrayPositionInputStream implements PositionInputStream
{
    /*
     * note this does not throw any IOExceptions
     * but if you use it wrong, it will throw ArrayIndexOutOfBoundsException
     */
    private final byte[] bytes;
    private int position = 0;
    private final int idSize;

    public ByteArrayPositionInputStream(final byte[] bytes, final int idSize) {
        this.bytes = bytes;
        this.idSize = idSize;
    }

    public void close() throws IOException
    {
        position = bytes.length;
    }
    
    public int read()
    {
        return 0xFF & ((int) bytes[position++]);
    }

    public int read(byte[] b, int off, int len)
    {
        System.arraycopy(bytes, position, b, off, len);
        position += len;
        return len;
    }

    public long skip(long n)
    {
        position += n;
        return n;
    }

    public boolean markSupported()
    {
        return false;
    }

    public void mark(int readLimit)
    {
        throw new UnsupportedOperationException(Messages.PositionInputStream_mark);
    }

    public void reset()
    {
        throw new UnsupportedOperationException(Messages.PositionInputStream_reset);
    }

    public int skipBytes(long n)
    {
        return (int) skip(n);
    }

    public void readFully(byte b[])
    {
        read(b, 0, b.length);
    }

    public void readFully(byte b[], int off, int len)
    {
        read(b, off, len);
    }

    public long position()
    {
        return position;
    }

    public void seek(int pos)
    {
        position = pos;
    }

    // //////////////////////////////////////////////////////////////
    // DataInput implementations
    // //////////////////////////////////////////////////////////////

    public int readUnsignedByte()
    {
        int ch = read();
        return ch;
    }

    public int readInt()
    {
        int ch1 = bytes[position] & 0xff;
        int ch2 = bytes[position + 1] & 0xff;
        int ch3 = bytes[position + 2] & 0xff;
        int ch4 = bytes[position + 3] & 0xff;
        
        int result = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0)); 
        
        position += 4;
        
        return result;
    }

    public long readLong()
    {
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

    public int readUnsignedShort()
    {
        int ch1 = read();
        int ch2 = read();
        return (ch1 << 8) + (ch2 << 0);
    }

    // //////////////////////////////////////////////////////////////
    // additions
    // //////////////////////////////////////////////////////////////

    protected int readIntArray(int[] a)
    {
        for(int i = 0; i < a.length; i++) {
            a[i] = readInt();
        }
        return a.length;
    }

    protected int readLongArray(long[] a)
    {
        for(int i = 0; i < a.length; i++) {
            a[i] = readLong();
        }
        return a.length;
    }

    public long readUnsignedInt()
    {
        return (0x0FFFFFFFFL & readInt());
    }

    public int skipBytes(int n)
    {
        position += n;
        return n;
    }

    public void seek(long pos)
    {
        position = (int) pos;
    }

    public String readUTF()
    {
        throw new UnsupportedOperationException();
    }

    public long readID(int idSize)
    {
        return idSize == 4 ? (0x0FFFFFFFFL & readInt()) : readLong();
    }

}