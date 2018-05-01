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
 *******************************************************************************/
package org.eclipse.mat.collect;

import java.io.Serializable;
import java.util.NoSuchElementException;

/**
 * Utility class to hold a set of longs
 * Similar to a Set, but efficient for longs
 */
public final class SetLong implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * Largest requested size that can be allocated on many VMs.
     * Size will be rounded up to the next prime, so choose prime - 1.
     * Biggest primes less than 2^31 are 0x7fffffff and 0x7fffffed,
     * but JVM limit can be less than Integer.MAX_VALUE.
     * E.g. ArrayList has a limit of Integer.MAX_VALUE - 8
     */
    private static final int BIG_CAPACITY = PrimeFinder.findPrevPrime(Integer.MAX_VALUE - 8 + 1) - 1;
    
    private int capacity;
    private int step;
    private int limit;
    private int size;
    private boolean[] used;
    private long[] keys;

    /**
     * Create a set of default size
     */
    public SetLong()
    {
        this(10);
    }

    /**
     * Create a set of given size
     * @param initialCapacity in number of longs
     */
    public SetLong(int initialCapacity)
    {
        init(initialCapacity);
    }

    /**
     * Add a value to the set 
     * @param key the value to add
     * @return return true if added 
     */
    public boolean add(long key)
    {
        if (size == limit)
        {
            // Double in size but avoid overflow or JVM limits
            resize(capacity <= BIG_CAPACITY >> 1 ? capacity << 1 : capacity < BIG_CAPACITY ? BIG_CAPACITY : capacity + 1);
        }
        int hash = hash(key) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key) { return false; }
            hash = (hash + step) % capacity;
        }
        used[hash] = true;
        keys[hash] = key;
        size++;
        return true;
    }

    /**
     * Remove a value from the set
     * @param key the value to add
     * @return return true if removed
     */
    public boolean remove(long key)
    {
        int hash = hash(key) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                used[hash] = false;
                size--;
                // Re-hash all follow-up entries anew; Do not fiddle with the
                // capacity limit (75 %) otherwise this code may loop forever
                hash = (hash + step) % capacity;
                while (used[hash])
                {
                    key = keys[hash];
                    used[hash] = false;
                    int newHash = hash(key) % capacity;
                    while (used[newHash])
                    {
                        newHash = (newHash + step) % capacity;
                    }
                    used[newHash] = true;
                    keys[newHash] = key;
                    hash = (hash + step) % capacity;
                }
                return true;
            }
            hash = (hash + step) % capacity;
        }
        return false;
    }

    /**
     * Find a value from the set
     * @param key the value to find
     * @return return true if found
     */
    public boolean contains(long key)
    {
        int hash = hash(key) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key) { return true; }
            hash = (hash + step) % capacity;
        }
        return false;
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
     * is the set empty
     * @return true if empty
     */
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /** 
     * clear all the entries
     */
    public void clear()
    {
        size = 0;
        used = new boolean[capacity];
    }

    /**
     * get an iterator to go through the set
     * @return the iterator
     */
    public IteratorLong iterator()
    {
        return new IteratorLong()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            public long next() throws NoSuchElementException
            {
                while (++i < used.length)
                {
                    if (used[i])
                    {
                        n++;
                        return keys[i];
                    }
                }
                throw new NoSuchElementException();
            }
        };
    }

    /** 
     * convert to an array
     * @return a copy of the entries
     */
    public long[] toArray()
    {
        long[] array = new long[size];
        int j = 0;
        for (int i = 0; i < used.length; i++)
        {
            if (used[i])
            {
                array[j++] = keys[i];
            }
        }
        return array;
    }

    private int hash(long key)
    {
        return (int) (key & Integer.MAX_VALUE);
    }

    private void init(int initialCapacity)
    {
        capacity = PrimeFinder.findNextPrime(initialCapacity);
        step = Math.max(1, PrimeFinder.findPrevPrime(initialCapacity / 3));
        limit = (int) (capacity * 0.75);
        clear();
        keys = new long[capacity];
    }

    private void resize(int newCapacity)
    {
        int oldSize = size;
        boolean[] oldUsed = used;
        long[] oldKeys = keys;
        // JIT bug with IBM Java 6.0 - avoid JIT using stale values
        keys = null;
        capacity = 0;
        // end work-around
        init(newCapacity);
        long key;
        int hash;
        for (int i = 0; i < oldUsed.length; i++)
        {
            if (oldUsed[i])
            {
                key = oldKeys[i];
                hash = hash(key) % capacity;
                while (used[hash])
                {
                    hash = (hash + step) % capacity;
                }
                used[hash] = true;
                keys[hash] = key;
            }
        }
        size = oldSize;
    }
}
