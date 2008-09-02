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
import java.util.NoSuchElementException;

public final class SetLong implements Serializable
{
    private static final long serialVersionUID = 1L;

    private int capacity;
    private int step;
    private int limit;
    private int size;
    private boolean[] used;
    private long[] keys;

    public SetLong()
    {
        this(10);
    }

    public SetLong(int initialCapacity)
    {
        init(initialCapacity);
    }

    public boolean add(long key)
    {
        if (size == limit)
        {
            resize(capacity << 1);
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
