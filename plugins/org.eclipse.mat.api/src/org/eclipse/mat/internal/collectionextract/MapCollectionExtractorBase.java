/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public abstract class MapCollectionExtractorBase implements IMapExtractor
{
    protected final String sizeField;
    protected final String keyField;
    protected final String valueField;

    public MapCollectionExtractorBase(String sizeField, String keyField, String valueField)
    {
        this.sizeField = sizeField;
        this.keyField = keyField;
        this.valueField = valueField;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        if (sizeField == null)
            return null;

        Object value = coll.resolveValue(sizeField);
        // Allow for int or long
        if (value instanceof Integer)
        {
            return (Integer) value;
        }
        else if (value instanceof Long)
        {
            return ((Long) value).intValue();
        }
        else
        {
            return null;
        }
    }

    public boolean hasCapacity()
    {
        return hasExtractableArray();
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IObjectArray table = extractEntries(coll);
        return (table == null) ? null : table.getLength();
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll)
    {
        try
        {
            return new MapEntryIterator(coll.getSnapshot(), extractEntryIds(coll));
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    private class MapEntryIterator implements Iterator<Entry<IObject, IObject>>
    {
        private final ISnapshot snapshot;
        private final int[] ids;
        private int idx;

        public MapEntryIterator(ISnapshot snapshot, int[] ids)
        {
            this.snapshot = snapshot;
            this.ids = ids;
            this.idx = 0;
        }

        public boolean hasNext()
        {
            return idx < ids.length;
        }

        public Entry<IObject, IObject> next()
        {
            try
            {
                IObject obj = snapshot.getObject(ids[idx++]);
                return new EntryObject(obj, getEntryKey(obj), getEntryValue(obj));
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    protected IObject getEntryKey(IObject obj)
    {
        try
        {
            IObject ret = (IObject) obj.resolveValue(keyField);
            if (ret != null)
            {
                return ret;
            }
            else
            {
                return null;
            }
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected IObject getEntryValue(IObject obj)
    {
        try
        {
            return (IObject) obj.resolveValue(valueField);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }
}
