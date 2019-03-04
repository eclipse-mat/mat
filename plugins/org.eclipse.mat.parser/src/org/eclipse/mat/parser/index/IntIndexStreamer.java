/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - enhancements for huge dumps
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.parser.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.index.IndexWriter.IntIndex;

public class IntIndexStreamer extends IntIndex<SoftReference<ArrayIntCompressed>>
{
    DataOutputStream out;
    ArrayLong pageStart;
    int[] page;
    int left;

    // tracks number of pages added, may not be available if they are still being compressed
    int pagesAdded;

    // if the writer task has an exception during async we want to throw it on the next write
    Exception storedException = null;

    // TODO bound these queues, and add more compressor threads if needed
    // for the moment it is ok, as the compress and write stage is fast
    // could do more threads but no point
    final ExecutorService compressor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "IntIndexStreamer-Compressor"));
    // this must be single threaded to ensure page update logic stays correct
    final ExecutorService writer = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "IntIndexStreamer-Writer"));

    public IIndexReader.IOne2OneIndex writeTo(File indexFile, IteratorInt iterator) throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

        openStream(out, 0);
        addAll(iterator);
        closeStream();

        out.close();

        return getReader(indexFile);
    }

    public IIndexReader.IOne2OneIndex writeTo(File indexFile, int[] array) throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

        openStream(out, 0);
        addAll(array);
        closeStream();

        out.close();

        return getReader(indexFile);
    }

    public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position, IteratorInt iterator)
                    throws IOException
    {
        openStream(out, position);
        addAll(iterator);
        closeStream();

        return getReader(null);
    }

    public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position, int[] array) throws IOException
    {
        openStream(out, position);
        addAll(array);
        closeStream();

        return getReader(null);
    }

    void openStream(DataOutputStream out, long position)
    {
        this.out = out;

        init(0, IndexWriter.PAGE_SIZE_INT);

        this.page = new int[pageSize];
        this.pageStart = new ArrayLong();
        this.pageStart.add(position);
        this.left = page.length;
    }

    /**
     * @return total bytes written to index file
     */
    long closeStream() throws IOException
    {
        if (left < page.length)
            addPage();

        try
        {
            compressor.shutdown();
            compressor.awaitTermination(100, TimeUnit.SECONDS);
            writer.shutdown();
            writer.awaitTermination(100, TimeUnit.SECONDS);
        }
        catch (InterruptedException ie)
        {
            throw new IOException(ie);
        }

        // write header information
        for (int jj = 0; jj < pageStart.size(); jj++)
            out.writeLong(pageStart.get(jj));

        out.writeInt(pageSize);
        // Encoded size is the negative number of entries in the last page
        int s = size <= IndexWriter.FORMAT1_MAX_SIZE ? (int)size : -(int)((size + pageSize - 1) % pageSize + 1);
        out.writeInt(s);

        this.page = null;

        this.out = null;

        return this.pageStart.lastElement() + (8 * pageStart.size()) + 8 - this.pageStart.firstElement();
    }

    IndexReader.IntIndexReader getReader(File indexFile)
    {
        return new IndexReader.IntIndexReader(indexFile, pages, size, pageSize, pageStart.toArray());
    }

    void addAll(IteratorInt iterator) throws IOException
    {
        while (iterator.hasNext())
            add(iterator.next());
    }

    void add(int value) throws IOException
    {
        if (left == 0)
            addPage();

        page[page.length - left--] = value;
        size++;

    }

    void addAll(int[] values) throws IOException
    {
        addAll(values, 0, values.length);
    }

    void addAll(int[] values, int offset, int length) throws IOException
    {
        while (length > 0)
        {
            if (left == 0)
                addPage();

            int chunk = Math.min(left, length);

            System.arraycopy(values, offset, page, page.length - left, chunk);
            left -= chunk;
            size += chunk;

            length -= chunk;
            offset += chunk;
        }
    }

    public long addAllWithLengthFirst(int[] values, int offset, int length) throws IOException
    {
        long startPos = size;
        add(length);
        addAll(values, offset, length);
        return startPos;
    }

    private void addPage() throws IOException
    {
        Future<byte[]> compressionResult = compressor.submit(new CompressionTask(page, left, pagesAdded));
        // compression may finish in any order, but writes have to be correct, so order them
        writer.execute(new WriterTask(compressionResult));

        pagesAdded += 1;
        page = new int[page.length];
        left = page.length;
    }

    @Override
    protected ArrayIntCompressed getPage(int page)
    {
        throw new UnsupportedOperationException();
    }

    /*
     * Compresses a page of data to a byte array
     */
    private class CompressionTask implements Callable<byte[]>
    {
        final int[] page;
        final int left;
        final int pageNumber;
        public CompressionTask(final int[] page, final int left, final int pageNumber)
        {
            this.page = page;
            this.left = left;
            this.pageNumber = pageNumber;
        }

        public byte[] call()
        {
            ArrayIntCompressed array = new ArrayIntCompressed(page, 0, page.length - left);
            pages.put(pageNumber, new SoftReference<ArrayIntCompressed>(array));
            return array.toByteArray();
        }
    }

    /*
     * Writes data to output
     * Assumes there is only a single WriterTask that can ever be running
     */
    private class WriterTask implements Runnable {
        final Future<byte[]> byteData;
        public WriterTask(final Future<byte[]> byteData) throws IOException
        {
            this.byteData = byteData;
            if (storedException != null)
            {
                throw new IOException("stored IO exception from writer", storedException);
            }
        }

        public void run() {
            byte[] buffer;
            try
            {
                buffer = byteData.get();
                out.write(buffer);
                int written = buffer.length;

                // update the shared page list
                pageStart.add(pageStart.lastElement() + written);
            }
            catch (InterruptedException | ExecutionException | IOException e)
            {
                storedException = e;
            }
        }
    }
}
