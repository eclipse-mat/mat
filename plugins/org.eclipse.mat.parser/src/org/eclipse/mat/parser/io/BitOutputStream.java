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
 *    Yi Yang (Alibaba) - batched bit writes for performance
 *******************************************************************************/
package org.eclipse.mat.parser.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

public class BitOutputStream implements Flushable, Closeable
{

    public final static int DEFAULT_BUFFER_SIZE = 16 * 1024;

    private OutputStream os;
    private long current;
    private byte[] buffer;
    private int fill;
    private int pos;

    public BitOutputStream(final OutputStream os)
    {
        this.os = os;
        this.buffer = new byte[DEFAULT_BUFFER_SIZE];
    }

    public void flush() throws IOException
    {
        if (fill != 0)
        {
            write((int) (current << (8 - fill)));
            fill = 0;
            current = 0;
        }

        os.write(buffer, 0, pos);
        pos = 0;

        os.flush();
    }

    public void close() throws IOException
    {
        flush();
        os.close();
        os = null;
        buffer = null;
    }

    private void write(final int b) throws IOException
    {
        if (pos == buffer.length)
        {
            os.write(buffer);
            pos = 0;
        }
        buffer[pos++] = (byte) b;
    }

    public int writeBit(final int bit) throws IOException
    {
        return writeInt(bit, 1);
    }

    public int writeInt(int x, final int len) throws IOException
    {
        // At most 7 pending bits plus 32 new bits fit in a long, so there is
        // no need to split the value across bytes via a temporary array.
        current = (current << len) | (x & ((1L << len) - 1));
        int remaining = fill + len;
        if (pos + (remaining >>> 3) <= buffer.length)
        {
            // Common case: check capacity once for all complete bytes and
            // store them directly into the buffer.
            int position = pos;
            while (remaining >= 8)
            {
                remaining -= 8;
                buffer[position++] = (byte) (current >>> remaining);
            }
            pos = position;
            fill = remaining;
        }
        else
        {
            fill = remaining;
            while (fill >= 8)
            {
                fill -= 8;
                write((int) (current >>> fill));
            }
        }
        return len;
    }

}
