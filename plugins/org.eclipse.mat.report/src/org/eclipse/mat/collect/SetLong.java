/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash] == key) { return false; }
            hash = step(hash);
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
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                used[hash] = false;
                size--;
                // Re-hash all follow-up entries anew; Do not fiddle with the
                // capacity limit (75 %) otherwise this code may loop forever
                hash = step(hash);
                while (used[hash])
                {
                    key = keys[hash];
                    used[hash] = false;
                    int newHash = hash(key);
                    while (used[newHash])
                    {
                        newHash = step(newHash);
                    }
                    used[newHash] = true;
                    keys[newHash] = key;
                    hash = step(hash);
                }
                return true;
            }
            hash = step(hash);
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
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash] == key) { return true; }
            hash = step(hash);
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

    private int oldHash(long key)
    {
        return (((int) key) & Integer.MAX_VALUE) % capacity;
    }

    private int step(int hash)
    {
        hash += step;
        // Allow for overflow
        if (hash >= capacity || hash < 0)
            hash -= capacity;
        return hash;
    }

    /**
     * Hash function.
     * Constant is phi and should be odd.
     * Capacity is positive, so 31 bits, so we carefully
     * shift down and expand up to 64 bits before extracting
     * the result.
     * @param key
     * @return
     */
    private int hash(long key)
    {
        int r = (int)(((key * 0x9e3779b97f4a7c15L >>> 31) * capacity) >>> 33);
        return r;
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
                hash = hash(key);
                while (used[hash])
                {
                    hash = step(hash);
                }
                used[hash] = true;
                keys[hash] = key;
            }
        }
        size = oldSize;
    }

    /**
     * Calculate a suitable initial capacity
     * @return initial capacity which has the same capacity, step 
     */
    private int calcInit()
    {
        // calculate initial capacity for this capacity and step
        int c2 = capacity - 1;
        int c1c = PrimeFinder.findPrevPrime(capacity);
        int c1s = (step + 1) * 3;
        int c1 = Math.max(c1c, c1s);
        for (int c = c1; c <= c2; ++c)
        {
            int s1 = Math.max(1, PrimeFinder.findPrevPrime(c / 3));
            if (s1 == step)
                return c;
        }
        return c2;
    }

    /**
     * Return a serializable version of this SetLong.
     * Previous versions of the code use an old hash function
     * and deserialize the fields directly, so we must map
     * the values to the correct position for the old hash function.
     * @return a old-style SetInt only suitable for serialization
     */
    private Object writeReplace() {
        SetLong out = new SetLong(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                long key = keys[i];
                int hash = out.oldHash(key);
                while (out.used[hash])
                {
                    hash = out.step(hash);
                }
                out.used[hash] = true;
                out.keys[hash] = key;
                ++out.size;
            }
        }
        return out;
    }

    /**
     * Deserialize a SetLong.
     * Previous versions serialized the object directly using
     * an old hash function, and the current version does
     * the same for compatibility, 
     * so rebuild allowing a new hash function.
     * @return a populated SetLong, ready to use.
     */
    private Object readResolve()
    {
        SetLong out = new SetLong(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                out.add(keys[i]);
            }
        }
        return out;
    }
}
