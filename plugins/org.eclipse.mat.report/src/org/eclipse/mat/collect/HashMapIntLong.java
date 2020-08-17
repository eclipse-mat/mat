/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
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
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A map from int to long.
 * More efficient than a general map
 */
public final class HashMapIntLong implements Serializable
{
    /**
     * An entry from the map
     */
    public interface Entry
    {
        /**
         * Get the key.
         * @return the key
         */
        int getKey();

        /**
         * Get the corresponding value.
         * @return the value
         */
        long getValue();
    }

    private static final NoSuchElementException noSuchElementException = new NoSuchElementException(
                    "This is static exception, there is no stack trace available. It is thrown by get() method."); //$NON-NLS-1$

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
    private int[] keys;
    private long[] values;

    /**
     * Create a map of default size
     */
    public HashMapIntLong()
    {
        this(10);
    }

    /**
     * Create a map of given size
     * @param initialCapacity in entries.
     */
    public HashMapIntLong(int initialCapacity)
    {
        init(initialCapacity);
    }

    /**
     * Add a mapping
     * @param key the key
     * @param value the corresponding value
     * @return true if an entry with the key already exists
     */
    public boolean put(int key, long value)
    {
        if (size == limit)
        {
            // Double in size but avoid overflow or JVM limits
            resize(capacity <= BIG_CAPACITY >> 1 ? capacity << 1 : capacity < BIG_CAPACITY ? BIG_CAPACITY : capacity + 1);
        }

        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                values[hash] = value;
                return true;
            }
            hash = step(hash);
        }
        used[hash] = true;
        keys[hash] = key;
        values[hash] = value;
        size++;

        return false;
    }

    private int oldHash(int key)
    {
        return (key & Integer.MAX_VALUE)  % capacity;
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
    private int hash(int key)
    {
        int r = (int)(((key * 0x9e3779b97f4a7c15L >>> 31) * capacity) >>> 33);
        return r;
    }

    /**
     * Remove an mapping from the map
     * @param key the key to remove
     * @return true if entry was found
     */
    public boolean remove(int key)
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
                    values[newHash] = values[hash];
                    hash = step(hash);
                }
                return true;
            }
            hash = step(hash);
        }

        return false;
    }

    /**
     * find if key is present in map
     * @param key the key
     * @return true if the key was found
     */
    public boolean containsKey(int key)
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
     * Retrieve the value corresponding to the key
     * @param key the key
     * @return the value
     * @throws NoSuchElementException if the key is not found
     */
    public long get(int key)
    {
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash] == key) { return values[hash]; }
            hash = step(hash);
        }

        throw noSuchElementException;
    }

    /**
     * Get all the used keys
     * @return an array of the used keys
     */
    public int[] getAllKeys()
    {
        int[] array = new int[size];
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

    /**
     * The number of mappings
     * @return the size of the map
     */
    public int size()
    {
        return size;
    }

    /**
     * Is the map empty 
     * @return true if no current mappings
     */
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * Remove all the existing mappings,
     * leaving the capacity unchanged.
     */
    public void clear()
    {
        size = 0;
        used = new boolean[capacity];
    }

    /**
     * Get a way of iterating over the keys
     * @return an iterator over the keys
     */
    public IteratorInt keys()
    {
        return new IteratorInt()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            public int next() throws NoSuchElementException
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
     * Get a way of iterating over the values.
     * @return an iterator over the values
     */
    public IteratorLong values()
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
                        return values[i];
                    }
                }
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * Iterate over all the map entries
     * @return the iterator over the entries
     */
    public Iterator<Entry> entries()
    {
        return new Iterator<Entry>()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            public Entry next() throws NoSuchElementException
            {
                while (++i < used.length)
                {
                    if (used[i])
                    {
                        n++;
                        return new Entry()
                        {
                            public int getKey()
                            {
                                return keys[i];
                            }

                            public long getValue()
                            {
                                return values[i];
                            }
                        };
                    }
                }
                throw new NoSuchElementException();
            }

            public void remove() throws UnsupportedOperationException
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Get all the values corresponding to the used keys.
     * Duplicate values are possible if they correspond to different keys.
     * @return an array of the used values
     */
    public long[] getAllValues()
    {
        long[] a = new long[size];

        int index = 0;
        for (int ii = 0; ii < values.length; ii++)
        {
            if (used[ii])
                a[index++] = values[ii];
        }

        return a;
    }

    private void init(int initialCapacity)
    {
        capacity = PrimeFinder.findNextPrime(initialCapacity);
        step = Math.max(1, PrimeFinder.findPrevPrime(initialCapacity / 3));
        limit = (int) (capacity * 0.75);
        clear();
        keys = new int[capacity];
        values = new long[capacity];
    }

    private void resize(int newCapacity)
    {
        int oldSize = size;
        boolean[] oldUsed = used;
        int[] oldKeys = keys;
        long[] oldValues = values;
        init(newCapacity);
        int key, hash;
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
                values[hash] = oldValues[i];
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
     * Return a serializable version of this HashMap.
     * Previous versions of the code use an old hash function
     * and deserialize the fields directly, so we must map
     * the values to the correct position for the old hash function.
     * @return a old-style HashMapIntLong only suitable for serialization
     */
    private Object writeReplace() {
        HashMapIntLong out = new HashMapIntLong(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                int key = keys[i];
                int hash = out.oldHash(key);
                while (out.used[hash])
                {
                    hash = out.step(hash);
                }
                out.used[hash] = true;
                out.keys[hash] = key;
                out.values[hash] = values[i];
                ++out.size;
            }
        }
        return out;
    }

    /**
     * Previous versions serialized the object directly using
     * an old hash function, and the current version does
     * the same for compatibility, 
     * so rebuild allowing a new hash function.
     * @return
     */
    private Object readResolve()
    {
        HashMapIntLong out = new HashMapIntLong(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                out.put(keys[i], values[i]);
            }
        }
        return out;
    }
}
