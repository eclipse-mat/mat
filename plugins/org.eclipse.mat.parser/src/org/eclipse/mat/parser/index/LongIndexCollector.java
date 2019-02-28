package org.eclipse.mat.parser.index;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.collect.HashMapIntObject;

public class LongIndexCollector
{
    final int mostSignificantBit;
    final int size;
    final int pageSize = IndexWriter.PAGE_SIZE_LONG;
    final ConcurrentHashMap<Integer, ArrayLongCompressed> pages = new ConcurrentHashMap<Integer, ArrayLongCompressed>();

    public LongIndexCollector(int size, int mostSignificantBit)
    {
        this.size = size;
        this.mostSignificantBit = mostSignificantBit;
    }

    protected ArrayLongCompressed getOrCreatePage(int page)
    {
        ArrayLongCompressed existing = pages.get(page);
        if (existing != null) return existing;

        int ps = page < (size / pageSize) ? pageSize : size % pageSize;
        ArrayLongCompressed newArray = new ArrayLongCompressed(ps, 63 - mostSignificantBit, 0);
        existing = pages.putIfAbsent(page, newArray);
        return (existing != null) ? existing : newArray;
    }

    public IIndexReader.IOne2LongIndex writeTo(File indexFile) throws IOException
    {
        HashMapIntObject<Object> output = new HashMapIntObject<Object>(pages.size());
        for(Map.Entry<Integer, ArrayLongCompressed> entry : pages.entrySet()) {
            output.put(entry.getKey(), entry.getValue());
        }
        // needed to re-compress
        return new LongIndexStreamer().writeTo(indexFile, this.size, output, this.pageSize);
    }

    public void set(int index, long value)
    {
        ArrayLongCompressed array = getOrCreatePage(index / pageSize);
        // uses bit operations internally, so we should sync against the page
        synchronized(array)
        {
            array.set(index % pageSize, value);
        }
    }
}
