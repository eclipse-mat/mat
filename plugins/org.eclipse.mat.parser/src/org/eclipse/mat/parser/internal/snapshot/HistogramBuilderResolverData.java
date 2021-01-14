/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

/**
 * @deprecated Use {@link HistogramBuilder#toHistogram(SnapshotImpl, boolean)} instead
 */
@Deprecated
public class HistogramBuilderResolverData
{
    private String classLabel;
    private long classUsedPermSize;
    private String classLoaderLabel;
    private int classLoaderId;
    private long retainedHeapSize;

    public HistogramBuilderResolverData(String classLabel, long classUsedPermSize, String classLoaderLabel,
                    int classLoaderId, long retainedHeapSize)
    {
        this.classLabel = classLabel;
        this.classUsedPermSize = classUsedPermSize;
        this.classLoaderLabel = classLoaderLabel;
        this.classLoaderId = classLoaderId;
        this.retainedHeapSize = retainedHeapSize;
    }

    public String getClassLabel()
    {
        return classLabel;
    }

    public long getClassUsedPermSize()
    {
        return classUsedPermSize;
    }

    public String getClassLoaderLabel()
    {
        return classLoaderLabel;
    }

    public int getClassLoaderId()
    {
        return classLoaderId;
    }

    public long getRetainedHeapSize()
    {
        return retainedHeapSize;
    }

}
