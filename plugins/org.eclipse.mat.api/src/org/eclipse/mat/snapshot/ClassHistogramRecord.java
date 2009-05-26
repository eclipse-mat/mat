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
package org.eclipse.mat.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * This class holds the histogram data on the objects found in the object set
 * for which a histogram was computed (aggregated per class).
 */
public class ClassHistogramRecord extends HistogramRecord
{
    private static final long serialVersionUID = 1L;

    private int classId;
    private int[] objectIds;

    /* package */ClassHistogramRecord()
    {
        super();
    }

    public ClassHistogramRecord(String label, int classId, long numberOfObjects, long usedHeapSize,
                    long retainedHeapSize)
    {
        super(label, numberOfObjects, usedHeapSize, retainedHeapSize);
        this.classId = classId;
        this.objectIds = new int[0];
    }

    public ClassHistogramRecord(String label, int classId, int[] objectIds, long usedHeapSize, long retainedHeapSize)
    {
        super(label, objectIds.length, usedHeapSize, retainedHeapSize);
        this.classId = classId;
        this.objectIds = objectIds;
    }

    /**
     * Get id of the class this class histogram record stands for.
     * 
     * @return id of the class this class histogram record stands for
     */
    public int getClassId()
    {
        return classId;
    }

    /**
     * Get ids of the objects this class histogram record stands for.
     * 
     * @return ids of the objects this class histogram record stands for
     */
    public int[] getObjectIds()
    {
        return objectIds;
    }

    public long calculateRetainedSize(ISnapshot snapshot, boolean calculateIfNotAvailable, boolean approximation,
                    IProgressListener listener) throws SnapshotException
    {
        if (retainedHeapSize > 0 || !calculateIfNotAvailable)
            return retainedHeapSize;

        if (retainedHeapSize < 0 && approximation)
            return retainedHeapSize;

        if (approximation)
        {
            retainedHeapSize = snapshot.getMinRetainedSize(objectIds, listener);
            retainedHeapSize = -retainedHeapSize;
        }
        else
        {
            retainedHeapSize = snapshot.getHeapSize(snapshot.getRetainedSet(objectIds, listener));
        }

        return retainedHeapSize;
    }

    @SuppressWarnings("nls")
    @Override
    public String toString()
    {
        StringBuilder summary = new StringBuilder();
        summary.append("Class Histogram ");
        summary.append(label);
        summary.append(" [");
        summary.append(classId);
        summary.append("] with ");
        summary.append(numberOfObjects);
        summary.append(" objects, ");
        summary.append(usedHeapSize);
        summary.append(" used heap bytes.");
        return summary.toString();
    }
}
