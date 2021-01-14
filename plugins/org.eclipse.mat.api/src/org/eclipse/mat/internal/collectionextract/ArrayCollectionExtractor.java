/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG, IBM Corporation and others
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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ArrayCollectionExtractor implements ICollectionExtractor
{
    public static final ICollectionExtractor INSTANCE = new ArrayCollectionExtractor();

    private ArrayCollectionExtractor()
    {}

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.getNumberOfNotNullArrayElements((IObjectArray) coll);
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return ((IObjectArray) coll).getLength();
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.referenceArrayToIds(coll.getSnapshot(), extractEntries(coll).getReferenceArray());
    }

    public boolean hasExtractableArray()
    {
        return true;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        return (IObjectArray) coll;
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.getNumberOfNotNullArrayElements(extractEntries(coll));
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        double numberOfNotNullElements = (double) getNumberOfNotNullElements(coll);
        double capacity = (double) getCapacity(coll);
        if (numberOfNotNullElements == 0 && capacity == 0)
        {
            // Arbitrary - zero length array isn't wasting space
            return 1.0;
        }
        return numberOfNotNullElements / capacity;
    }
}
