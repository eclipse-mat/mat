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
package org.eclipse.mat.inspections.threads;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@Name("Thread Overview")
@Category("Java Basics")
@Icon("/META-INF/icons/threads.gif")
public class ThreadOverviewQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask("Searching Threads...");

        List<ThreadInfoImpl> result = new ArrayList<ThreadInfoImpl>();

        Collection<IClass> classes = snapshot.getClassesByName("java.lang.Thread", true);
        for (IClass clasz : classes)
        {
            for (int id : clasz.getObjectIds())
            {
                result.add(ThreadInfoImpl.build(snapshot.getObject(id), false, listener));
            }
        }

        Collections.sort(result, new Comparator<ThreadInfoImpl>()
        {
            public int compare(ThreadInfoImpl o1, ThreadInfoImpl o2)
            {
                return o1.getRetainedHeap() > o2.getRetainedHeap() ? -1
                                : o1.getRetainedHeap() == o2.getRetainedHeap() ? 0 : 1;
            }
        });

        if (result.isEmpty())
            return null;

        return new ThreadInfoList(snapshot, result);
    }

    private static class ThreadInfoList implements IResultTable, IIconProvider
    {

        ISnapshot snapshot;
        List<ThreadInfoImpl> infos;
        Column[] columns;

        public ThreadInfoList(ISnapshot snapshot, List<ThreadInfoImpl> infos)
        {
            this.snapshot = snapshot;
            this.infos = infos;

            this.columns = ThreadInfoImpl.getColumns().toArray(new Column[0]);
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //
                            .addDetailResult(new DetailResultProvider("Thread Details")
                            {
                                @Override
                                public boolean hasResult(Object row)
                                {
                                    return true;
                                }

                                @Override
                                public IResult getResult(Object row, IProgressListener listener)
                                                throws SnapshotException
                                {
                                    int threadId = ((ThreadInfoImpl) row).getThreadId();

                                    return SnapshotQuery.lookup("thread_details", snapshot) //
                                                    .set("threadIds", threadId) //
                                                    .execute(listener);
                                }

                            }) //
                            .build();
        }

        public Column[] getColumns()
        {
            return columns;
        }

        public Object getRow(int rowId)
        {
            return infos.get(rowId);
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            ThreadInfoImpl info = (ThreadInfoImpl) row;
            return info.getValue(columns[columnIndex]);
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((ThreadInfoImpl) row).getThreadId();
                }
            };
        }

        public int getRowCount()
        {
            return infos.size();
        }

        public URL getIcon(Object row)
        {
            return Icons.forObject(snapshot, ((ThreadInfoImpl) row).getThreadId());
        }
    }
}
