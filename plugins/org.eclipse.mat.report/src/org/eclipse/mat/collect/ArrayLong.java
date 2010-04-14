/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.collect;

import java.util.Arrays;

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
     * Create a list of given size
     * @param initialCapacity
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
     * append a group of entries
     * @param elements
     */
    public void addAll(long[] elements)
    {
        ensureCapacity(size + elements.length);
        System.arraycopy(elements, 0, this.elements, size, elements.length);
        size += elements.length;
    }

    /**
     * append all of another 
     * @param template
     */
    public void addAll(ArrayLong template)
    {
        ensureCapacity(size + template.size);
        System.arraycopy(template.elements, 0, elements, size, template.size);
        size += template.size;
    }

    /**
     * modify one particular entry
     * @param index
     * @param element
     * @return
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
     * retrieve one entry
     * @param index
     * @return
     */
    public long get(int index)
    {
        if (index < 0 || index >= size)
            throw new ArrayIndexOutOfBoundsException(index);
        return elements[index];
    }

    /**
     * get the number of used entries
     * @return
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
     * @return
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * get an iterator to go through the list
     * @return
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
     * @return
     */
    public long lastElement()
    {
        return elements[size - 1];
    }

    /**
     * get the first entry to be written.
     * Must be at least one entry.
     * @return
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
        Arrays.sort(elements, 0, size);
    }

    // //////////////////////////////////////////////////////////////
    // implementation stuff
    // //////////////////////////////////////////////////////////////

    private void ensureCapacity(int minCapacity)
    {
        int oldCapacity = elements.length;
        if (minCapacity > oldCapacity)
        {
            long oldData[] = elements;
            int newCapacity = (oldCapacity * 3) / 2 + 1;
            if (newCapacity < minCapacity)
                newCapacity = minCapacity;
            elements = new long[newCapacity];
            System.arraycopy(oldData, 0, elements, 0, size);
        }
    }

}
