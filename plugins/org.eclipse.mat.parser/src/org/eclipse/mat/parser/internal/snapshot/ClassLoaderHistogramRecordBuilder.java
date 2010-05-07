/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import java.util.ArrayList;

import org.eclipse.mat.parser.model.XClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.HistogramRecord;

public class ClassLoaderHistogramRecordBuilder extends HistogramRecord
{
    private static final long serialVersionUID = 1L;

    private int classLoaderId;
    private ArrayList<ClassHistogramRecord> classHistogramRecords;

    public ClassLoaderHistogramRecordBuilder(String label, int classLoaderId, long retainedHeapSize)
    {
        super(label, 0, 0, retainedHeapSize);
        this.classLoaderId = classLoaderId;
        this.classHistogramRecords = new ArrayList<ClassHistogramRecord>();
    }

    public void add(ClassHistogramRecord classHistogramRecord)
    {
        this.classHistogramRecords.add(classHistogramRecord);
        this.incNumberOfObjects(classHistogramRecord.getNumberOfObjects());
        this.incUsedHeapSize(classHistogramRecord.getUsedHeapSize());
    }

    public ClassLoaderHistogramRecord toClassLoaderHistogramRecord(boolean isDefaultHistogram)
    {
        return isDefaultHistogram ? new XClassLoaderHistogramRecord(getLabel(), classLoaderId, classHistogramRecords,
                        getNumberOfObjects(), getUsedHeapSize(), getRetainedHeapSize())
                        : new ClassLoaderHistogramRecord(getLabel(), classLoaderId, classHistogramRecords,
                                        getNumberOfObjects(), getUsedHeapSize(), getRetainedHeapSize());
    }
}
