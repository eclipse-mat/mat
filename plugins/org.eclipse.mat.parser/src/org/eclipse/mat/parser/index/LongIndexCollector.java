package org.eclipse.mat.parser.index;

import java.io.File;
import java.io.IOException;

import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.parser.index.IndexWriter.LongIndex;

public class LongIndexCollector extends LongIndex
{
    int mostSignificantBit;

    public LongIndexCollector(int size, int mostSignificantBit)
    {
        super(size);
        this.mostSignificantBit = mostSignificantBit;
    }

    @Override
    protected ArrayLongCompressed getPage(int page)
    {
        ArrayLongCompressed array = (ArrayLongCompressed) pages.get(page);
        if (array == null)
        {
            int ps = page < (size / pageSize) ? pageSize : size % pageSize;
            array = new ArrayLongCompressed(ps, 63 - mostSignificantBit, 0);
            pages.put(page, array);
        }
        return array;
    }

    public IIndexReader.IOne2LongIndex writeTo(File indexFile) throws IOException
    {
        // needed to re-compress
        return new LongIndexStreamer().writeTo(indexFile, this.size, this.pages, this.pageSize);
    }
}