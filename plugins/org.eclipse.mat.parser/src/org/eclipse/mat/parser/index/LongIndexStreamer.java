package org.eclipse.mat.parser.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;

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
        ArrayLongCompressed array = new ArrayLongCompressed(page, 0, page.length - left);

        byte[] buffer = array.toByteArray();
        out.write(buffer);
        int written = buffer.length;

        pages.put(pages.size(), new SoftReference<ArrayLongCompressed>(array));
        pageStart.add(pageStart.lastElement() + written);

        left = page.length;
    }

    @Override
    protected ArrayLongCompressed getPage(int page)
    {
        throw new UnsupportedOperationException();
    }

}