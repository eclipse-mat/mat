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
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ConcurrentSkipListCollectionExtractor extends HashedMapCollectionExtractorBase
{
    public ConcurrentSkipListCollectionExtractor(String arrayField, String keyField, String valueField)
    {
        super(arrayField, keyField, valueField);
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        return null;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ArrayInt entries = new ArrayInt();
        ISnapshot snapshot = coll.getSnapshot();
        for (int outbound: snapshot.getOutboundReferentIds(getTableId(coll)))
            collectEntriesFromTable(entries, coll.getObjectId(), outbound, snapshot);
        return entries.toArray();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return extractEntryIds(coll).length;
    }

    @Override
    public boolean hasCapacity()
    {
        return false;
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return null;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return getMapSize(coll, extractEntryIds(coll));
    }

    @Override
    public boolean hasFillRatio()
    {
        return false;
    }

    @Override
    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return null;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return 0.0;
    }

    private int getTableId(IObject coll) throws SnapshotException
    {
        // strip the trailing period back off
        IObject arr = (IObject) coll.resolveValue(arrayField);
        return arr.getObjectId();
    }
}
