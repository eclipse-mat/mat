/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation (Andrew Johnson) - tidy up for incomplete reads
 *******************************************************************************/
package org.eclipse.mat.parser.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;

import org.eclipse.mat.collect.HashMapLongObject;

/**
 * Used to wrap a {@link RandomAccessFile} with multiple buffers so that different locations
 * can each have a buffer for the file.
 */
public class BufferedRandomAccessInputStream extends InputStream
{
    /** The underlying {@link RandomAccessFile}. */
    RandomAccessFile raf;

    private class Page
    {
        long real_pos_start;
        byte[] buffer;
        int buf_end;

        public Page()
        {
            buffer = new byte[bufsize];
        }
    }

    /** The size of each buffer */
    int bufsize;
    /** The file length, used for EOF processing. */
    long fileLength;
    /** The position of the {@link RandomAccessFile}. */
    long real_pos;
    /** The position as shown to the caller. */
    long reported_pos;

    HashMapLongObject<SoftReference<Page>> pages = new HashMapLongObject<SoftReference<Page>>();

    Page current;

    public BufferedRandomAccessInputStream(RandomAccessFile in) throws IOException
    {
        this(in, 1024);
    }

    public BufferedRandomAccessInputStream(RandomAccessFile in, int bufsize) throws IOException
    {
        this.bufsize = bufsize;
        this.raf = in;
        this.fileLength = in.length();
    }

    public final int read() throws IOException
    {
        if (reported_pos == fileLength)
            return -1;

        if (current == null || (reported_pos - current.real_pos_start) >= current.buf_end)
            current = getPage(reported_pos);
        if (reported_pos - current.real_pos_start >= current.buf_end)
            return -1;

        return current.buffer[((int) (reported_pos++ - current.real_pos_start))] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (b == null)
        {
            throw new NullPointerException();
        }
        else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
        {
            throw new IndexOutOfBoundsException();
        }
        else if (len == 0) { return 0; }

        if (reported_pos == fileLength)
            return -1;

        int copied = 0;

        while (copied < len)
        {
            if (reported_pos == fileLength)
                return copied;

            if (current == null || (reported_pos - current.real_pos_start) >= current.buf_end)
                current = getPage(reported_pos);
            if (reported_pos - current.real_pos_start >= current.buf_end)
                return copied > 0 ? copied : -1;

            int buf_pos = (int) (reported_pos - current.real_pos_start);
            int length = Math.min(len - copied, current.buf_end - buf_pos);
            System.arraycopy(current.buffer, buf_pos, b, off + copied, length);

            reported_pos += length;
            copied += length;
        }

        return copied;
    }

    private Page getPage(long pos) throws IOException
    {
        long key = pos / bufsize;

        SoftReference<Page> r = pages.get(key);
        Page p = r == null ? null : r.get();

        long page_start = key * bufsize;

        // Existing page
        if (p != null)
        {   
            // Has enough data?
            if (page_start + p.buf_end > pos)
                return p;
            // Move to the right place to append to buffer 
            if (page_start + p.buf_end != real_pos)
            {
                raf.seek(page_start + p.buf_end);
                real_pos = page_start + p.buf_end;
            }
            // Fill the page to the required position
            while (real_pos <= pos)
            {
                int n = raf.read(p.buffer, p.buf_end, bufsize - p.buf_end);
                if (n >= 0)
                {
                    p.buf_end += n;
                    real_pos += n;
                }
                else
                {
                    // No more data
                    if (real_pos < fileLength)
                        fileLength = real_pos;
                    break;
                }
            }
            return p;
        }

        if (page_start != real_pos)
        {
            raf.seek(page_start);
            real_pos = page_start;
        }

        p = new Page();

        int n = raf.read(p.buffer);
        if (n >= 0)
        {
            p.real_pos_start = real_pos;
            p.buf_end = n;
            real_pos += n;
            // Fill the page to the required position
            while (real_pos <= pos)
            {
                n = raf.read(p.buffer, p.buf_end, bufsize - p.buf_end);
                if (n >= 0)
                {
                    p.buf_end += n;
                    real_pos += n;
                }
                else
                {
                    // No more data
                    if (real_pos < fileLength)
                        fileLength = real_pos;
                    break;
                }
            }
        }
        else
        {
            // No data read
            p.real_pos_start = real_pos;
            if (real_pos < fileLength)
                fileLength = real_pos;
        }

        pages.put(key, new SoftReference<Page>(p));

        return p;
    }

    public boolean markSupported()
    {
        return false;
    }

    public void close() throws IOException
    {
        raf.close();
    }

    /**
     * @throws IOException
     */
    public void seek(long pos) throws IOException
    {
        reported_pos = pos;
        current = null;
    }

    public long getFilePointer()
    {
        return reported_pos;
    }
}
