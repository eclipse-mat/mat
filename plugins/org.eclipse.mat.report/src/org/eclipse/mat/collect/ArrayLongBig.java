/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.collect;

import java.util.ArrayList;

/**
 * This class simplifies the handling of growing long[] in a very fast and
 * memory efficient manner so that no slow collections must be used. However
 * this class is only fast on big long[] and not on small ones where you collect
 * just a couple of longs. The internal data is never copied during the process
 * of growing. Only with {@link #toArray} the data is copied to the result
 * long[].
 */
public final class ArrayLongBig
{
    private ArrayList<long[]> pages;
    private long[] page;
    private int length;

    /**
     * Create an <code>LongArray</code>. Memory consumption is equal to creating
     * a new <code>ArrayList</code>.
     */
    public ArrayLongBig()
    {
        pages = new ArrayList<long[]>();
        length = 0;
    }

    /**
     * Add long to <code>LongArray</code>.
     * 
     * @param element
     *            long which should be added
     */
    public final void add(long element)
    {
        int index = (length++) & 0x3FF;
        if (index == 0)
        {
            pages.add(page = new long[0x400]);
        }
        page[index] = element;
    }

    /**
     * Add long[] to <code>LongArray</code>.
     * 
     * @param elements
     *            long[] which should be added
     */
    public final void addAll(long[] elements)
    {
        int free = (length & 0x3FF);
        int bite = free == 0 ? 0 : Math.min(elements.length, 0x400 - free);
        if (bite > 0)
        {
            System.arraycopy(elements, 0, pages.get(length >> 10), length & 0x3FF, bite);
            length += bite;
        }
        int copied = bite;
        while (copied < elements.length)
        {
            pages.add(page = new long[0x400]);
            bite = Math.min(elements.length - copied, 0x400);
            System.arraycopy(elements, copied, page, 0, bite);
            copied += bite;
            length += bite;
        }
    }

    /**
     * Get long at index from <code>LongArray</code>.
     * 
     * @param index
     *            index of long which should be returned
     * @return long at index
     * @throws IndexOutOfBoundsException
     */
    public final long get(int index) throws IndexOutOfBoundsException
    {
        if (index >= length) { throw new IndexOutOfBoundsException(); }
        return pages.get(index >> 10)[index & 0x3FF];
    }

    /**
     * Get length of <code>LongArray</code>.
     * 
     * @return length of <code>LongArray</code>
     */
    public final int length()
    {
        return length;
    }

    /**
     * Get memory consumption of <code>LongArray</code>.
     * 
     * @return memory consumption of <code>LongArray</code>
     */
    public final long consumption()
    {
        return ((long) pages.size()) << 13;
    }

    /**
     * Convert <code>LongArray</code> to long[]. This operation is the only one
     * where the internal data is copied. It is directly copied to the long[]
     * which is returned, so don't call this method more than once when done.
     * 
     * @return long[] representing the <code>LongArray</code>
     */
    public final long[] toArray()
    {
        long[] elements = new long[length];
        int bite;
        int copied = 0;
        while (copied < length)
        {
            bite = Math.min(length - copied, 0x400);
            System.arraycopy(pages.get(copied >> 10), 0, elements, copied, bite);
            copied += bite;
        }
        return elements;
    }
}
