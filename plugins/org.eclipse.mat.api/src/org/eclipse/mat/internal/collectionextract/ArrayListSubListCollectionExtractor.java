/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation  - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class ArrayListSubListCollectionExtractor extends FieldSizeArrayCollectionExtractor
{
    private String offset;

    public ArrayListSubListCollectionExtractor(String sizeField, String arrayField, String firstIndex)
    {
        super(sizeField, arrayField);
        if (firstIndex == null)
            throw new IllegalArgumentException();
        this.offset = firstIndex;
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        long referenceArray[] = extractEntries(coll).getReferenceArray();
        Integer size = getSize(coll);
        if (size == null)
        {
            return new int[0];
        }
        ArrayInt arr = new ArrayInt(size);
        if (size > 0)
        {
            int firstIndex = (Integer) coll.resolveValue(this.offset);
            int lastIndex = firstIndex + getSize(coll);
            for (int i = firstIndex; i < lastIndex; i++)
            {
                if (referenceArray[i] != 0)
                    arr.add(snapshot.mapAddressToId(referenceArray[i]));
            }
        }
        return arr.toArray();
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return getSize(coll);
    }
}
