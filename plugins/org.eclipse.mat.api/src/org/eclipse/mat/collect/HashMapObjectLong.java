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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * null not allowed as key.
 */
public final class HashMapObjectLong<E> implements Serializable
{
    private static final NoSuchElementException noSuchElementException = new NoSuchElementException(
    "This is static exception, there is no stack trace available. It is thrown by get() method.");

    public interface Entry<E>
    {
        E getKey();

        long getValue();
    }

    private static final long serialVersionUID = 1L;

    private int capacity;
    private int step;
    private int limit;
    private int size;
    private boolean[] used;
    private Object[] keys;
    private long[] values;

    public HashMapObjectLong()
    {
        this(10);
    }

    public HashMapObjectLong(int initialCapacity)
    {
        init(initialCapacity);
    }

    public boolean put(E key, long value)
    {
        if (size == limit)
            resize(capacity << 1);

        int hash = key.hashCode() % capacity;
        while (used[hash])
        {
            if (keys[hash].equals(key))
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

    public boolean remove(E key)
    {
        Object keyObj = key;
        
        int hash = keyObj.hashCode() % capacity;
        while (used[hash])
        {
            if (keys[hash] == keyObj)
            {
                used[hash] = false;
                size--;
                // Re-hash all follow-up entries anew; Do not fiddle with the
                // capacity limit (75 %) otherwise this code may loop forever
                hash = (hash + step) % capacity;
                while (used[hash])
                {
                    keyObj = keys[hash];
                    used[hash] = false;
                    int newHash = keyObj.hashCode() % capacity;
                    while (used[newHash])
                        newHash = (newHash + step) % capacity;

                    used[newHash] = true;
                    keys[newHash] = keyObj;
                    values[newHash] = values[hash];
                    hash = (hash + step) % capacity;
                }
                return true;
            }
            hash = (hash + step) % capacity;
        }

        return false;
    }

    public boolean containsKey(E key)
    {
        int hash = key.hashCode() % capacity;
        while (used[hash])
        {
            if (keys[hash].equals(key)) { return true; }
            hash = (hash + step) % capacity;
        }
        return false;
    }

    public long get(E key)
    {
        int hash = key.hashCode() % capacity;
        while (used[hash])
        {
            if (keys[hash].equals(key)) { return values[hash]; }
            hash = (hash + step) % capacity;
        }

        throw noSuchElementException;
    }

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

    public int size()
    {
        return size;
    }

    public boolean isEmpty()
    {
        return size() == 0;
    }

    public void clear()
    {
        size = 0;
        used = new boolean[capacity];
    }

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
                        return (E)keys[i];
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
                                return (E)keys[i];
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
        step = PrimeFinder.findPrevPrime(initialCapacity);
        limit = (int) ((float) capacity * 0.75);
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
                hash = key.hashCode() % capacity;
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
