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

import java.util.Arrays;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

/**
 * Utility class to hold a list of ints
 * Similar to a list, but efficient for ints
 */
public final class ArrayInt
{
    int elements[];
    int size;

    /**
     * Create a list of default size
     */
    public ArrayInt()
    {
        this(10);
    }

    /**
     * Create a list of given size
     * @param initialCapacity
     */
    public ArrayInt(int initialCapacity)
    {
        elements = new int[initialCapacity];
        size = 0;
    }

    /**
     * Create a list based on a supplied array
     * @param initialValues a copy is taken of this array
     */
    public ArrayInt(int[] initialValues)
    {
        this(initialValues.length);
        System.arraycopy(initialValues, 0, elements, 0, initialValues.length);
        size = initialValues.length;
    }

    /**
     * Create a list based on an existing ArrayInt, of size of the template
     * @param template a copy is taken of these values
     */
    public ArrayInt(ArrayInt template)
    {
        this(template.size);
        System.arraycopy(template.elements, 0, elements, 0, template.size);
        size = template.size;
    }

    /**
     * append one more entry
     * @param element the int to add to the end
     */
    public void add(int element)
    {
        ensureCapacity(size + 1);
        elements[size++] = element;
    }

    /**
     * append a group of entries
     * @param elements
     */
    public void addAll(int[] elements)
    {
        ensureCapacity(size + elements.length);
        System.arraycopy(elements, 0, this.elements, size, elements.length);
        size += elements.length;
    }

    /**
     * append all of another 
     * @param template
     */
    public void addAll(ArrayInt template)
    {
        ensureCapacity(size + template.size);
        System.arraycopy(template.elements, 0, elements, size, template.size);
        size += template.size;
    }

    /**
     * modify one particular entry
     * @param index
     * @param element
     * @return the previous value
     */
    public int set(int index, int element)
    {
        if (index < 0 || index >= size)
            throw new ArrayIndexOutOfBoundsException(index);

        int oldValue = elements[index];
        elements[index] = element;
        return oldValue;
    }

    /**
     * retrieve one entry
     * @param index
     * @return the entry
     */
    public int get(int index)
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
    public int[] toArray()
    {
        int[] result = new int[size];
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
    public IteratorInt iterator()
    {
        return new IteratorInt()
        {
            int index = 0;

            public boolean hasNext()
            {
                return index < size;
            }

            public int next()
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
     * @since 1.0
     */
    public int lastElement()
    {
        return elements[size - 1];
    }

    /**
     * get the first entry to be written.
     * Must be at least one entry.
     * @return the first element
     * @since 1.0
     */
    public int firstElement()
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

    /**
     * Truncate the array
     * @param newCapacity the new size
     * @since 1.6
     */
    public void truncate(int newCapacity) {
        int oldData[] = elements;
        elements = new int[newCapacity];
        System.arraycopy(oldData, 0, elements, 0, newCapacity);
        size = newCapacity;
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
                throw new OutOfMemoryError(MessageUtil.format(Messages.ArrayInt_Error_LengthExceeded, minCapacity, newCapacity));
            }
            int oldData[] = elements;
            elements = new int[newCapacity];
            System.arraycopy(oldData, 0, elements, 0, size);
        }
    }

    private int newCapacity(int oldCapacity, int minCapacity)
    {
        // Scale by 1.5 without overflow
        int newCapacity = oldCapacity * 3 >>> 1;
        if (newCapacity < minCapacity)
        {
            newCapacity = minCapacity * 3 >>> 1;
            if (newCapacity < minCapacity)
            {
                // Avoid VM limits for final size
                newCapacity = Integer.MAX_VALUE - 8;
            }
        }
        return newCapacity;
    }
}
