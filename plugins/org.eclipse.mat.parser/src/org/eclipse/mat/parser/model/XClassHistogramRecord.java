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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

/**
 * Holds details about a class, the number of instances and the shallow and retained sizes.
 * This hold a direct link to the class instance.
 */
public final class XClassHistogramRecord extends ClassHistogramRecord
{
    private static final long serialVersionUID = 1L;

    private ClassImpl classInstance;

    /**
     * Create an XClassHistogramRecord by retrieving information from the class
     * @param classInstance the class
     * @throws SnapshotException
     */
    public XClassHistogramRecord(ClassImpl classInstance) throws SnapshotException
    {
        super(classInstance.getName(), //
                        classInstance.getObjectId(), //
                        classInstance.getNumberOfObjects(), //
                        classInstance.getTotalSize(), //
                        classInstance.getRetainedHeapSizeOfObjects(false, false, null));
        this.classInstance = classInstance;
    }

    public int getClassId()
    {
        return classInstance.getObjectId();
    }

    public int[] getObjectIds()
    {
        try
        {
            return classInstance.source.getIndexManager().c2objects().getObjectsOf(classInstance.getCacheEntry());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long calculateRetainedSize(ISnapshot snapshot, boolean calculateIfNotAvailable, boolean approximation,
                    IProgressListener listener) throws SnapshotException
    {
        if (retainedHeapSize > 0 || !calculateIfNotAvailable)
            return retainedHeapSize;

        if (retainedHeapSize < 0 && approximation)
            return retainedHeapSize;

        retainedHeapSize = classInstance.getRetainedHeapSizeOfObjects(calculateIfNotAvailable, approximation, listener);

        return retainedHeapSize;
    }
}
