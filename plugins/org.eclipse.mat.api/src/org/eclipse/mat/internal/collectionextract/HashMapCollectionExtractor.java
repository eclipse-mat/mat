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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.MessageUtil;

public class HashMapCollectionExtractor extends HashedMapCollectionExtractorBase
{
    protected final String sizeField;

    public HashMapCollectionExtractor(String sizeField, String arrayField, String keyField, String valueField)
    {
        super(arrayField, keyField, valueField);
        this.sizeField = sizeField;
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public boolean hasExtractableArray()
    {
        // true for CHM segments?
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        return null;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        ArrayInt entries = new ArrayInt();

        final IObjectArray table = getBackingArray(coll);
        if (table != null)
        {
            long[] addresses = table.getReferenceArray();
            for (int i = 0; i < addresses.length; i++)
            {
                if (addresses[i] != 0)
                {
                    int id = snapshot.mapAddressToId(addresses[i]);
                    collectEntriesFromTable(entries, coll.getObjectId(), id, snapshot);
                }
            }
        }

        return entries.toArray();
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IObjectArray table = getBackingArray(coll);
        if (table != null)
        {
            return table.getLength();
        }
        else
        {
            return null;
        }
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        // fast path
        Object value = ExtractionUtils.toInteger(coll.resolveValue(sizeField));
        if (value != null)
            return ((Number) value).intValue();

        if (hasExtractableContents())
        {
            return getMapSize(coll, extractEntryIds(coll));
        }
        else
        {
            // LinkedList
            IObject header = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(arrayField, coll);
            if (header != null)
            {
                ISnapshot snapshot = coll.getSnapshot();
                return getMapSize(coll, snapshot.getOutboundReferentIds(header.getObjectId()));
            }
            else
            {
                return null;
            }
        }
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        IObjectArray arrayObject = extractBackingArray(collection);
        if (arrayObject == null)
            return 0;
        return ExtractionUtils.getNumberOfNotNullArrayElements(arrayObject);
    }

    public IObjectArray getBackingArray(IObject coll) throws SnapshotException
    {
        if (arrayField == null)
            return null;
        final Object obj = coll.resolveValue(arrayField);
        IObjectArray ret = null;
        if (obj instanceof IObjectArray)
        {
            ret = (IObjectArray) obj;
            return ret;
        }
        else if (obj instanceof IObject)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), ((IObject) obj).getTechnicalName());
            throw new SnapshotException(msg);
        }
        else if (obj != null)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), obj.toString());
            throw new SnapshotException(msg);
        }
        IObject next = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(arrayField, coll);
        if (next == null)
            return null;
        // Look for the only object array field
        final ISnapshot snapshot = next.getSnapshot();
        for (int i : snapshot.getOutboundReferentIds(next.getObjectId()))
        {
            if (snapshot.isArray(i))
            {
                IObject o = snapshot.getObject(i);
                if (o instanceof IObjectArray)
                {
                    // Have we already found a possible return type?
                    // If so, things are uncertain and so give up.
                    if (ret != null)
                        return null;
                    ret = (IObjectArray) o;
                }
            }
        }
        return ret;
    }
}
