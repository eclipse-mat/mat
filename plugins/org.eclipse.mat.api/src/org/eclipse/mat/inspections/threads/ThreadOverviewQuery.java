/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - optional columns
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@CommandName("thread_overview")
@Icon("/META-INF/icons/threads.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingthreads.html")
public class ThreadOverviewQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false, flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;
    
    private static URL THREAD_ICON_URL = ThreadOverviewQuery.class.getResource("/META-INF/icons/threads.gif"); //$NON-NLS-1$
    public static final String CLASS_THREAD = "java.lang.Thread";

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.ThreadOverviewQuery_SearchingThreads);

        List<ThreadOverviewNode> result = new ArrayList<ThreadOverviewNode>();

        if (objects != null)
        {
            for (int[] objectIds : objects)
            {
                for (int objectId : objectIds)
                {
                    if (isThread(snapshot, objectId)) {
                        result.add(buildThreadOverviewNode(objectId, listener));
                    }
                }
            }
        }
        else
        {
            Collection<IClass> classes = snapshot.getClassesByName(CLASS_THREAD, true); //$NON-NLS-1$
            if (classes != null)
            {
                for (IClass clasz : classes)
                {
                    for (int id : clasz.getObjectIds())
                    {
                        result.add(buildThreadOverviewNode(id, listener));
                    }
                }
            }
        }

        Collections.sort(result, new Comparator<ThreadOverviewNode>()
        {
            public int compare(ThreadOverviewNode o1, ThreadOverviewNode o2)
            {
                return o1.threadInfo.getRetainedHeap() > o2.threadInfo.getRetainedHeap() ? -1
                                : o1.threadInfo.getRetainedHeap() == o2.threadInfo.getRetainedHeap() ? 0 : 1;
            }
        });

        if (result.isEmpty())
            return null;

        return new ThreadInfoList(snapshot, result);
    }
    
    public static boolean isThread(ISnapshot snapshot, int objectId) throws SnapshotException
    {
        IObject obj = snapshot.getObject(objectId);
        IClass cls = obj.getClazz();
        if (cls != null)
        {
            String className = cls.getName();
            if (CLASS_THREAD.equals(className))
            {
                return true;
            }
            while (cls.hasSuperClass())
            {
                cls = cls.getSuperClass();
                className = cls.getName();
                if (CLASS_THREAD.equals(className))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private ThreadOverviewNode buildThreadOverviewNode(int objectId, IProgressListener listener) throws SnapshotException
    {
        ThreadOverviewNode result = new ThreadOverviewNode();
        result.threadInfo = ThreadInfoImpl.build(snapshot.getObject(objectId), false, listener);
        
        result.stack = snapshot.getThreadStack(objectId);

        // add all GC roots referenced from this thread stack
        if (result.stack != null)
        {
            IStackFrame[] frames = result.stack.getStackFrames();
            if (frames != null)
            {
                SetInt roots = new SetInt();
                for (IStackFrame IStackFrame : frames)
                {
                    int[] objects = IStackFrame.getLocalObjectsIds();
                    if (objects != null)
                    {
                        for (int i : objects)
                        {
                            roots.add(i);
                        }
                    }
                }
                result.stackRoots = roots.toArray();
            }
        }
        return result;
    }

    private class ThreadOverviewNode
    {
        private ThreadInfoImpl threadInfo;
        private IThreadStack stack;
        private int[] stackRoots;
    }

    private static class ThreadInfoList implements IResultTree, IIconProvider, IDecorator
    {
        ISnapshot snapshot;
        List<ThreadOverviewNode> infos;
        Column[] columns;
        HashMapIntObject<Object> root2element;
        ObjectListResult.Outbound objectList;
        int colMap[];

        public ThreadInfoList(ISnapshot snapshot, List<ThreadOverviewNode> infos)
        {
            this.snapshot = snapshot;
            this.infos = infos;

            List<ThreadInfoImpl> threadInfos = new ArrayList<ThreadInfoImpl>(infos.size());
            ArrayInt roots = new ArrayInt();
            for (ThreadOverviewNode node : infos)
            {
                threadInfos.add(node.threadInfo);
                if (node.stackRoots != null) {
                    roots.addAll(node.stackRoots);
                }
            }
            
            this.columns = ThreadInfoImpl.getUsedColumns(threadInfos).toArray(new Column[0]);
            this.columns[0].decorator(this).comparing(new NoCompareComparator());
            root2element = new HashMapIntObject<Object>();
            objectList = new ObjectListResult.Outbound(snapshot, roots.toArray());
            for (Object o : objectList.getElements())
                root2element.put(objectList.getContext(o).getObjectId(), o);
            // Find matching columns in the object list
            colMap = new int[columns.length];
            Column[] objColumns = objectList.getColumns();
            for (int i = 0; i < columns.length; ++i)
            {
                colMap[i] = i == 0 ? 0 : -1;
                for (int j = 0; j < objColumns.length; ++j)
                {
                    if (columns[i].equals(objColumns[j]))
                    {
                        colMap[i] = j;
                        break;
                    }
                }
            }
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //
                            .addDetailResult(new DetailResultProvider(Messages.ThreadOverviewQuery_ThreadDetails)
                            {
                                @Override
                                public boolean hasResult(Object row)
                                {
                                    if (!(row instanceof ThreadOverviewNode))
                                        return false;
                                    try
                                    {
                                        InspectionAssert.heapFormatIsNot(snapshot, "phd"); //$NON-NLS-1$
                                        return true;
                                    }
                                    catch (UnsupportedOperationException e)
                                    {
                                        return false;
                                    }
                                }

                                @Override
                                public URL getIcon()
                                {
                                    try
                                    {
                                        return SnapshotQuery
                                                        .lookup("thread_details", snapshot).getDescriptor().getIcon(); //$NON-NLS-1$
                                    }
                                    catch (SnapshotException e)
                                    {
                                        return null;
                                    }
                                }

                                @Override
                                public IResult getResult(Object row, IProgressListener listener)
                                                throws SnapshotException
                                {
                                    int threadId = ((ThreadOverviewNode) row).threadInfo.getThreadId();

                                    return SnapshotQuery.lookup("thread_details", snapshot) //$NON-NLS-1$
                                                    .setArgument("threadIds", threadId) //$NON-NLS-1$
                                                    .execute(listener);
                                }

                            }) //
                            .build();
        }

        public Column[] getColumns()
        {
            return columns;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            if (row instanceof ThreadOverviewNode) {
                ThreadOverviewNode info = (ThreadOverviewNode) row;
                return info.threadInfo.getValue(columns[columnIndex]);
            } else {
                if (row instanceof IStackFrame)
                {
                    switch (columnIndex)
                    {
                        case 0:
                            IStackFrame frame = (IStackFrame) row;
                            return frame.getText();
                        default:
                            break;
                    }
                }
                else
                {
                    int newColumnIndex = colMap[columnIndex];
                    if (newColumnIndex >= 0)
                        return objectList.getColumnValue(row, newColumnIndex);
                }
                return null;
            }
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof ThreadOverviewNode) { return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((ThreadOverviewNode) row).threadInfo.getThreadId();
                }
            }; }
            if (row instanceof IStackFrame)
                return null;

            return objectList.getContext(row);
        }

        public URL getIcon(Object row)
        {
            if (row instanceof ThreadOverviewNode)
            {
                return THREAD_ICON_URL;
            }
            else if (row instanceof IStackFrame)
            {
                return null;
            }
            else
                return objectList.getIcon(row);
        }

        public String prefix(Object row)
        {
            if (row instanceof ThreadOverviewNode)
                return null;
            else if (row instanceof IStackFrame)
                return null;
            else
            {
                String prefix = objectList.prefix(row);
                return prefix == null ? Messages.ThreadStackQuery_Label_Local : prefix;
            }
        }

        public String suffix(Object row)
        {
            if (row instanceof ThreadOverviewNode)
                return null;
            else if (row instanceof IStackFrame)
                return null;
            else
                return objectList.suffix(row);
        }

        public List<?> getElements()
        {
            return infos;
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof ThreadOverviewNode)
            {
                IThreadStack stack = ((ThreadOverviewNode) element).stack;
                if (stack == null) return false;
                IStackFrame[] frames = stack.getStackFrames();
                return frames != null && frames.length > 0;
            }
            else if (element instanceof IStackFrame)
            {
                IStackFrame frame = (IStackFrame) element;
                int[] objectIds = frame.getLocalObjectsIds();
                return objectIds != null && objectIds.length > 0;
            }
            return objectList.hasChildren(element);
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof ThreadOverviewNode)
            {
                IStackFrame[] frames = ((ThreadOverviewNode) parent).stack.getStackFrames();
                return Arrays.asList(frames);
            }
            else if (parent instanceof IStackFrame)
            {
                IStackFrame frame = (IStackFrame) parent;
                int[] localIds = frame.getLocalObjectsIds();
                if (localIds != null) { return asList(localIds); }
            }
            return objectList.getChildren(parent);
        }
        
        private List<?> asList(int[] objectIds)
        {
            List<Object> answer = new ArrayList<Object>(objectIds.length);
            for (int id : objectIds)
                answer.add(root2element.get(id));
            return answer;
        }
        
        class NoCompareComparator implements Comparator<Object>
        {
            public int compare(Object o1, Object o2)
            {
                return 0;
            }
        }
    }
}
