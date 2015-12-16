/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

public class FieldSizedCollectionExtractor implements ICollectionExtractor
{
    private final String sizeField;

    public FieldSizedCollectionExtractor(String sizeField)
    {
        if (sizeField == null)
            throw new IllegalArgumentException();
        this.sizeField = sizeField;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.toInteger(coll.resolveValue(sizeField));
    }

    public boolean hasCapacity()
    {
        return false;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public boolean hasExtractableContents()
    {
        return false;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public boolean hasFillRatio()
    {
        return false;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return null;
    }
}
