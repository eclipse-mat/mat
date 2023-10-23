/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation  - initial API and implementation
 *    James Livingston - expose collection utils as API
 *    IBM Corporation/Andrew Johnson  - add size field, cope with wrap-around
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class IBM6ArrayListCollectionExtractor extends FieldArrayCollectionExtractor
{
    private String firstIndex;
    private String lastIndex;
    private String sizeField;

    public IBM6ArrayListCollectionExtractor(String firstIndex, String lastIndex, String arrayField)
    {
        this(firstIndex, lastIndex, arrayField, null);
    }
    public IBM6ArrayListCollectionExtractor(String firstIndex, String lastIndex, String arrayField, String sizeField)
    {
        super(arrayField);
        if (firstIndex == null)
            throw new IllegalArgumentException();
        if (lastIndex == null)
            throw new IllegalArgumentException();
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.sizeField = sizeField;
    }

    @Override
    public boolean hasSize()
    {
        return true;
    }

    @Override
    public Integer getSize(IObject coll) throws SnapshotException
    {
        if (sizeField != null)
        {
            Integer size = (Integer) coll.resolveValue(this.sizeField);
            if (size != null)
            {
                return size;
            }
        }
        Integer firstIndex = (Integer) coll.resolveValue(this.firstIndex);
        Integer lastIndex = (Integer) coll.resolveValue(this.lastIndex);

        if (lastIndex == null)
        {
            if (firstIndex == null)
            {
                IObjectArray arr = extractEntries(coll);
                if (arr == null)
                    return null;
                return ExtractionUtils.getNumberOfNotNullArrayElements(arr);
            }
            return null;
        }
        else if (firstIndex == null || lastIndex <= 0)
            return lastIndex;
        else
        {
            if (lastIndex >= firstIndex)
            {
                return lastIndex - firstIndex;
            }
            else
            {
                // Queue wraps
                return lastIndex - firstIndex + getCapacity(coll);
            }
        }
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        long referenceArray[] = extractEntries(coll).getReferenceArray();
        Integer size = getSize(coll);
        if (size == null)
        {
            // PHD?
            return ExtractionUtils.referenceArrayToIds(snapshot, referenceArray);
        }
        ArrayInt arr = new ArrayInt(size);
        if (size > 0)
        {
            Object firstIdx = coll.resolveValue(this.firstIndex);
            Object lastIdx = coll.resolveValue(this.lastIndex);
            if (!((firstIdx instanceof Integer) && (lastIdx instanceof Integer)))
            {
                // PHD?
                return ExtractionUtils.referenceArrayToIds(snapshot, referenceArray);
            }
            // For ArrayBlockingQueue, first == last & size > 0 means do everything
            // For ArrayList, first == last means size == 0, but wouldn't get here
            int firstIndex = (Integer) firstIdx;
            int lastIndex = (Integer) lastIdx;
            int end = getCapacity(coll);
            for (int i = firstIndex; i < (firstIndex < lastIndex  ? lastIndex : end); i++)
            {
                if (referenceArray[i] != 0)
                    arr.add(snapshot.mapAddressToId(referenceArray[i]));
            }
            for (int i = 0; i < (firstIndex < lastIndex ? 0 : lastIndex); i++)
            {
                if (referenceArray[i] != 0)
                    arr.add(snapshot.mapAddressToId(referenceArray[i]));
            }
        }
        return arr.toArray();

    }
}
