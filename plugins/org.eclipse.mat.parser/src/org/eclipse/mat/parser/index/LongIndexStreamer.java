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
import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IndexWriter.LongIndex;

public class LongIndexStreamer extends LongIndex
{
    DataOutputStream out;
    ArrayLong pageStart;
    long[] page;
    int left;

    // tracks number of pages added, may not be available if they are still being compressed
    int pagesAdded;

    // if the writer task has an exception during async we want to throw it on the next write
    Exception storedException = null;

    // TODO bound these queues
    // for the moment it is ok, as the compress and write stage is fast
    // could do more threads but no point
    final ExecutorService compressor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "LongIndexStreamer-Compressor"));
    // this must be single threaded to ensure page update logic stays correct
    final ExecutorService writer = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "LongIndexStreamer-Writer"));

    public LongIndexStreamer()
    {}

    public LongIndexStreamer(File indexFile) throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
        openStream(out, 0);
    }

    public void close() throws IOException
    {
        DataOutputStream out = this.out;
        closeStream();
        out.close();
    }

    public IOne2LongIndex writeTo(File indexFile, int size, HashMapIntObject<Object> pages, int pageSize)
                    throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

        openStream(out, 0);

        int noOfPages = size / pageSize + (size % pageSize > 0 ? 1 : 0);
        for (int ii = 0; ii < noOfPages; ii++)
        {
            ArrayLongCompressed a = (ArrayLongCompressed) pages.get(ii);
            int len = (ii + 1) < noOfPages ? pageSize : (size % pageSize);

            if (a == null)
                addAll(new long[len]);
            else
                for (int jj = 0; jj < len; jj++)
                {
                    add(a.get(jj));
                }
        }

        closeStream();
        out.close();

        return getReader(indexFile);
    }

    public IOne2LongIndex writeTo(File indexFile, long[] array) throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

        openStream(out, 0);
        addAll(array);
        closeStream();

        out.close();

        return getReader(indexFile);
    }

    public IOne2LongIndex writeTo(File indexFile, IteratorLong iterator) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(indexFile);
        try
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos));

            openStream(out, 0);
            addAll(iterator);
            closeStream();

            out.flush();

            return getReader(indexFile);
        }
        finally
        {
            try
            {
                fos.close();
            }
            catch (IOException ignore)
            {}
        }
    }

    public IOne2LongIndex writeTo(File indexFile, ArrayLong array) throws IOException
    {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

        openStream(out, 0);
        addAll(array);
        closeStream();

        out.close();

        return getReader(indexFile);
    }

    IIndexReader.IOne2LongIndex writeTo(DataOutputStream out, long position, IteratorLong iterator)
                    throws IOException
    {
        openStream(out, position);
        addAll(iterator);
        closeStream();

        return getReader(null);
    }

    void openStream(DataOutputStream out, long position)
    {
        this.out = out;

        init(0, IndexWriter.PAGE_SIZE_LONG);

        this.page = new long[pageSize];
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
        out.writeInt(size);

        this.page = null;

        this.out = null;

        return this.pageStart.lastElement() + (8 * pageStart.size()) + 8 - this.pageStart.firstElement();
    }

    IndexReader.LongIndexReader getReader(File indexFile) throws IOException
    {
        return new IndexReader.LongIndexReader(indexFile, pages, size, pageSize, pageStart.toArray());
    }

    public void addAll(IteratorLong iterator) throws IOException
    {
        while (iterator.hasNext())
            add(iterator.next());
    }

    public void addAll(ArrayLong array) throws IOException
    {
        for (IteratorLong e = array.iterator(); e.hasNext();)
            add(e.next());
    }

    public void add(long value) throws IOException
    {
        if (left == 0)
            addPage();

        page[page.length - left--] = value;
        size++;

    }

    public void addAll(long[] values) throws IOException
    {
        addAll(values, 0, values.length);
    }

    public void addAll(long[] values, int offset, int length) throws IOException
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

    private void addPage() throws IOException
    {
        Future<byte[]> compressionResult = compressor.submit(new CompressionTask(page, left, pagesAdded));
        // compression may finish in any order, but writes have to be correct, so order them
        writer.execute(new WriterTask(compressionResult));

        pagesAdded += 1;
        page = new long[page.length];
        left = page.length;
    }

    @Override
    protected ArrayLongCompressed getPage(int page)
    {
        throw new UnsupportedOperationException();
    }

    /*
     * Compresses a page of data to a byte array
     */
    private class CompressionTask implements Callable<byte[]>
    {
        final long[] page;
        final int left;
        final int pageNumber;
        public CompressionTask(final long[] page, final int left, final int pageNumber)
        {
            this.page = page;
            this.left = left;
            this.pageNumber = pageNumber;
        }

        public byte[] call()
        {
            ArrayLongCompressed array = new ArrayLongCompressed(page, 0, page.length - left);
            pages.put(pageNumber, new SoftReference<ArrayLongCompressed>(array));
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
