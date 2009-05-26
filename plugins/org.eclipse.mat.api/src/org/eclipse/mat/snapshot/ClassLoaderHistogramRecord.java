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

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.util.IProgressListener;

/**
 * This class holds the histogram data on the objects found in the object set
 * for which a histogram was computed (aggregated per class loader).
 */
public class ClassLoaderHistogramRecord extends HistogramRecord
{
    private static final long serialVersionUID = 1L;

    protected int classLoaderId;
    private ArrayList<ClassHistogramRecord> classHistogramRecords;

    ClassLoaderHistogramRecord()
    {
        super();
    }

    public ClassLoaderHistogramRecord(String label, int classLoaderId,
                    ArrayList<ClassHistogramRecord> classHistogramRecords, long numberOfObjects, long usedHeapSize,
                    long retainedHeapSize)
    {
        super(label, numberOfObjects, usedHeapSize, retainedHeapSize);
        this.classLoaderId = classLoaderId;
        this.classHistogramRecords = classHistogramRecords;
    }

    /**
     * Get id of the class loader this class loader histogram record stands for.
     * 
     * @return id of the class loader this class loader histogram record stands
     *         for
     */
    public int getClassLoaderId()
    {
        return classLoaderId;
    }

    /**
     * Get collection of the class histogram records this class loader histogram
     * record stands for.
     * 
     * @return collection of the class histogram records this class loader
     *         histogram record stands for
     */
    public Collection<ClassHistogramRecord> getClassHistogramRecords()
    {
        return classHistogramRecords;
    }

    /**
     * Get ids of the objects this class loader histogram record stands for.
     * 
     * @return ids of the objects this class loader histogram record stands for
     * @throws SnapshotException
     */
    public int[] getObjectIds() throws SnapshotException
    {
        ArrayIntBig ids = new ArrayIntBig();
        for (ClassHistogramRecord record : classHistogramRecords)
        {
            ids.addAll(record.getObjectIds());
        }
        return ids.toArray();
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
            retainedHeapSize = snapshot.getMinRetainedSize(getObjectIds(), listener);
            retainedHeapSize = -retainedHeapSize;
        }
        else
        {
            retainedHeapSize = snapshot.getHeapSize(snapshot.getRetainedSet(getObjectIds(), listener));
        }

        return retainedHeapSize;
    }

    @SuppressWarnings("nls")
    @Override
    public String toString()
    {
        StringBuilder summary = new StringBuilder();
        summary.append("Class Loader Histogram ");
        summary.append(label);
        summary.append(" [");
        summary.append(classLoaderId);
        summary.append("] with ");
        summary.append((classHistogramRecords != null) ? classHistogramRecords.size() : 0);
        summary.append(" classes, ");
        summary.append(numberOfObjects);
        summary.append(" objects, ");
        summary.append(usedHeapSize);
        summary.append(" used heap bytes:\r\n\r\n");
        if (classHistogramRecords != null)
        {
            for (ClassHistogramRecord record : classHistogramRecords)
            {
                summary.append(record);
                summary.append("\r\n"); //$NON-NLS-1$
            }
        }
        return summary.toString();
    }
}
