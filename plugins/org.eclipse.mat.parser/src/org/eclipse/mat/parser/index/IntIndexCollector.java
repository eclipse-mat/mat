package org.eclipse.mat.parser.index;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IndexWriter.IntIndex;

public class IntIndexCollector extends IntIndex<ArrayIntCompressed> implements IOne2OneIndex
{
    int mostSignificantBit;

    public IntIndexCollector(int size, int mostSignificantBit)
    {
        super(size);
        this.mostSignificantBit = mostSignificantBit;
    }

    @Override
    protected ArrayIntCompressed getPage(int page)
    {
        ArrayIntCompressed array = pages.get(page);
        if (array == null)
        {
            int ps = page < page(size) ? pageSize : offset(size);
            array = new ArrayIntCompressed(ps, 31 - mostSignificantBit, 0);
            pages.put(page, array);
        }
        return array;
    }

    public IIndexReader.IOne2OneIndex writeTo(File indexFile) throws IOException
    {
        // needed to re-compress
        return new IntIndexStreamer().writeTo(indexFile, this.iterator());
    }

    public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position) throws IOException
    {
        return new IntIndexStreamer().writeTo(out, position, this.iterator());
    }

    public void close() throws IOException
    {}

    public void delete()
    {
        pages = null;
    }
}