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
     * @param initialCapacity
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

        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                values[hash] = value;
                return true;
            }
            hash = (hash + step) % capacity;
        }
        used[hash] = true;
        keys[hash] = key;
        values[hash] = value;
        size++;

        return false;
    }

    /**
     * Remove an mapping from the map
     * @param key the key to remove
     * @return true if entry was found
     */
    public boolean remove(int key)
    {
        int hash = (key & Integer.MAX_VALUE) % capacity;
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
                    int newHash = (key & Integer.MAX_VALUE) % capacity;
                    while (used[newHash])
                    {
                        newHash = (newHash + step) % capacity;
                    }
                    used[newHash] = true;
                    keys[newHash] = key;
                    values[newHash] = values[hash];
                    hash = (hash + step) % capacity;
                }
                return true;
            }
            hash = (hash + step) % capacity;
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
        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key) { return true; }
            hash = (hash + step) % capacity;
        }
        return false;
    }

    /**
     * Retrieve the value corresponding to the key
     * @param key the key
     * @return the value
     * @throws NosuchElementException if the key is not found
     */
    public long get(int key)
    {
        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key) { return values[hash]; }
            hash = (hash + step) % capacity;
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
                hash = (key & Integer.MAX_VALUE) % capacity;
                while (used[hash])
                {
                    hash = (hash + step) % capacity;
                }
                used[hash] = true;
                keys[hash] = key;
                values[hash] = oldValues[i];
            }
        }
        size = oldSize;
    }
}
