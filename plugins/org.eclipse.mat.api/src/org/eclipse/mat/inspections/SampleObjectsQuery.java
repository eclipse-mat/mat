/*******************************************************************************
 * Copyright (c) 2026 IBM.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("sample_objects")
@Icon("/META-INF/icons/sample_objects.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/sample_objects.html")
public class SampleObjectsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = true)
    public SamplingType sample = SamplingType.RANDOM;

    @Argument(isMandatory = true)
    public int number = 10000;

    public enum SamplingType
    {
        FIRST(Messages.SampleObjectQuery_SampleType_First, //
                        Icons.getURL("down.gif")), //$NON-NLS-1$
        LAST(Messages.SampleObjectQuery_SampleType_Last, //
                        Icons.getURL("up.gif")), //$NON-NLS-1$
        RANDOM(Messages.SampleObjectQuery_SampleType_Random, //
                        Icons.getURL("balls.gif")), //$NON-NLS-1$
        LARGEST_RETAINED(Messages.SampleObjectQuery_SampleType_LargestRetainedHeap, //
                        Icons.getURL("down.gif")), //$NON-NLS-1$
        LARGEST_SHALLOW(Messages.SampleObjectQuery_SampleType_LargestShallowHeap, //
                        Icons.getURL("down.gif")), //$NON-NLS-1$
        SMALLEST_RETAINED(Messages.SampleObjectQuery_SampleType_SmallestRetainedHeap, //
                        Icons.getURL("up.gif")), //$NON-NLS-1$
        SMALLEST_SHALLOW(Messages.SampleObjectQuery_SampleType_SmallestShallowHeap, //
                        Icons.getURL("up.gif")); //$NON-NLS-1$

        String label;
        URL icon;

        private SamplingType(String label, URL icon)
        {
            this.label = label;
            this.icon = icon;
        }

        public URL getIcon()
        {
            return icon;
        }

        public String toString()
        {
            return label;
        }
    }

    public IResult execute(IProgressListener listener) throws Exception
    {
        if (number <= 0)
        {
            throw new IllegalArgumentException("number"); //$NON-NLS-1$
        }

        int[] objectIds = objects.getIds(listener);

        int[] finalObjectIds = null;
        if (objectIds.length <= number)
        {
            finalObjectIds = objectIds;
        }
        else if (sample == SamplingType.FIRST)
        {
            finalObjectIds = new int[number];
            System.arraycopy(objectIds, 0, finalObjectIds, 0, number);
        }
        else if (sample == SamplingType.LAST)
        {
            finalObjectIds = new int[number];
            System.arraycopy(objectIds, objectIds.length - number, finalObjectIds, 0, number);
        }
        else if (sample == SamplingType.LARGEST_RETAINED)
        {
            finalObjectIds = Arrays.stream(objectIds).boxed().sorted((x, y) -> {
                try
                {
                    return Long.compare(snapshot.getRetainedHeapSize(y), snapshot.getRetainedHeapSize(x));
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }).limit(number).mapToInt(Integer::intValue).toArray();
        }
        else if (sample == SamplingType.SMALLEST_RETAINED)
        {
            finalObjectIds = Arrays.stream(objectIds).boxed().sorted((x, y) -> {
                try
                {
                    return Long.compare(snapshot.getRetainedHeapSize(x), snapshot.getRetainedHeapSize(y));
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }).limit(number).mapToInt(Integer::intValue).toArray();
        }
        else if (sample == SamplingType.LARGEST_SHALLOW)
        {
            finalObjectIds = Arrays.stream(objectIds).boxed().sorted((x, y) -> {
                try
                {
                    return Long.compare(snapshot.getHeapSize(y), snapshot.getHeapSize(x));
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }).limit(number).mapToInt(Integer::intValue).toArray();
        }
        else if (sample == SamplingType.SMALLEST_SHALLOW)
        {
            finalObjectIds = Arrays.stream(objectIds).boxed().sorted((x, y) -> {
                try
                {
                    return Long.compare(snapshot.getHeapSize(x), snapshot.getHeapSize(y));
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }).limit(number).mapToInt(Integer::intValue).toArray();
        }
        else if (sample == SamplingType.RANDOM)
        {
            List<Integer> list = Arrays.asList(Arrays.stream(objectIds).boxed().toArray(Integer[]::new));
            Collections.shuffle(list);
            finalObjectIds = list.stream().limit(number).mapToInt(Integer::intValue).toArray();
        }
        else
        {
            throw new UnsupportedOperationException();
        }

        return new ObjectListResult.Outbound(snapshot, finalObjectIds);
    }
}
