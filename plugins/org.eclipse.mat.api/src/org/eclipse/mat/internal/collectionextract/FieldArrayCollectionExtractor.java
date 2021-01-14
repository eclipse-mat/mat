/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
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
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.MessageUtil;

public class FieldArrayCollectionExtractor implements ICollectionExtractor
{
    protected final String arrayField;

    public FieldArrayCollectionExtractor(String arrayField)
    {
        if (arrayField == null)
            throw new IllegalArgumentException();
        this.arrayField = arrayField;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        // what if they can hold null?
        return getNumberOfNotNullElements(coll);
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IObjectArray arrayObject = extractEntries(coll);
        if (arrayObject == null)
            return null;
        else
            return arrayObject.getLength();
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        Integer size = getNumberOfNotNullElements(coll);
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

    public boolean hasExtractableContents()
    {
        return hasExtractableArray();
    }

    public boolean hasExtractableArray()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.referenceArrayToIds(coll.getSnapshot(), extractEntries(coll).getReferenceArray());
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        final Object obj = coll.resolveValue(arrayField);
        if (obj instanceof IObjectArray)
        {
            return (IObjectArray) obj;
        }
        else if (obj != null)
        {
            String desc = (obj instanceof IObject) ? ((IObject) obj).getTechnicalName() : obj.toString();
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), desc);
            throw new SnapshotException(msg);
        }

        // the array wasn't found, because the field information is missing
        IObject next = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(arrayField, coll);
        if (next == null)
            return null;
        return ExtractionUtils.getOnlyArrayField(next);
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        IObjectArray arrayObject = extractEntries(coll);
        if (arrayObject == null)
            return null;
        return ExtractionUtils.getNumberOfNotNullArrayElements(arrayObject);
    }
}
