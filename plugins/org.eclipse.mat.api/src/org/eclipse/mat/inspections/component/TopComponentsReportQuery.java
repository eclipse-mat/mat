/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.component;

import java.util.ArrayList;
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
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("component_report_top")
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
        int[] topDominators = snapshot.getImmediateDominatedIds(-1);

        List<Record> loaders = createClassLoaderRecords(listener, topDominators);

        SectionSpec result = new SectionSpec(Messages.TopComponentsReportQuery_TopComponentReports);

        long totalHeapSize = snapshot.getSnapshotInfo().getUsedHeapSize();
        long threshold = totalHeapSize / 100 * thresholdPercent;

        for (Record record : loaders)
        {
            if (record.retainedSize < threshold)
                break;

            SnapshotQuery query = SnapshotQuery.lookup("component_report", snapshot) //$NON-NLS-1$
                            .setArgument("objects", record.objects); //$NON-NLS-1$
            query.setArgument("aggressive", aggressive); //$NON-NLS-1$
            IResult report = query.execute(listener);

            QuerySpec spec = new QuerySpec(MessageUtil.format("{0} ({1,number,percent})", //$NON-NLS-1$
                            record.name, (double) record.retainedSize / (double) totalHeapSize), report);
            spec.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
            result.add(spec);
        }

        return result;
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
                loaderRecord = new Record(name);
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

    private static class Record implements Comparable<Record>
    {
        String name;
        ArrayInt objects = new ArrayInt();
        long retainedSize;

        public Record(String name)
        {
            this.name = name;
        }

        public int compareTo(Record other)
        {
            return retainedSize > other.retainedSize ? -1 : retainedSize == other.retainedSize ? 0 : 1;
        }

    }
}
