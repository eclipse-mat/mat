/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - OQL continuation
 *******************************************************************************/
package org.eclipse.mat.inspections.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

@CommandName("component_report_top")
@Icon("/META-INF/icons/top_components_report.gif")
public class TopComponentsReportQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false, flag = "t")
    public int thresholdPercent = 1;

    @Argument(isMandatory = false)
    public boolean aggressive;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SimpleMonitor sm = new SimpleMonitor(Messages.TopComponentsReportQuery_TopComponentReports, listener, new int[] {10,90});
        int[] topDominators = snapshot.getImmediateDominatedIds(-1);

        List<Record> loaders = createClassLoaderRecords(sm.nextMonitor(), topDominators);

        SectionSpec result = new SectionSpec(Messages.TopComponentsReportQuery_TopComponentReports);

        long totalHeapSize = snapshot.getSnapshotInfo().getUsedHeapSize();
        long threshold = totalHeapSize / 100 * thresholdPercent;

        int nr = 0;
        for (Record record : loaders)
        {
            if (record.retainedSize < threshold)
                break;
            ++nr;
        }
        int tasks[] = new int[nr];
        int nobj = snapshot.getSnapshotInfo().getNumberOfObjects();
        long totalheap = snapshot.getSnapshotInfo().getUsedHeapSize();
        if (totalheap > 0)
        {
            for (int i = 0; i < nr; ++i)
            {
                // estimate how much work for each loader
                tasks[i] = (int)(loaders.get(i).retainedSize * nobj / totalheap);
            }
        }
        else
        {
            Arrays.fill(tasks, 100);
        }
        SimpleMonitor sm2 = new SimpleMonitor(Messages.TopComponentsReportQuery_TopComponentReports, sm.nextMonitor(), tasks);

        for (Record record : loaders)
        {
            if (listener.isCanceled())
                break;
            if (record.retainedSize < threshold)
                break;

            String oql = oqlLoader(record.name, record.loaderAddr);
            oql = oql.replace("\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$
            SnapshotQuery query = SnapshotQuery.parse("component_report "+oql+";", snapshot); //$NON-NLS-1$ //$NON-NLS-2$
            query.setArgument("aggressive", aggressive); //$NON-NLS-1$
            IResult report = query.execute(sm2.nextMonitor());

            QuerySpec spec = new QuerySpec(MessageUtil.format("{0} ({1,number,percent})", //$NON-NLS-1$
                            record.name, (double) record.retainedSize / (double) totalHeapSize), report);
            spec.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
            /*
             * Set a command which generates the same report for a class loader
             * Uses OQL to select from the top level dominators,
             * choosing an object which is the class loader,
             * or is a class and is loaded by the loader
             * or is an ordinary object of type of of a class loaded by the class loader.
             */
            spec.setCommand("component_report "+ //$NON-NLS-1$
                            (aggressive ? "-aggressive " : "") +   //$NON-NLS-1$//$NON-NLS-2$
                             oql + ";" ); //$NON-NLS-1$
            result.add(spec);
        }

        return result;
    }

    private String oqlLoader(String name, long loaderAddress)
    {
        return
        //"select * from objects (select objects a from objects (dominators(-1)) a) b"+ //$NON-NLS-1$
        // Add the component name as a comment
        "select * /* " + name.replace("*/", "* /") + " */ from objects (select objects a from objects (dominators(-1)) a) b"+ //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        " where"+ //$NON-NLS-1$
        " b implements org.eclipse.mat.snapshot.model.IClassLoader and b.@objectAddress = " + loaderAddress + "L or"+ //$NON-NLS-1$ //$NON-NLS-2$
        " b implements org.eclipse.mat.snapshot.model.IClass and b.@classLoaderAddress = " + loaderAddress + "L or"+ //$NON-NLS-1$ //$NON-NLS-2$
        " b implements org.eclipse.mat.snapshot.model.IClassLoader = false and b implements org.eclipse.mat.snapshot.model.IClass = false and b.@clazz.@classLoaderAddress = " + loaderAddress + "L"; //$NON-NLS-1$ //$NON-NLS-2$
        //" $ {snapshot}.isClass(b.@objectId) and b.@classLoaderAddress = " + loaderAddress + "L or"+ //$NON-NLS-1$ //$NON-NLS-2$
        //" $ {snapshot}.isClassLoader(b.@objectId) = false and $ {snapshot}.isClass(b.@objectId) = false and b.@clazz.@classLoaderAddress = " + loaderAddress + "L"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private List<Record> createClassLoaderRecords(IProgressListener listener, int[] topDominators)
                    throws SnapshotException
    {
        HashMapIntObject<Record> id2loader = new HashMapIntObject<Record>();

        for (int ii = 0; ii < topDominators.length; ii++)
        {
            int classLoaderId;
            if (snapshot.isClass(topDominators[ii]))
            {
                classLoaderId = ((IClass) snapshot.getObject(topDominators[ii])).getClassLoaderId();
            }
            else if (snapshot.isClassLoader(topDominators[ii]))
            {
                classLoaderId = topDominators[ii];
            }
            else
            {
                classLoaderId = snapshot.getClassOf(topDominators[ii]).getClassLoaderId();
            }

            Record loaderRecord = id2loader.get(classLoaderId);
            if (loaderRecord == null)
            {
                IObject loader = snapshot.getObject(classLoaderId);
                String name = loader.getClassSpecificName();
                if (name == null)
                    name = loader.getTechnicalName();
                loaderRecord = new Record(name, loader.getObjectAddress());
                id2loader.put(classLoaderId, loaderRecord);
            }
            loaderRecord.objects.add(topDominators[ii]);
            loaderRecord.retainedSize += snapshot.getRetainedHeapSize(topDominators[ii]);

            if (ii % 1000 == 0)
            {
                listener.worked(1);
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

        List<Record> loaders = new ArrayList<Record>(id2loader.size());
        for (Iterator<Record> ee = id2loader.values(); ee.hasNext();)
            loaders.add(ee.next());

        Collections.sort(loaders);

        return loaders;
    }

    // //////////////////////////////////////////////////////////////
    // internal classes
    // //////////////////////////////////////////////////////////////

    /**
     * Records objects associated with this class loader.
     */
    private static class Record implements Comparable<Record>
    {
        String name;
        ArrayInt objects = new ArrayInt();
        long retainedSize;
        long loaderAddr;

        /**
         * Create record to hold objects associated with the class loader
         * @param name The name of the class loader
         * @param addr Its address
         */
        public Record(String name, long addr)
        {
            this.name = name;
            this.loaderAddr = addr;
        }

        public int compareTo(Record other)
        {
            return retainedSize > other.retainedSize ? -1 : retainedSize == other.retainedSize ? 0 : 1;
        }

    }
}
