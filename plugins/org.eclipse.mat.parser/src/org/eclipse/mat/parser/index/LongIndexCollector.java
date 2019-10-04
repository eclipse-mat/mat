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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.parser.index.IndexWriter.LongIndex;

class LongIndexCollector extends LongIndex
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

    protected ArrayLongCompressed getPage(int page)
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
        ArrayLongCompressed array = getPage(index / pageSize);
        // uses bit operations internally, so we should sync against the page
        // TODO unlock this by having ArrayLongCompressed use atomics
        synchronized(array)
        {
            array.set(index % pageSize, value);
        }
    }
}
