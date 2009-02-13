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
package org.eclipse.mat.parser.internal.util;

import java.util.BitSet;

public class LimitedValueIntStore
{
    private static int SIZE_SZ = 4;
    private static int PREV_SZ = 25;
    private static int END_MARKER = ~0 >>> (32 - PREV_SZ); // only 111111s
    private static int NUM_VALUES = 15;

    int[] data;
    int numBits; // number of bits needed for a value
    int size; // number of entries
    int entrySize; // size of each entry
    BitSet used;
    int usedCount;

    public LimitedValueIntStore(int maxValue, int initialCapacity)
    {
        for (numBits = 0; maxValue > Math.pow(2, numBits); numBits++)
        {
            // empty
        }

        /* sz, , NEXT_SZ, nvalue entries bits next; */
        entrySize = SIZE_SZ + PREV_SZ + NUM_VALUES * numBits;

        size = initialCapacity;

        int length = (size * entrySize) / 32;
        length = length % 32 == 0 ? length : length + 32 - length % 32; // align
        data = new int[length];

        used = new BitSet(size);
    }

    public final int add(int index, int value)
    {
        // removed the check intentionally
        // if (value > maxValue)
        // throw new IllegalArgumentException("Value: " + value +
        // " is bigger than the defined maximum: " + maxValue);

        int count = readBits(index * entrySize, SIZE_SZ);
        if (count == NUM_VALUES)
        {
            // add next entry
            int newAddr = create();
            // set the current entry as prev of the new one
            setBits(newAddr * entrySize + SIZE_SZ, PREV_SZ, index);
            index = newAddr;
            count = 0;
        }

        setBits(index * entrySize, SIZE_SZ, count + 1);
        setBits(index * entrySize + SIZE_SZ + PREV_SZ + count * numBits, numBits, value);

        // return a possibly new index
        return index;
    }

    public final int[] get(int index)
    {
        IntStack stack = new IntStack(); // needed to reorder the result
        int prev = index;

        do
        {
            stack.push(prev);
            prev = readBits(prev * entrySize + SIZE_SZ, PREV_SZ);
        }
        while (prev != END_MARKER);

        int lastSize = readBits(index * entrySize, SIZE_SZ);
        int[] result = new int[(stack.size() - 1) * NUM_VALUES + lastSize];

        int i = 0;
        while (stack.size() > 0)
        {
            int current = stack.pop();
            lastSize = readBits(current * entrySize, SIZE_SZ);
            for (int j = 0; j < lastSize; j++)
            {
                result[i++] = readBits(current * entrySize + SIZE_SZ + PREV_SZ + j * numBits, numBits);
            }
        }

        return result;
    }

    public final int create()
    {
        if (usedCount == size)
        {
            int[] newData = new int[data.length * 2];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
            size *= 2;
        }
        int index = used.nextClearBit(0);

        used.set(index);
        usedCount++;
        setBits(index * entrySize, SIZE_SZ, 0); // clear the size bits
        setBits(index * entrySize + SIZE_SZ, PREV_SZ, END_MARKER); // clear the
        // prev bits

        return index;
    }

    public final void dispose(int index)
    {
        do
        {
            used.clear(index);
            usedCount--;
            index = readBits(index * entrySize + SIZE_SZ, PREV_SZ);
        }
        while (index != END_MARKER);
    }

    private int readBits(int pos, int length)
    {
        // calculate the index
        int idx = pos >>> 5;

        // does it fit into one int?
        int off = pos & 0x1f;
        if ((off + length) <= 0x20)
        {
            return ((data[idx] << off) >>> (0x20 - length));
        }
        else
        {
            return ((data[idx] << off) >>> (0x20 - length)) | data[idx + 1] >>> (0x40 - length - off);
        }
    }

    private void setBits(int pos, int length, int value)
    {
        int idx = pos >>> 5;
        int off = pos & 0x1f; // pos % 32
        if (off + length <= 0x20) // will fit into one int
        {
            // do not check if the value fits...
            int clear = ~0;

            // prepare a mask to keep the bits around the value we set
            clear <<= off;
            clear >>>= off;

            clear >>>= 0x20 - off - length;
            clear <<= 0x20 - off - length;

            clear = ~clear;

            value <<= 0x20 - off - length;

            data[idx] &= clear; // clear only the bits which will be set
            data[idx] |= value; // set the bits
        }
        else
        // have to set two ints
        {
            int tmp = data[idx]; // take the int
            tmp >>>= 0x20 - off; // clear the needed trailing bits
            tmp <<= 0x20 - off;
            tmp |= (value >>> (length - (0x20 - off))); // set the cleared bits
                                                        // with data
            data[idx] = tmp;

            tmp = data[idx + 1]; // take the second int
            tmp <<= (length - (0x20 - off)); // clear the leading needed bits
            tmp >>>= (length - (0x20 - off));
            tmp |= value << (0x20 - (length - (0x20 - off))); // set the cleared
                                                              // bits with data
            data[idx + 1] = tmp;

        }
    }

}
