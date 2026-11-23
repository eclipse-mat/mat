/*******************************************************************************
 * Copyright (c) 2008, 2026 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Yi Yang (Alibaba) - batched bit reads for performance
 *******************************************************************************/
package org.eclipse.mat.parser.io;

import java.io.Closeable;
import java.io.EOFException;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;

public class BitInputStream implements Flushable, Closeable
{
    public final static int DEFAULT_BUFFER_SIZE = 16 * 1024;

    private InputStream is;
    private long current;
    private byte[] buffer;
    private int fill;
    private int pos;
    private int avail;

    public BitInputStream(final InputStream is)
    {
        this.is = is;
        this.buffer = new byte[DEFAULT_BUFFER_SIZE];
    }

    public void flush()
    {
        avail = 0;
        pos = 0;
        fill = 0;
        current = 0;
    }

    public void close() throws IOException
    {
        is.close();
        is = null;
        buffer = null;
    }

    private int read() throws IOException
    {
        if (avail == 0)
        {
            avail = is.read(buffer);
            if (avail == -1)
            {
                avail = 0;
                throw new EOFException();
            }
            else
            {
                pos = 0;
            }
        }

        avail--;
        return buffer[pos++] & 0xFF;
    }

    public int readBit() throws IOException
    {
        return readInt(1);
    }

    public int readInt(int len) throws IOException
    {
        // Up to 31 leftover bits plus a 32-bit prefetch still fit in a long,
        // so most reads avoid the per-byte path.
        while (fill < len)
        {
            if (avail >= 4)
            {
                current = (current << 32) | ((buffer[pos] & 0xffL) << 24)
                                | ((buffer[pos + 1] & 0xffL) << 16)
                                | ((buffer[pos + 2] & 0xffL) << 8) | (buffer[pos + 3] & 0xffL);
                pos += 4;
                avail -= 4;
                fill += 32;
            }
            else
            {
                current = (current << 8) | read();
                fill += 8;
            }
        }
        fill -= len;
        return (int) ((current >>> fill) & ((1L << len) - 1));
    }

}
