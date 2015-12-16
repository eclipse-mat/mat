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
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

//FIXME: should this really implement MapExtractor?
// the collection tests use it.
public class HashSetCollectionExtractor implements IMapExtractor
{
    private final String array_attribute;
    private final String key_attribute;
    private final String size_attribute; // can be null
    private final String value_attribute; // can be null

    public HashSetCollectionExtractor(String array_attribute, String key_attribute)
    {
        this(null, array_attribute, key_attribute, null);
    }

    public HashSetCollectionExtractor(String size_attribute, String array_attribute, String key_attribute,
                    String value_attribute)
    {
        if (array_attribute == null)
            throw new IllegalArgumentException();
        if (key_attribute == null)
            throw new IllegalArgumentException();
        this.size_attribute = size_attribute;
        this.array_attribute = array_attribute;
        this.key_attribute = key_attribute;
        this.value_attribute = value_attribute;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        if (size_attribute != null)
        {
            return ExtractionUtils.toInteger(coll.resolveValue(size_attribute));
        }
        else
        {
            return getNumberOfNotNullElements(coll);
        }
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return createHashMapExtractor().getCapacity(coll);
    }

    public boolean hasFillRatio()
    {
        return createHashMapExtractor().hasFillRatio();
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return createHashMapExtractor().getFillRatio(coll);
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        ArrayInt ret = new ArrayInt();

        int[] entries = createHashMapExtractor().extractEntryIds(coll);

        if (key_attribute.endsWith("[]")) //$NON-NLS-1$
        {
            // FIXME: what if there are two []s ?
            String attr = key_attribute.replaceFirst("\\[\\]$", ""); //$NON-NLS-1$//$NON-NLS-2$
            for (int entryId : entries)
            {
                IInstance entry = (IInstance) snapshot.getObject(entryId);
                Object f = entry.resolveValue(attr);
                if (f instanceof IObjectArray)
                {
                    IObjectArray valarr = (IObjectArray) f;
                    int n = valarr.getLength();
                    int s = 10;
                    for (int i = 0; i < n; i += s)
                    {
                        s = Math.min(s, n - i);
                        long b[] = valarr.getReferenceArray(i, s);
                        for (int j = 0; j < s; ++j)
                        {
                            if (b[j] != 0)
                            {
                                int valueId = snapshot.mapAddressToId(b[j]);
                                ret.add(valueId);
                            }
                        }
                    }
                }
            }
        }
        else
        {
            for (int entryId : entries)
            {
                IInstance entry = (IInstance) snapshot.getObject(entryId);
                Object f = entry.resolveValue(key_attribute);
                if (f instanceof IObject)
                {
                    ret.add(((IObject) f).getObjectId());
                }
            }
        }
        return ret.toArray();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return createHashMapExtractor().getNumberOfNotNullElements(coll);
    }

    private IMapExtractor createHashMapExtractor()
    {
        return new HashMapCollectionExtractor(size_attribute, array_attribute, key_attribute, value_attribute);
    }

    public boolean hasCollisionRatio()
    {
        return createHashMapExtractor().hasCollisionRatio();
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return createHashMapExtractor().getCollisionRatio(coll);
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException
    {
        return createHashMapExtractor().extractMapEntries(coll);
    }
}
