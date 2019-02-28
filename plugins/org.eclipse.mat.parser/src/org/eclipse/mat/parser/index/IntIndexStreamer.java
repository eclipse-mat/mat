package org.eclipse.mat.parser.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;

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

    private void addPage() throws IOException
    {
        ArrayIntCompressed array = new ArrayIntCompressed(page, 0, page.length - left);

        byte[] buffer = array.toByteArray();
        out.write(buffer);
        int written = buffer.length;

        pages.put(pages.size(), new SoftReference<ArrayIntCompressed>(array));
        pageStart.add(pageStart.lastElement() + written);

        left = page.length;
    }

    @Override
    protected ArrayIntCompressed getPage(int page)
    {
        throw new UnsupportedOperationException();
    }

}