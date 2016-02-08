/*******************************************************************************
 * Copyright (c) 2016 Red Hat and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.model.IObject;

public class LinkedBoundedCollectionExtractor extends FieldSizedCollectionExtractor
{
    private final String capacityField;
    private final String headField;
    private final String nextField;
    private final String elementField;

    public LinkedBoundedCollectionExtractor(String sizeField, String capacityField,
                    String headField, String nextField, String elementField)
    {
        super(sizeField);
        this.capacityField = capacityField;
        if (headField == null)
            throw new IllegalArgumentException();
        this.headField = headField;
        if (nextField == null)
            throw new IllegalArgumentException();
        this.nextField = nextField;
        if (elementField == null)
            throw new IllegalArgumentException();
        this.elementField = elementField;
    }

    @Override
    public boolean hasCapacity()
    {
        return capacityField != null;
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        if (capacityField == null)
            throw new IllegalArgumentException();
        else
            return ExtractionUtils.toInteger(coll.resolveValue(capacityField));
    }

    @Override
    public boolean hasExtractableContents()
    {
        return true;
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ArrayInt arr = new ArrayInt();
        
        IObject cur = (IObject) coll.resolveValue(headField);
        while (cur != null) {
            IObject element = (IObject) cur.resolveValue(elementField);
            if (element != null) {
                arr.add(element.getObjectId());
            }

            IObject next = (IObject) cur.resolveValue(nextField);
            //there could be self-loop if the queue modification was in progress
            cur = (next != cur) ? next : null;
        }
        
        return arr.toArray();
    }

    @Override
    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return getSize(coll);
    }

    @Override
    public boolean hasFillRatio()
    {
        return hasCapacity();
    }

    @Override
    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return ((double)getSize(coll))/((double)getCapacity(coll));
    }
}
