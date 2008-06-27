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
package org.eclipse.mat.parser.model;

import java.util.ArrayList;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


public final class XClassLoaderHistogramRecord extends ClassLoaderHistogramRecord
{
    private static final long serialVersionUID = 1L;

    public XClassLoaderHistogramRecord(String label, int classLoaderId,
                    ArrayList<ClassHistogramRecord> classHistogramRecords, long numberOfObjects, long usedHeapSize,
                    long retainedHeapSize)
    {
        super(label, classLoaderId, classHistogramRecords, numberOfObjects, usedHeapSize, retainedHeapSize);
    }

    @Override
    public long calculateRetainedSize(ISnapshot snapshot, boolean calculateIfNotAvailable, boolean approximation,
                    IProgressListener listener) throws SnapshotException
    {
        if (retainedHeapSize > 0 || !calculateIfNotAvailable)
            return retainedHeapSize;

        if (retainedHeapSize < 0 && approximation)
            return retainedHeapSize;

        IObject object = snapshot.getObject(classLoaderId);
        if (object instanceof IClassLoader)
        {
            retainedHeapSize = ((IClassLoader) object).getRetainedHeapSizeOfObjects(calculateIfNotAvailable,
                            approximation, listener);
            return retainedHeapSize;
        }
        else
        {
            return super.calculateRetainedSize(snapshot, calculateIfNotAvailable, approximation, listener);
        }
    }

}
