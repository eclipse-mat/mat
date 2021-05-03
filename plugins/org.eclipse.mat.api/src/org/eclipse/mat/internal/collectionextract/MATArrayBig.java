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
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

public class MATArrayBig extends FieldSizedCollectionExtractor
{
    WrapperCollectionExtractor pages;

    public MATArrayBig(String sizeField, String pagesField)
    {
        super(sizeField);
        pages = new WrapperCollectionExtractor(pagesField);
    }

    public boolean hasCapacity()
    {
        return pages.hasExtractableContents();
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        int[] pageArrays = pages.extractEntryIds(coll);
        if (pageArrays == null)
            return null;
        ISnapshot snapshot = coll.getSnapshot();
        int capacity = 0;
        for (int a : pageArrays)
        {
            if (snapshot.isArray(a))
            {
                IObject a1 = snapshot.getObject(a);
                if (a1 instanceof IPrimitiveArray)
                {
                    capacity += ((IPrimitiveArray)a1).getLength();
                }
            }
        }
        return capacity;
    }

    @Override
    public boolean hasFillRatio()
    {
        return hasCapacity();
    }

    @Override
    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        Integer size = getSize(coll);
        Integer cap = getCapacity(coll);
        if (size != null && cap != null)
        {
            double sz = size.doubleValue();
            double cp = cap.doubleValue();
            if (sz == 0.0 && cp == 0.0)
            {
                return 1.0;
            }
            else
            {
                return sz / cp;
            }
        }
        else
            return null;
    }

    @Override
    public boolean hasExtractableContents()
    {
        return false;
    }

    @Override
    public boolean hasExtractableArray()
    {
        return false;
    }
}
