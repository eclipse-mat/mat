/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class TreeMapArrayCollectionExtractor extends TreeMapCollectionExtractor
{
    public TreeMapArrayCollectionExtractor(String sizeField, String keyField, String valueField)
    {
        super(sizeField, keyField, valueField);
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll)
    {
        // The entries are arrays - the keys and values are in the arrays
        return new MapArrayEntryIterator(coll.getSnapshot(), coll, super.extractMapEntries(coll));
    }

    private class MapArrayEntryIterator implements Iterator<Entry<IObject, IObject>>
    {
        ISnapshot snapshot;
        private IObject coll;
        Iterator<Entry<IObject, IObject>> it;
        IObjectArray keys;
        IObjectArray values;
        int i = 0;
        int len = 0;
        EntryObject saved = null;

        MapArrayEntryIterator(ISnapshot snapshot, IObject coll, Iterator<Entry<IObject, IObject>> it)
        {
            this.it = it;
            this.snapshot = snapshot;
            this.coll = coll;
        }

        public boolean hasNext()
        {
            if (saved != null) { return true; }
            saved = getNext(true);
            return saved != null;
        }

        public Entry<IObject, IObject> next()
        {
            EntryObject ret = saved;
            if (ret != null)
            {
                saved = null;
                return ret;
            }
            ret = getNext(false);
            return ret;
        }

        private EntryObject getNext(boolean test)
        {
            EntryObject ret = null;
            while (true)
            {
                while (i < len)
                {
                    try
                    {
                        long keyAddress = keys.getReferenceArray(i, 1)[0];
                        long valueAddress = values.getReferenceArray(i, 1)[0];
                        ++i;
                        if (keyAddress != 0 && valueAddress != 0)
                        {
                            ret = new EntryObject(coll, snapshot.getObject(snapshot.mapAddressToId(keyAddress)),
                                            snapshot.getObject(snapshot.mapAddressToId(valueAddress)));
                            return ret;
                        }
                    }
                    catch (SnapshotException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                if (test && !it.hasNext())
                    return null;
                Entry<IObject, IObject> e = it.next();
                keys = (IObjectArray) e.getKey();
                values = (IObjectArray) e.getValue();
                len = keys.getLength();
                i = 0;
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
