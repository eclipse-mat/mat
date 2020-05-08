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
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * Info for IdentityHashMaps These are stored as key/value pairs in an array
 */
// FIXME: is this really a map?
public class IdentityHashMapCollectionExtractor extends FieldSizeArrayCollectionExtractor implements IMapExtractor
{
    public IdentityHashMapCollectionExtractor(String sizeField, String arrayField)
    {
        super(sizeField, arrayField);
    }

    /**
     * Gets the capacity of the map. Needs two array elements for each entry.
     */
    @Override
    public Integer getCapacity(IObject collection) throws SnapshotException
    {
        Integer capacity = super.getCapacity(collection);
        return (capacity != null) ? capacity / 2 : null;
    }

    @Override
    public boolean hasExtractableArray()
    {
        // there is an array, but it's not one entry per item
        return false;
    }

    @Override
    public boolean hasExtractableContents()
    {
        return true;
    }


    @Override
    public Integer getSize(IObject coll) throws SnapshotException
    {
        // fast path, check the size field
        Integer value = ExtractionUtils.toInteger(coll.resolveValue(sizeField));
        if (value != null)
        {
            return value;
        }
        else
        {
            return ExtractionUtils.getNumberOfNotNullArrayElements(extractEntryIds(coll));
        }
    }

    public int[] extractEntryIds(IObject idMap) throws SnapshotException
    {
        ISnapshot snapshot = idMap.getSnapshot();
        IObjectArray array = super.extractEntries(idMap);
        if (array == null)
            return new int[0];

        ArrayInt result = new ArrayInt();
        for (int i = 0; i < array.getLength(); i += 2)
        {
            long l[] = array.getReferenceArray(i, 2);
            // Skip over empty entries
            boolean empty = true;
            for (int j = 0; j < l.length; ++j)
            {
                if (l[j] != 0)
                {
                    empty = false;
                    break;
                }
            }
            if (empty)
                continue;
            for (int j = 0; j < l.length; ++j)
            {
                int objId;
                if (l[j] != 0)
                {
                    objId = snapshot.mapAddressToId(l[j]);
                }
                else
                {
                    objId = -1;
                }
                result.add(objId);
            }
        }
        return result.toArray();
    }

    /**
     * Can't calculate the collision ratio as we don't have the identityHashCode for the keys,
     * even if we did know the hash algorithm.
     */
    public boolean hasCollisionRatio()
    {
        return true;
    }

    /**
     * Complete guess as to collision ratio.
     * Look for consecutive entries - perhaps they were caused
     * by a linear probe.
     */
    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        IObjectArray array = super.extractEntries(coll);
        if (array == null)
            return 0.0;

        int consec = 0;
        int count = 0;
        boolean first = false;
        boolean prev = false;
        for (int i = 0; i < array.getLength(); i += 2)
        {
            long l[] = array.getReferenceArray(i, 2);
            // Skip over empty entries
            boolean empty = true;
            for (int j = 0; j < l.length; ++j)
            {
                if (l[j] != 0)
                {
                    empty = false;
                    break;
                }
            }
            if (empty)
            {
                prev = false;
                continue;
            }
            ++count;
            if (i == 0)
                first = true;
            if (prev)
                ++consec;
            prev = true;
        }
        if (first && prev)
            ++consec;
        // Ad-hoc calculation
        if (count == 0)
            return 0.0;
        return 0.5*(double)consec / count;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return super.getFillRatio(coll);
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        // Both key and value are stored in the array, only want to count 1
        return super.getNumberOfNotNullElements(coll) / 2;
    }
    
    public Iterator<Map.Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException
    {
        try
        {
            return new EntryIterator(coll.getSnapshot(), extractEntryIds(coll));
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    private class EntryIterator implements Iterator<Map.Entry<IObject, IObject>>
    {
        private final ISnapshot snapshot;
        private final int[] ids;
        private int idx;

        public EntryIterator(ISnapshot snapshot, int[] ids)
        {
            this.snapshot = snapshot;
            this.ids = ids;
            this.idx = 0;
        }

        public boolean hasNext()
        {
            return idx < ids.length;
        }

        public Map.Entry<IObject, IObject> next()
        {
            final int oidx = this.idx;
            this.idx += 2;
            return new Map.Entry<IObject, IObject>()
            {
                public IObject getKey()
                {
                    try
                    {
                        return snapshot.getObject(ids[oidx]);
                    }
                    catch (SnapshotException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                public IObject getValue()
                {
                    try
                    {
                        return snapshot.getObject(ids[oidx + 1]);
                    }
                    catch (SnapshotException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                public IObject setValue(IObject value)
                {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
