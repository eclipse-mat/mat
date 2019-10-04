/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
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

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IndexWriter.IntIndex;

class IntIndexCollector extends IntIndex<ArrayIntCompressed> implements IOne2OneIndex
{
    final int mostSignificantBit;
    final int size;
    final int pageSize = IndexWriter.PAGE_SIZE_INT;
    final ConcurrentHashMap<Integer, ArrayIntCompressed> pages = new ConcurrentHashMap<Integer, ArrayIntCompressed>();

    public IntIndexCollector(int size, int mostSignificantBit)
    {
        this.size = size;
        this.mostSignificantBit = mostSignificantBit;
    }

    protected ArrayIntCompressed getPage(int page)
    {
        ArrayIntCompressed existing = pages.get(page);
        if (existing != null) return existing;

        int ps = page < (size / pageSize) ? pageSize : size % pageSize;
        ArrayIntCompressed newArray = new ArrayIntCompressed(ps, 31 - mostSignificantBit, 0);
        existing = pages.putIfAbsent(page, newArray);
        return (existing != null) ? existing : newArray;
    }

    public IIndexReader.IOne2OneIndex writeTo(File indexFile) throws IOException
    {
        // needed to re-compress
        return new IntIndexStreamer().writeTo(indexFile, new IteratorInt()
        {
            int index = 0;
            public boolean hasNext()
            {
                return (index < size);
            }

            public int next()
            {
                return get(index++);
            }
        });
    }

    public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position) throws IOException
    {
        // needed to re-compress
        return new IntIndexStreamer().writeTo(out, position, new IteratorInt()
        {
            int index = 0;
            public boolean hasNext()
            {
                return (index < size);
            }

            public int next()
            {
                return get(index++);
            }
        });
    }

    public void set(int index, int value)
    {
        ArrayIntCompressed array = getPage(index / pageSize);
        // TODO unlock this by having ArrayIntCompressed use atomics
        // uses bit operations internally, so we should sync against the page
        synchronized(array)
        {
            array.set(index % pageSize, value);
        }
    }

    public int get(int index)
    {
        ArrayIntCompressed array = getPage(index / pageSize);

        // TODO unlock this by having ArrayIntCompressed use atomics
        // we currently lock a whole page, when we only need a single element
        synchronized(array) {
            return array.get(index % pageSize);
        }
    }

    public void close() throws IOException
    { }

    public void delete()
    {
        pages.clear();
    }

    public int size()
    {
        return size;
    }

    public void unload()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    public int[] getAll(int[] index)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    public int[] getNext(int index, int length)
    {
        throw new UnsupportedOperationException("not implemented");
    }
}
