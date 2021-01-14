/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class SingletonMapExtractor implements IMapExtractor
{
    private final String keyField;
    private final String valueField;

    public SingletonMapExtractor(String keyField, String valueField)
    {
        this.keyField = keyField;
        this.valueField = valueField;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return 1;
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return 1;
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return new int[] { coll.getObjectId() };
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return (coll.resolveValue(valueField) != null) ? 1 : 0;
    }

    public boolean hasCollisionRatio()
    {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return 0d;
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException
    {
        return Collections.singleton((Entry<IObject, IObject>)(new EntryObject(coll, (IObject) coll.resolveValue(keyField), (IObject) coll.resolveValue(valueField))))
                                   .iterator();
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return getNumberOfNotNullElements(coll).doubleValue();
    }
}
