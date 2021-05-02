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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

public class FieldSizedCapacityMapExtractor extends FieldSizedCapacityCollectionExtractor implements IMapExtractor
{
    String capacityField;

    public FieldSizedCapacityMapExtractor(String sizeField, String arrayField, String capacityField)
    {
        super(sizeField, arrayField);
        this.capacityField = capacityField;
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        if (capacityField != null)
        {
            Integer i = ExtractionUtils.toInteger(coll.resolveValue(capacityField));
            if (i != null)
                return i;
        }
        return super.getCapacity(coll);
    }

    @Override
    public boolean hasCollisionRatio()
    {
        return true;
    }

    @Override
    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        IArray obj = getArray(coll);
        if (obj instanceof IPrimitiveArray)
        {
            IPrimitiveArray pa = (IPrimitiveArray)obj;
            int consec = 0;
            boolean prev = false;
            boolean prev0 = false;
            for (int i = 0; i < pa.getLength(); ++i)
            {
                Object ba = pa.getValueAt(i);
                if (ba instanceof Boolean && ((Boolean)ba))
                {
                    if (prev)
                    {
                        ++consec;
                    }
                    prev = true;
                    if (i == 0)
                        prev0 = true;
                }
                else
                {
                    prev = false;
                }
            }
            if (prev && prev0)
                ++consec;
            double x = (double)consec / pa.getLength();
            return 0.29 * x + 0.21 * x * x;
        }
        return null;
    }

    @Override
    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject collection) throws SnapshotException
    {
        return Collections.<Entry<IObject, IObject>>emptyIterator();
    }

}
