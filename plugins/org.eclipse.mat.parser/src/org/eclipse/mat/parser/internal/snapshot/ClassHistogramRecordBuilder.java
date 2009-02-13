/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.HistogramRecord;

public class ClassHistogramRecordBuilder extends HistogramRecord
{
    private static final long serialVersionUID = 1L;

    private int classId;
    private ArrayIntBig objectIds;

    public ClassHistogramRecordBuilder(String label, int classId)
    {
        super(label, 0, 0, 0);
        this.classId = classId;
        this.objectIds = new ArrayIntBig();
    }

    public void add(long usedHeapSize)
    {
        this.numberOfObjects++;
        this.usedHeapSize += usedHeapSize;
    }

    public void add(int objectId, long usedHeapSize)
    {
        this.objectIds.add(objectId);
        this.numberOfObjects++;
        this.usedHeapSize += usedHeapSize;
    }

    public void addAll(long numberOfObjects, long usedHeapSize)
    {
        this.numberOfObjects += numberOfObjects;
        this.usedHeapSize += usedHeapSize;
    }

    public void addAll(int[] objectIds, long usedHeapSize)
    {
        this.objectIds.addAll(objectIds);
        this.numberOfObjects += objectIds.length;
        this.usedHeapSize += usedHeapSize;
    }

    public ClassHistogramRecord toClassHistogramRecord()
    {
        if (objectIds.length() > 0 && this.numberOfObjects != objectIds.length())
            throw new RuntimeException(Messages.ClassHistogramRecordBuilder_Error_IllegalUseOfHistogramBuilder);

        if (objectIds.length() > 0)
            return new ClassHistogramRecord(getLabel(), classId, objectIds.toArray(), getUsedHeapSize(),
                            getRetainedHeapSize());
        else
            return new ClassHistogramRecord(getLabel(), classId, getNumberOfObjects(), getUsedHeapSize(),
                            getRetainedHeapSize());
    }
}
