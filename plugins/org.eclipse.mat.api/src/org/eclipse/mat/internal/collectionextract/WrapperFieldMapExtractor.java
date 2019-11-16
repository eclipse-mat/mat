/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class WrapperFieldMapExtractor extends WrapperMapExtractor
{
    String keyfield;
    String valuefield;

    public WrapperFieldMapExtractor(String field, String keyfield, String valuefield)
    {
        this(field, keyfield, valuefield, null);
    }

    public WrapperFieldMapExtractor(String field, String keyfield, String valuefield, ICollectionExtractor extractor)
    {
        super(field, extractor);
        this.keyfield = keyfield;
        this.valuefield = valuefield;
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        int r[] = super.extractEntryIds(coll);
        ArrayInt a = new ArrayInt(r.length);
        for (int i = 0; i < r.length; ++i)
        {
            IObject o = coll.getSnapshot().getObject(r[i]);
            Object o2 = o.resolveValue(keyfield);
            if (o2 instanceof IObject)
            {
                a.add(((IObject)o2).getObjectId());
            }
        }
        return a.toArray();
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(final IObject coll)
    {
        // Wrap the returned object so the wrapper collection is the entry object
        ExtractedMap em = extractMap(coll);
        final Iterator<Entry<IObject, IObject>> it = em.iterator();
        return new Iterator<Entry<IObject, IObject>>() {

            public boolean hasNext()
            {
                return it.hasNext();
            }

            public Entry<IObject, IObject> next()
            {
                Entry<IObject, IObject> e = it.next();
                // Unwrap the keys and values
                IObject key = e.getKey();
                if (key != null)
                {
                    try
                    {
                        Object o = key.resolveValue(keyfield);
                        if (o instanceof IObject)
                            key = (IObject)o;
                        else
                            key = null;
                    }
                    catch (SnapshotException e1)
                    {
                        key = null;
                    }
                }
                IObject value = e.getValue();
                if (value != null)
                {
                    try
                    {
                        Object o = value.resolveValue(valuefield);
                        if (o instanceof IObject)
                            value = (IObject)o;
                        else
                            value = null;
                        
                    }
                    catch (SnapshotException e1)
                    {
                        value = null;
                    }
                }
                return new EntryObject(coll, key, value);
            }
        };
    }
}
