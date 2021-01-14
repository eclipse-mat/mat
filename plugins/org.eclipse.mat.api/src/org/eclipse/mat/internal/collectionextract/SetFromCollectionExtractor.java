/*******************************************************************************
 * Copyright (c) 2020 SAP AG, IBM Corporation and others
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
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * Give a set-like extractor from a collection extractor
 */
public class SetFromCollectionExtractor implements IMapExtractor
{
    ICollectionExtractor ex;
    final Double collisionRatio;
    public SetFromCollectionExtractor(ICollectionExtractor ex)
    {
        this.ex = ex;
        this.collisionRatio = null;
    }

    public SetFromCollectionExtractor(ICollectionExtractor ex, double collisionRatio)
    {
        this.ex = ex;
        this.collisionRatio = collisionRatio;
    }

    public boolean hasCollisionRatio()
    {
        return collisionRatio != null;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return collisionRatio;
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject collection) throws SnapshotException
    {
        final int objs[] = extractEntryIds(collection);
        final ISnapshot snapshot = collection.getSnapshot();
        return new Iterator<Entry<IObject, IObject>>() {
            int i;
            public boolean hasNext()
            {
                return i < objs.length;
            }

            public Entry<IObject, IObject> next()
            {
                if (hasNext())
                {
                    final int ii = i++;
                    final IObject value;
                    /*
                     * Could delay getting the value until getKey, 
                     * but then not possible to throw an exception
                     */
                    try
                    {
                        value = snapshot.getObject(objs[ii]);
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
                            return value;
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
                else
                {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    public boolean hasSize()
    {
        return ex.hasSize();
    }

    public Integer getSize(IObject collection) throws SnapshotException
    {
        return ex.getSize(collection);
    }

    public boolean hasCapacity()
    {
        return ex.hasCapacity();
    }

    public Integer getCapacity(IObject collection) throws SnapshotException
    {
        return ex.getCapacity(collection);
    }

    public boolean hasFillRatio()
    {
        return ex.hasFillRatio();
    }

    public Double getFillRatio(IObject collection) throws SnapshotException
    {
        return ex.getFillRatio(collection);
    }

    public boolean hasExtractableContents()
    {
        return ex.hasExtractableContents();
    }

    public int[] extractEntryIds(IObject collection) throws SnapshotException
    {
        return ex.extractEntryIds(collection);
    }

    public boolean hasExtractableArray()
    {
        return ex.hasExtractableArray();
    }

    public IObjectArray extractEntries(IObject collection) throws SnapshotException
    {
        return ex.extractEntries(collection);
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        return ex.getNumberOfNotNullElements(collection);
    }

}
