/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
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

public class IOUtils
{
    public static void writeLong(long v, byte[] arr, int offset)
    {
        arr[offset] = (byte) ((int) (v >>> 56) & 0xFF);
        arr[offset + 1] = (byte) ((int) (v >>> 48) & 0xFF);
        arr[offset + 2] = (byte) ((int) (v >>> 40) & 0xFF);
        arr[offset + 3] = (byte) ((int) (v >>> 32) & 0xFF);
        arr[offset + 4] = (byte) ((int) (v >>> 24) & 0xFF);
        arr[offset + 5] = (byte) ((int) (v >>> 16) & 0xFF);
        arr[offset + 6] = (byte) ((int) (v >>> 8) & 0xFF);
        arr[offset + 7] = (byte) ((int) (v >>> 0) & 0xFF);
    }

    public static void writeInt(int v, byte[] arr, int offset)
    {
        arr[offset] = (byte) ((v >>> 24) & 0xFF);
        arr[offset + 1] = (byte) ((v >>> 16) & 0xFF);
        arr[offset + 2] = (byte) ((v >>> 8) & 0xFF);
        arr[offset + 3] = (byte) ((v >>> 0) & 0xFF);
    }

    public static char readChar(byte[] buf, int offset) throws IOException
    {
        int ch1 = (int) buf[offset];
        int ch2 = (int) buf[offset + 1];
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    public static long readLong(byte[] arr, int offset)
    {
        long val = 0;
        int bits = 64;
        long tval;

        for (int i = 0; i < 8; i++)
        {
            bits -= 8;
            tval = arr[offset + i];
            tval = tval < 0 ? 256 + tval : tval;
            val |= tval << bits;
        }

        return val;
    }

    public static int readInt(byte[] arr, int offset)
    {
        int val = 0;
        int bits = 32;
        long tval;

        for (int i = 0; i < 4; i++)
        {
            bits -= 8;
            tval = arr[offset + i];
            tval = tval < 0 ? 128 + tval : tval;
            val |= tval << bits;
        }

        return val;
    }
}
