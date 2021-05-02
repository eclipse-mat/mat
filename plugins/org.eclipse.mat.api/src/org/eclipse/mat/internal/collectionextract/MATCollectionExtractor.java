/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class MATCollectionExtractor implements ICollectionExtractor
{

    String sizeField;
    String capacityField;
    public MATCollectionExtractor(String sizeField, String capacityField)
    {
        this.sizeField = sizeField;
        this.capacityField = capacityField;
    }

    public boolean hasSize()
    {
        return sizeField != null;
    }

    public Integer getSize(IObject collection) throws SnapshotException
    {
        return (Integer) collection.resolveValue(sizeField);
    }

    public boolean hasCapacity()
    {
        return capacityField != null;
    }

    public Integer getCapacity(IObject collection) throws SnapshotException
    {
        return (Integer) collection.resolveValue(sizeField);
    }

    public boolean hasFillRatio()
    {
        return hasSize() && hasCapacity();
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        Integer size = getSize(coll);
        Integer cap = getCapacity(coll);
        if (size != null && cap != null)
        {
            double sz = size.doubleValue();
            double cp = cap.doubleValue();
            // If the size and capacity are zero mark as full
            // to avoid generating wasted space reports
            if (sz == 0.0 && cp == 0.0)
                return 1.0;
            else
                return sz / cp;
        }
        else
        {
            // Sometimes an empty map doesn't have a capacity yet
            return 1.0;
        }
    }

    public boolean hasExtractableContents()
    {
        return false;
    }

    public int[] extractEntryIds(IObject collection) throws SnapshotException
    {
        return null;
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject collection) throws SnapshotException
    {
        return null;
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        return getSize(collection);
    }

}
