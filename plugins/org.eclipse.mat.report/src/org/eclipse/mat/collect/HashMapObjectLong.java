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
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A map from Object to long.
 * More efficient than a general map
 * null not allowed as key.
 */
public final class HashMapObjectLong<E> implements Serializable
{
    /**
     * An entry from the map
     */
    public interface Entry<E>
    {
       /**
         * Get the key.
         * @return the key
         */
        E getKey();

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
    private Object[] keys;
    private long[] values;

    /**
     * Create a map of default size
     */
    public HashMapObjectLong()
    {
        this(10);
    }

    /**
     * Create a map of given size
     * @param initialCapacity the initial capacity of the HashMap
     */
    public HashMapObjectLong(int initialCapacity)
    {
        init(initialCapacity);
    }

    /**
     * Add a mapping
     * @param key the key
     * @param value the corresponding value
     * @return true if an entry with the key already exists
     */
    public boolean put(E key, long value)
    {
        if (size == limit)
        {
            // Double in size but avoid overflow or JVM limits
            resize(capacity <= BIG_CAPACITY >> 1 ? capacity << 1 : capacity < BIG_CAPACITY ? BIG_CAPACITY : capacity + 1);
        }

        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash].equals(key))
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

    /**
     * Remove an mapping from the map
     * @param key the key to remove
     * @return true if entry was found
     */
    public boolean remove(E key)
    {
        Object keyObj = key;

        int hash = hash(keyObj);
        while (used[hash])
        {
            if (keys[hash].equals(keyObj))
            {
                used[hash] = false;
                size--;
                // Re-hash all follow-up entries anew; Do not fiddle with the
                // capacity limit (75 %) otherwise this code may loop forever
                hash = step(hash);
                while (used[hash])
                {
                    keyObj = keys[hash];
                    used[hash] = false;
                    int newHash = hash(keyObj);
                    while (used[newHash])
                    {
                        newHash = step(newHash);
                    }
                    used[newHash] = true;
                    keys[newHash] = keyObj;
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
    public boolean containsKey(E key)
    {
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash].equals(key)) { return true; }
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
    public long get(E key)
    {
        int hash = hash(key);
        while (used[hash])
        {
            if (keys[hash].equals(key)) { return values[hash]; }
            hash = step(hash);
        }

        throw noSuchElementException;
    }

    /**
     * Get all the used keys.
     * Consider using {@link #getAllKeys(Object[])} for better type safety
     * @return an array of the used keys
     */
    public Object[] getAllKeys()
    {
        Object[] array = new Object[size];
        int j = 0;
        for (int i = 0; i < used.length; i++)
        {
            if (used[i])
                array[j++] = keys[i];
        }
        return array;
    }

    /**
     * Get all the used keys.
     * @param a an array of the right type for the output, which will be used
     * if it is big enough, otherwise another array of this type will be allocated.
     * @param <T> the type of the keys
     * @return an array of the used keys
     */
    @SuppressWarnings("unchecked")
    public <T> T[] getAllKeys(T[] a)
    {
        if (a.length < size)
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);

        int j = 0;
        for (int ii = 0; ii < used.length; ii++)
        {
            if (used[ii])
                a[j++] = (T) keys[ii];
        }

        if (a.length > size)
            a[size] = null;
        return a;
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
    public Iterator<E> keys()
    {
        return new Iterator<E>()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            @SuppressWarnings("unchecked")
            public E next() throws NoSuchElementException
            {
                while (++i < used.length)
                {
                    if (used[i])
                    {
                        n++;
                        return (E) keys[i];
                    }
                }
                throw new NoSuchElementException();
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
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
    public Iterator<Entry<E>> entries()
    {
        return new Iterator<Entry<E>>()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            public Entry<E> next() throws NoSuchElementException
            {
                while (++i < used.length)
                {
                    if (used[i])
                    {
                        n++;
                        return new Entry<E>()
                        {
                            @SuppressWarnings("unchecked")
                            public E getKey()
                            {
                                return (E) keys[i];
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
        keys = new Object[capacity];
        values = new long[capacity];
    }

    private void resize(int newCapacity)
    {
        int oldSize = size;
        boolean[] oldUsed = used;
        Object[] oldKeys = keys;
        long[] oldValues = values;
        init(newCapacity);
        Object key;
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
                values[hash] = oldValues[i];
            }
        }
        size = oldSize;
    }

    private int step(int hash)
    {
        hash += step;
        // Allow for overflow
        if (hash >= capacity || hash < 0)
            hash -= capacity;
        return hash;
    }
    private int oldHash(Object obj)
    {
        // Math.abs isn't safe for Integer.MIN_VALUE as it returns Integer.MIN_VALUE 
        return (obj.hashCode() & Integer.MAX_VALUE) % capacity;
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
    private int hash(Object key)
    {
        int r = (int)(((key.hashCode() * 0x9e3779b97f4a7c15L >>> 31) * capacity) >>> 33);
        return r;
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
     * @return a old-style HashMapObjectLong only suitable for serialization
     */
    private Object writeReplace() {
        HashMapObjectLong<E> out = new HashMapObjectLong<E>(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                Object key = keys[i];
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
    @SuppressWarnings("unchecked")
    private Object readResolve()
    {
        HashMapObjectLong<E> out = new HashMapObjectLong<E>(calcInit());
        for (int i = 0; i < capacity; ++i)
        {
            if (used[i])
            {
                out.put((E)keys[i], values[i]);
            }
        }
        return out;
    }
}
