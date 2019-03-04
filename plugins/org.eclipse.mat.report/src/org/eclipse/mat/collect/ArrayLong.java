/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.collect;

import java.util.Arrays;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

/**
 * Utility class to hold a list of longs
 * Similar to a list, but efficient for longs
 */
public final class ArrayLong
{
    long elements[];
    int size;

    /**
     * Create a list of default size
     */
    public ArrayLong()
    {
        this(10);
    }

    /**
     * Create an empty list of given capacity for more entries.
     * @param initialCapacity in number of entries
     */
    public ArrayLong(int initialCapacity)
    {
        elements = new long[initialCapacity];
        size = 0;
    }

    /**
     * Create a list based on a supplied array
     * @param initialValues a copy is taken of this array
     */
    public ArrayLong(long[] initialValues)
    {
        this(initialValues.length);
        System.arraycopy(initialValues, 0, elements, 0, initialValues.length);
        size = initialValues.length;
    }

    /**
     * Create a list based on an existing ArrayInt, of size of the template
     * @param template a copy is taken of these values
     */
    public ArrayLong(ArrayLong template)
    {
        this(template.size);
        System.arraycopy(template.elements, 0, elements, 0, template.size);
        size = template.size;
    }

    /**
     * append one more entry
     * @param element the int to add to the end
     */
    public void add(long element)
    {
        ensureCapacity(size + 1);
        elements[size++] = element;
    }

    /**
     * Append a group of entries
     * @param elements an array of long, to be added to end of this ArrayLong.
     */
    public void addAll(long[] elements)
    {
        ensureCapacity(size + elements.length);
        System.arraycopy(elements, 0, this.elements, size, elements.length);
        size += elements.length;
    }

    /**
     * Append all of another  ArrayLong to the end of this one.
     * @param template the other ArrayLong
     */
    public void addAll(ArrayLong template)
    {
        ensureCapacity(size + template.size);
        System.arraycopy(template.elements, 0, elements, size, template.size);
        size += template.size;
    }

    /**
     * modify one particular entry
     * @param index into this ArrayLong
     * @param element the new value to be put here
     * @return the previous value
     */
    public long set(int index, long element)
    {
        if (index < 0 || index >= size)
            throw new ArrayIndexOutOfBoundsException(index);

        long oldValue = elements[index];
        elements[index] = element;
        return oldValue;
    }

    /**
     * Retrieve one entry
     * @param index into the ArrayLong
     * @return the entry
     */
    public long get(int index)
    {
        if (index < 0 || index >= size)
            throw new ArrayIndexOutOfBoundsException(index);
        return elements[index];
    }

    /**
     * get the number of used entries
     * @return the number of entries
     */
    public int size()
    {
        return size;
    }

    /** 
     * convert to an array
     * @return a copy of the entries
     */
    public long[] toArray()
    {
        long[] result = new long[size];
        System.arraycopy(elements, 0, result, 0, size);
        return result;
    }

    /**
     * is the list empty
     * @return true if empty
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * get an iterator to go through the list
     * @return the iterator
     */
    public IteratorLong iterator()
    {
        return new IteratorLong()
        {
            int index = 0;

            public boolean hasNext()
            {
                return index < size;
            }

            public long next()
            {
                return elements[index++];
            }
        };
    }

    /** 
     * clear all the entries
     */
    public void clear()
    {
        size = 0;
    }

    /**
     * get the last entry to be written.
     * Must be at least one entry.
     * @return the last element
     */
    public long lastElement()
    {
        return elements[size - 1];
    }

    /**
     * get the first entry to be written.
     * Must be at least one entry.
     * @return the first element
     */
    public long firstElement()
    {
        if (size == 0)
            throw new ArrayIndexOutOfBoundsException();

        return elements[0];
    }

    /**
      * arrange the entries in ascending order
      */
    public void sort()
    {
        Arrays.parallelSort(elements, 0, size);
    }

    // //////////////////////////////////////////////////////////////
    // implementation stuff
    // //////////////////////////////////////////////////////////////

    private void ensureCapacity(int minCapacity)
    {
        int oldCapacity = elements.length;
        if (minCapacity > oldCapacity)
        {
            int newCapacity = newCapacity(oldCapacity, minCapacity);
            if (newCapacity < minCapacity)
            {
                // Avoid strange exceptions later
                throw new OutOfMemoryError(MessageUtil.format(Messages.ArrayLong_Error_LengthExceeded, minCapacity, newCapacity));
            }
            long oldData[] = elements;
            elements = new long[newCapacity];
            System.arraycopy(oldData, 0, elements, 0, size);
        }
    }

    private int newCapacity(int oldCapacity, int minCapacity)
    {
        // Scale by 1.5 without overflow
        int newCapacity = (oldCapacity * 3 >>> 1);
        if (newCapacity < minCapacity)
        {
            newCapacity = (minCapacity * 3 >>> 1);
            if (newCapacity < minCapacity)
            {
                // Avoid VM limits for final size
                newCapacity = Integer.MAX_VALUE - 8;
            }
        }
        return newCapacity;
    }
}
