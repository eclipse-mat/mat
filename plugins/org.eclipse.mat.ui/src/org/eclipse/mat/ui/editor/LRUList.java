/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* package */class LRUList<E> implements Iterable<E>
{
    private class Entry
    {
        E value;

        Entry next;
        Entry previous;
    }

    Map<E, Entry> object2entry;
    Entry root;

    public LRUList()
    {
        object2entry = new HashMap<E, Entry>();
        root = new Entry();
        root.next = root.previous = root;
    }

    public Iterator<E> iterator()
    {
        return object2entry.keySet().iterator();
    }

    public void clear()
    {
        object2entry.clear();
        root.next = root.previous = root;
    }

    public void add(E obj)
    {
        Entry entry = new Entry();
        entry.value = obj;

        object2entry.put(obj, entry);
        doAdd(entry);
    }

    public void remove(E obj)
    {
        Entry entry = object2entry.remove(obj);
        if (entry != null)
            doRemove(entry);
    }

    public void touch(E obj)
    {
        Entry entry = object2entry.get(obj);
        if (entry != null)
        {
            doRemove(entry);
            doAdd(entry);
        }
    }

    public E peek()
    {
        return root.next.value;
    }

    @Override
    public String toString()
    {
        List<E> elements = new ArrayList<E>();

        Entry e = root;

        while (e.next.value != null)
        {
            elements.add(e.next.value);
            e = e.next;
        }

        return elements.toString();
    }

    // //////////////////////////////////////////////////////////////
    // internal
    // //////////////////////////////////////////////////////////////

    private void doAdd(Entry entry)
    {
        entry.previous = root;
        entry.next = root.next;

        root.next.previous = entry;
        root.next = entry;
    }

    private void doRemove(Entry entry)
    {
        entry.previous.next = entry.next;
        entry.next.previous = entry.previous;
        entry.next = entry.previous = null;
    }

}
