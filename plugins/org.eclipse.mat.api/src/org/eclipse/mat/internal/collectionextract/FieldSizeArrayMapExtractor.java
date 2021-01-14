/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class FieldSizeArrayMapExtractor extends FieldSizeArrayCollectionExtractor implements IMapExtractor
{
    FieldArrayCollectionExtractor keysExtractor;
    public FieldSizeArrayMapExtractor(String sizeField, String valuesArrayField, String keysCollectionField)
    {
        super(sizeField, valuesArrayField);
        keysExtractor = new FieldArrayCollectionExtractor(keysCollectionField);
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
        final IObjectArray valueArray = extractEntries(collection);
        final IObjectArray keyArray = keysExtractor.extractEntries(collection);
        final ISnapshot snapshot = collection.getSnapshot();
        return new Iterator<Entry<IObject, IObject>>() {
            int ix = 0;
            public boolean hasNext()
            {
                while (ix < valueArray.getLength())
                {
                    if (valueArray.getReferenceArray(ix, 1)[0] != 0)
                        return true;
                    ++ix;
                }
                return false;
            }

            public Map.Entry<IObject, IObject> next()
            {
                if (hasNext())
                {
                    final int ix2 = ix++;
                    final IObject key, value;
                    long keyaddr = keyArray.getReferenceArray(ix2, 1)[0];
                    try
                    {
                        int keyid = snapshot.mapAddressToId(keyaddr);
                        key = snapshot.getObject(keyid);
                    }
                    catch (SnapshotException e)
                    {
                        NoSuchElementException ise = new NoSuchElementException();
                        ise.initCause(e);
                        throw ise;
                    }

                    long valueaddr = valueArray.getReferenceArray(ix2, 1)[0];
                    try
                    {
                        int valueid = snapshot.mapAddressToId(valueaddr);
                        value = snapshot.getObject(valueid);
                    }
                    catch (SnapshotException e)
                    {
                        NoSuchElementException ise = new NoSuchElementException();
                        ise.initCause(e);
                        throw ise;
                    }
                    return new Map.Entry<IObject, IObject>() {

                        public IObject getKey()
                        {
                            return key;
                        }

                        public IObject getValue()
                        {
                            return value;
                        }

                        public IObject setValue(IObject value)
                        {
                            throw new UnsupportedOperationException();
                        }

                    };
                }
                throw new NoSuchElementException();
            }
            
        };
    }
}
