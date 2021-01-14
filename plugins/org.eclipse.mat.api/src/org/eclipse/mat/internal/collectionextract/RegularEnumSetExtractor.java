/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation
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
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class RegularEnumSetExtractor extends FieldSizeArrayCollectionExtractor implements IMapExtractor
{

    /**
     * For java.util.RegularEnumSet
     * @param sizeField - holds the bits indicating which set items are used
     * @param arrayField - all the enum values
     */
    public RegularEnumSetExtractor(String sizeField, String arrayField)
    {
        super(sizeField, arrayField);
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        Object o = coll.resolveValue(sizeField);
        if (o instanceof Number)
        {
            return Long.bitCount(((Number)o).longValue());
        }
        return null;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        Object o = coll.resolveValue(sizeField);
        long used = 0;
        if (o instanceof Number)
        {
            used = ((Number)o).longValue();
        }

        ArrayInt arr = new ArrayInt();
        long referenceArray[] = extractEntries(coll).getReferenceArray();
        ISnapshot snapshot = coll.getSnapshot();
        for (int i = 0; i < referenceArray.length && used != 0; i++)
        {
            if (referenceArray[i] != 0 && (used & 1) != 0)
                arr.add(snapshot.mapAddressToId(referenceArray[i]));
        }
        return arr.toArray();
    }

    public boolean hasCollisionRatio()
    {
        return true;
    }

    public Double getCollisionRatio(IObject collection) throws SnapshotException
    {
        return 0.0;
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject collection) throws SnapshotException
    {
        final ISnapshot snapshot = collection.getSnapshot();
        final int[] ids = extractEntryIds(collection);
        return new Iterator<Entry<IObject, IObject>>() {
            int ix = 0;
            public boolean hasNext()
            {
                return ix < ids.length;
            }

            public Entry<IObject, IObject> next()
            {
                final IObject o;
                try
                {
                    o = snapshot.getObject(ids[ix++]);
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
                return new Entry<IObject, IObject>() {

                    public IObject getKey()
                    {
                        return o;
                    }

                    public IObject getValue()
                    {
                        return o;
                    }

                    public IObject setValue(IObject value)
                    {
                        throw new UnsupportedOperationException();
                    }

                };
            }
        };
    }
}
