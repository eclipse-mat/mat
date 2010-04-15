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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A map from int to Object.
 * More efficient than a general map
 */
public final class HashMapIntObject<E> implements Serializable
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
        int getKey();

        /**
         * Get the corresponding value.
         * @return the value
         */
        E getValue();
    }

    private static final long serialVersionUID = 2L;

    private int capacity;
    private int step;
    private int limit;
    private int size;
    private transient boolean[] used;
    private transient int[] keys;
    private transient E[] values;

    /**
     * Create a map of default size
     */
    public HashMapIntObject()
    {
        this(10);
    }

    /**
     * Create a map of given capacity
     * @param initialCapacity - can grow beyond this
     */
    public HashMapIntObject(int initialCapacity)
    {
        init(initialCapacity);
    }

    /**
     * Add a mapping
     * @param key the key
     * @param value the corresponding value
     * @return the old value if an entry with the key already exists
     */
    public E put(int key, E value)
    {
        if (size == limit)
            resize(capacity << 1);

        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                E oldValue = values[hash];
                values[hash] = value;
                return oldValue;
            }
            hash = (hash + step) % capacity;
        }
        used[hash] = true;
        keys[hash] = key;
        values[hash] = value;
        size++;
        return null;
    }

    /**
     * Remove an mapping from the map
     * @param key the key to remove
     * @return the old value if the key was found, otherwise null
     */
    public E remove(int key)
    {
        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                E oldValue = values[hash];
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
                return oldValue;
            }
            hash = (hash + step) % capacity;
        }
        return null;
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
     * @return the value, or null if the key is not found
     */
    public E get(int key)
    {
        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key) { return values[hash]; }
            hash = (hash + step) % capacity;
        }
        return null;
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
     * Get all the values corresponding to the used keys.
     * Duplicate values are possible if they correspond to different keys.
     * Consider using {@link #getAllValues(T[] a)} for better type safety.
     * @return an array of the used values
     */
    public Object[] getAllValues()
    {
        Object[] array = new Object[size];
        int index = 0;
        for (int ii = 0; ii < used.length; ii++)
        {
            if (used[ii])
                array[index++] = values[ii];
        }
        return array;
    }

    /**
     * Get all the values corresponding to the used keys.
     * Duplicate values are possible if they correspond to different keys.
     * @param a an array of the right type for the output, which will be used
       if it is big enough, otherwise another array of this type will be allocated.
     * @return an array of the used values
     */
    @SuppressWarnings("unchecked")
    public <T> T[] getAllValues(T[] a)
    {
        if (a.length < size)
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);

        int index = 0;
        for (int ii = 0; ii < used.length; ii++)
        {
            if (used[ii])
                a[index++] = (T) values[ii];
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
    public Iterator<E> values()
    {
        return new Iterator<E>()
        {
            int n = 0;
            int i = -1;

            public boolean hasNext()
            {
                return n < size;
            }

            public E next() throws NoSuchElementException
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

            public void remove() throws UnsupportedOperationException
            {
                throw new UnsupportedOperationException();
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
                            public int getKey()
                            {
                                return keys[i];
                            }

                            public E getValue()
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

    @SuppressWarnings("unchecked")
    private void init(int initialCapacity)
    {
        capacity = PrimeFinder.findNextPrime(initialCapacity);
        step = Math.max(1, PrimeFinder.findPrevPrime(initialCapacity / 3));
        limit = (int) (capacity * 0.75);
        clear();
        keys = new int[capacity];
        // This cast is ok as long as nobody assigns the field values to a field
        // of type <E>[] (values is of type Object[] and an assignment would
        // lead to a ClassCastException). This cast here is performed to extract
        // the array elements later without additional casts in the other calls.
        values = (E[]) new Object[capacity];
    }

    private void resize(int newCapacity)
    {
        int oldSize = size;
        boolean[] oldUsed = used;
        int[] oldKeys = keys;
        E[] oldValues = values;
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

    private void writeObject(ObjectOutputStream stream) throws IOException
    {
        stream.defaultWriteObject();
        for (int ii = 0; ii < used.length; ii++)
        {
            if (used[ii])
            {
                stream.writeInt(keys[ii]);
                stream.writeObject(values[ii]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();

        // compat: serialized maps could contain old step factor
        step = Math.max(1, PrimeFinder.findPrevPrime(capacity / 3));

        used = new boolean[capacity];
        keys = new int[capacity];
        values = (E[]) new Object[capacity];

        for (int ii = 0; ii < size; ii++)
            putQuick(stream.readInt(), (E) stream.readObject());
    }

    private void putQuick(int key, E value)
    {
        int hash = (key & Integer.MAX_VALUE) % capacity;
        while (used[hash])
        {
            if (keys[hash] == key)
            {
                values[hash] = value;
                return;
            }
            hash = (hash + step) % capacity;
        }
        used[hash] = true;
        keys[hash] = key;
        values[hash] = value;
    }
}
