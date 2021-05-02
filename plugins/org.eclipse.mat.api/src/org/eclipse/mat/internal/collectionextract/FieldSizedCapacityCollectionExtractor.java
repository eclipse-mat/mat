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
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.MessageUtil;

public class FieldSizedCapacityCollectionExtractor extends FieldSizeArrayCollectionExtractor
{
    public FieldSizedCapacityCollectionExtractor(String sizeField, String arrayField)
    {
        super(sizeField, arrayField);
    }

    @Override
    public boolean hasCapacity()
    {
        return true;
    }

    public IArray getArray(IObject coll) throws SnapshotException
    {
        Object obj = coll.resolveValue(arrayField);
        if (obj == null)
        {
            // the array wasn't found, because the field information is missing
            IObject next = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(arrayField, coll);
            if (next == null)
                return null;
            // Look for the only object array field
            IObject ret = null;
            final ISnapshot snapshot = next.getSnapshot();
            for (int i : snapshot.getOutboundReferentIds(next.getObjectId()))
            {
                if (snapshot.isArray(i))
                {
                    IObject o = snapshot.getObject(i);
                    if (o instanceof IArray)
                    {
                        // Have we already found a possible return type?
                        // If so, things are uncertain and so give up.
                        if (ret != null)
                            return null;
                        ret = (IArray) o;
                    }
                }
            }
            obj = ret;
        }
        if (obj instanceof IArray)
        {
            return ((IArray) obj);
        }
        else if (obj != null)
        {
            String desc = (obj instanceof IObject) ? ((IObject) obj).getTechnicalName() : obj.toString();
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), desc);
            throw new SnapshotException(msg);
        }
        return null;
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IArray obj = getArray(coll);
        if (obj != null)
            return obj.getLength();
        return null;
    }

    @Override
    public boolean hasExtractableContents()
    {
        return false;
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean hasExtractableArray()
    {
        return false;
    }

    @Override
    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }
}
