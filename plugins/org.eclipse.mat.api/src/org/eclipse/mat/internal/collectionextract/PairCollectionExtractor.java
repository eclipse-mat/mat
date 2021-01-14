/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation/Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class PairCollectionExtractor implements ICollectionExtractor
{
    final String field1;
    final String field2;

    public PairCollectionExtractor(String field1, String field2)
    {
        this.field1 = field1;
        this.field2 = field2;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return 2;
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return 2;
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        int id1 = ((IObject) coll.resolveValue(field1)).getObjectId();
        int id2= ((IObject) coll.resolveValue(field2)).getObjectId();
        return new int[] { id1, id2 };
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
        return ((coll.resolveValue(field1) != null) ? 1 : 0) +
               ((coll.resolveValue(field2) != null) ? 1 : 0);
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return getNumberOfNotNullElements(coll).doubleValue() / 2.0;
    }
}
