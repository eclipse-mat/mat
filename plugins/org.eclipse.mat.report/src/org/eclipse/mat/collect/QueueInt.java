/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/

package org.eclipse.mat.collect;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

/**
 * A simple queue of ints
 * @since 0.8
 */
public class QueueInt
{
    int[] data;
    int headIdx;
    int tailIdx;
    int size;
    int capacity;

    /**
     * Create a queue of specified initial capacity.
     * The queue can grow if required.
     * @param capacity the initial capacity
     */
    public QueueInt(int capacity)
    {
        this.capacity = capacity;
        data = new int[capacity];
    }

    /**
     * Retrieve the next element from the queue.
     * @return the next element
     */
    public final int get()
    {

        if (size == 0)
            throw new ArrayIndexOutOfBoundsException(Messages.QueueInt_ZeroSizeQueue);
        int result = data[headIdx];
        headIdx++;
        size--;

        if (headIdx == capacity)
            headIdx = 0;

        return result;
    }

    /**
     * The number of elements available for retrieval.
     * @return the size
     */
    public final int size()
    {
        return size;
    }

    /**
     * Add an element to the back of the queue.
     * @param x the element to add
     */
    public final void put(int x)
    {

        if (tailIdx == capacity)
            tailIdx = 0;

        if (size == capacity)
        {
            // resize
            int minCapacity = size + 1;
            int newCapacity = newCapacity(capacity, minCapacity);
            if (newCapacity < minCapacity)
            {
                // Avoid strange exceptions later
                throw new OutOfMemoryError(MessageUtil.format(Messages.QueueInt_Error_LengthExceeded, minCapacity, newCapacity));
            }
            capacity = newCapacity;
            int[] tmp = new int[capacity];
            int headToEnd = data.length - headIdx;
            System.arraycopy(data, headIdx, tmp, 0, headToEnd);
            if (tailIdx > 0)
                System.arraycopy(data, 0, tmp, headToEnd, tailIdx);

            headIdx = 0;
            tailIdx = data.length;

            data = tmp;
        }

        data[tailIdx] = x;
        size++;
        tailIdx++;
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
                newCapacity = Integer.MAX_VALUE - 8; // Avoid VM limits for final size
            }
        }
        return newCapacity;
    }

}
