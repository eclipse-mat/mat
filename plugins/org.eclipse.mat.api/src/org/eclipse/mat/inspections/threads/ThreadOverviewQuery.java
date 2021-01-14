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
 *    IBM Corporation - optional columns, cancellation
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

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
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo.Type;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@CommandName("thread_overview")
@Icon("/META-INF/icons/threads.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingthreads.html")
@Subject("java.lang.Thread")
public class ThreadOverviewQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false, flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;
    
    private static URL THREAD_ICON_URL = Icons.getURL("thread.gif"); //$NON-NLS-1$
    private static URL STACK_FRAME_ICON_URL = Icons.getURL("stack_frame.gif"); //$NON-NLS-1$
    public static final String CLASS_THREAD = "java.lang.Thread"; //$NON-NLS-1$

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
                    if (listener.isCanceled())
                        break;
                    if (isThread(snapshot, objectId)) {
                        result.add(buildThreadOverviewNode(objectId, listener));
                    }
                }
            }
        }
        else
        {
            Collection<IClass> classes = snapshot.getClassesByName(CLASS_THREAD, true);
            if (classes != null)
            {
                for (IClass clasz : classes)
                {
                    for (int id : clasz.getObjectIds())
                    {
                        if (listener.isCanceled())
                            break;
                        result.add(buildThreadOverviewNode(id, listener));
                    }
                }
            }
            // Add objects marked as a GCRoot THREAD_OBJ
            for (int objectId : snapshot.getGCRoots())
            {
                GCRootInfo gcs[] = snapshot.getGCRootInfo(objectId);
                if (gcs != null)
                {
                    for (GCRootInfo gc : gcs)
                    {
                        // Sometime PHD files have threads marked as objects
                        if (gc.getType() == Type.THREAD_OBJ)
                        {
                            if (isThread(snapshot, objectId)) {
                                result.add(buildThreadOverviewNode(objectId, listener));
                            }
                        }
                    }
                }
                if (listener.isCanceled())
                    break;
            }
        }

        Collections.sort(result, new Comparator<ThreadOverviewNode>()
        {
            public int compare(ThreadOverviewNode o1, ThreadOverviewNode o2)
            {
                return o1.threadInfo.getRetainedHeap().getValue() > o2.threadInfo.getRetainedHeap().getValue() ? -1
                                : o1.threadInfo.getRetainedHeap().getValue() < o2.threadInfo.getRetainedHeap().getValue() ? 1 : 
                                  Long.compare(o1.threadInfo.getThreadObject().getObjectAddress(), o2.threadInfo.getThreadObject().getObjectAddress());
            }
        });

        // Remove duplicates - easy as already sorted
        ThreadOverviewNode prev = null;
        for (Iterator<ThreadOverviewNode>it = result.iterator(); it.hasNext();)
        {
            ThreadOverviewNode node = it.next();
            if (prev != null && node.threadInfo.getThreadId() == prev.threadInfo.getThreadId())
            {
                it.remove();
            }
            else
            {
                prev = node;
            }
        }

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
            GCRootInfo gcs[] = snapshot.getGCRootInfo(objectId);
            if (gcs != null)
            {
                // Reset class name
                className = obj.getClazz().getName();
                for (GCRootInfo gc : gcs)
                {
                    // Sometime PHD files have threads marked as objects
                    if (gc.getType() == Type.THREAD_OBJ && "java.lang.Object".equals(className)) //$NON-NLS-1$
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
    
    private static class ThreadStackFrameNode
    {
        private ThreadOverviewNode threadOverviewNode;
        private IStackFrame stackFrame;
        private int depth;
        private boolean firstNonNativeFrame;
        private int objectId;
    }
    
    private static class ThreadStackFrameLocalNode
    {
        private ThreadStackFrameNode threadStackFrameNode;
        private int objectId;
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
                    Column threadCol = columns[i];
                    Column listCol = objColumns[j];
                    // Don't worry about matching totals or sort direction.
                    if (threadCol.getLabel().equals(listCol.getLabel())
                        && Objects.equals(threadCol.getFormatter(), listCol.getFormatter())
                        && threadCol.getType().equals(listCol.getType()))
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
                if (row instanceof ThreadStackFrameNode)
                {
                    switch (columnIndex)
                    {
                        case 0:
                            IStackFrame frame = ((ThreadStackFrameNode) row).stackFrame;
                            return frame.getText();
                        default:
                            break;
                    }
                }
                else
                {
                    int newColumnIndex = colMap[columnIndex];
                    if (newColumnIndex >= 0)
                    {
                        if (row instanceof ThreadStackFrameLocalNode)
                        {
                            row = root2element.get(((ThreadStackFrameLocalNode)row).objectId);
                        }
                        return objectList.getColumnValue(row, newColumnIndex);
                    }
                }
                return null;
            }
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof ThreadOverviewNode) {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return ((ThreadOverviewNode) row).threadInfo.getThreadId();
                    }
                };
            }
            if (row instanceof ThreadStackFrameNode)
            {
                if (((ThreadStackFrameNode)row).objectId != -1)
                {
                    return new IContextObject()
                    {
                        public int getObjectId()
                        {
                            return ((ThreadStackFrameNode) row).objectId;
                        }
                    };
                }
                return null;
            }

            if (row instanceof ThreadStackFrameLocalNode)
            {
                return objectList.getContext(root2element.get(((ThreadStackFrameLocalNode)row).objectId));
            }
            else
            {
                return objectList.getContext(row);
            }
        }

        public URL getIcon(Object row)
        {
            if (row instanceof ThreadOverviewNode)
            {
                return THREAD_ICON_URL;
            }
            else if (row instanceof ThreadStackFrameNode)
            {
                return STACK_FRAME_ICON_URL;
            }
            else
            {
                if (row instanceof ThreadStackFrameLocalNode)
                {
                    row = root2element.get(((ThreadStackFrameLocalNode)row).objectId);
                }
                return objectList.getIcon(row);
            }
        }

        public String prefix(Object row)
        {
            if (row instanceof ThreadOverviewNode)
                return null;
            else if (row instanceof ThreadStackFrameNode)
                return null;
            else
            {
                ThreadStackFrameLocalNode tsfmln = null;
                if (row instanceof ThreadStackFrameLocalNode)
                {
                    tsfmln = (ThreadStackFrameLocalNode) row;
                    row = root2element.get(tsfmln.objectId);
                }
                String prefix = objectList.prefix(row);
                if (prefix == null)
                {
                    // If this is the top stack frame and this object is a GC
                    // Root, then check if it's of the type BUSY_MONITOR with
                    // the context ID of this thread, and if so, note that this
                    // is the object the thread is blocked on
                    if (tsfmln != null && tsfmln.threadStackFrameNode != null)
                    {
                        boolean topFrame = tsfmln.threadStackFrameNode.firstNonNativeFrame || tsfmln.threadStackFrameNode.depth == 0;
                        ThreadInfoImpl threadInfo = tsfmln.threadStackFrameNode.threadOverviewNode.threadInfo;
                        int objectId = objectList.getContext(row).getObjectId();
                        try
                        {
                            GCRootInfo[] gcRootInfos = snapshot.getGCRootInfo(objectId);
                            if (gcRootInfos != null)
                            {
                                for (GCRootInfo gcRootInfo : gcRootInfos)
                                {
                                    if (gcRootInfo.getType() == GCRootInfo.Type.BUSY_MONITOR
                                                    && gcRootInfo.getContextAddress() != 0
                                                    && gcRootInfo.getContextId() == threadInfo.getThreadId())
                                    { 
                                        return topFrame ? Messages.ThreadStackQuery_Label_Local_Probably_Blocked_On
                                                        : Messages.ThreadStackQuery_Label_Local_Busy_Monitor;
                                    }
                                    if (gcRootInfo.getType() == GCRootInfo.Type.BUSY_MONITOR
                                                    && gcRootInfo.getContextAddress() == 0)
                                    {
                                        // HPROF doesn't have a context address
                                        return topFrame ? Messages.ThreadStackQuery_Label_Local_Possibly_Blocked_On
                                                        : Messages.ThreadStackQuery_Label_Local_Possible_Busy_Monitor;
                                    }
                                }
                            }
                            List<NamedReference> refs = threadInfo
                                            .getThreadObject().getOutboundReferences();
                            for (NamedReference ref : refs)
                            {
                                if (ref.getObjectId() != objectId)
                                    continue;
                                if (ref instanceof ThreadToLocalReference)
                                {
                                    ThreadToLocalReference tlr = (ThreadToLocalReference)ref;
                                    for (GCRootInfo gcRootInfo : tlr.getGcRootInfo())
                                    {
                                        if (gcRootInfo.getType() == GCRootInfo.Type.BUSY_MONITOR)
                                        {
                                            return topFrame ? Messages.ThreadStackQuery_Label_Local_Probably_Blocked_On
                                                            : Messages.ThreadStackQuery_Label_Local_Busy_Monitor;
                                        }
                                    }
                                }
                            }
                        }
                        catch (SnapshotException e)
                        {
                            // Something is wrong with the GC root information,
                            // so just skip this
                        }
                    }
                    return Messages.ThreadStackQuery_Label_Local;
                }
                else
                {
                    return prefix;
                }
            }
        }

        public String suffix(Object row)
        {
            if (row instanceof ThreadOverviewNode)
                return null;
            else if (row instanceof ThreadStackFrameNode)
                return null;
            else
            {
                if (row instanceof ThreadStackFrameLocalNode)
                {
                    row = root2element.get(((ThreadStackFrameLocalNode)row).objectId);
                }
                return objectList.suffix(row);
            }
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
            else if (element instanceof ThreadStackFrameNode)
            {
                IStackFrame frame = ((ThreadStackFrameNode) element).stackFrame;
                int[] objectIds = frame.getLocalObjectsIds();
                return objectIds != null && objectIds.length > 0;
            }
            else
            {
                if (element instanceof ThreadStackFrameLocalNode)
                {
                    element = root2element.get(((ThreadStackFrameLocalNode)element).objectId);
                }
                return objectList.hasChildren(element);
            }
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof ThreadOverviewNode)
            {
                ThreadOverviewNode ton = (ThreadOverviewNode) parent;
                IStackFrame[] frames = ton.stack.getStackFrames();
                final int numFrames = frames.length;
                List<ThreadStackFrameNode> stackFrameNodes = new ArrayList<ThreadStackFrameNode>(numFrames);
                boolean foundNonNativeFrame = false;

                int frameIds[] = frameIds(ton);
                for (int i = 0; i < numFrames; i++)
                {
                    IStackFrame frame = frames[i];
                    ThreadStackFrameNode tsfn = new ThreadStackFrameNode();
                    tsfn.stackFrame = frame;
                    tsfn.depth = i;
                    tsfn.firstNonNativeFrame = false;
                    if (!foundNonNativeFrame)
                    {
                        String frameText = frame.getText();
                        if (frameText != null && !frameText.contains("Native Method")) //$NON-NLS-1$
                        {
                            tsfn.firstNonNativeFrame = true;
                            foundNonNativeFrame = true;
                        }
                    }
                    tsfn.threadOverviewNode = ton;
                    tsfn.objectId = frameIds[tsfn.depth];
                    if (tsfn.objectId == -1)
                    {
                        // Add the class from the class name of the stack frame
                        tsfn.objectId = frameId(frame);
                    }
                    stackFrameNodes.add(tsfn);
                }
                return stackFrameNodes;
            }
            else if (parent instanceof ThreadStackFrameNode)
            {
                ThreadStackFrameNode tsfn = (ThreadStackFrameNode) parent;
                IStackFrame frame = tsfn.stackFrame;
                int[] localIds = frame.getLocalObjectsIds();
                if (localIds != null)
                {
                    List<ThreadStackFrameLocalNode> stackFrameLocals = new ArrayList<ThreadStackFrameLocalNode>(localIds.length);
                    for (int localId : localIds)
                    {
                        ThreadStackFrameLocalNode tsfln = new ThreadStackFrameLocalNode();
                        tsfln.objectId = localId;
                        tsfln.threadStackFrameNode = tsfn;
                        stackFrameLocals.add(tsfln);
                    }
                    return stackFrameLocals;
                }
                else
                {
                    return null;
                }
            }
            else
            {
                if (parent instanceof ThreadStackFrameLocalNode)
                {
                    parent = root2element.get(((ThreadStackFrameLocalNode)parent).objectId);
                }
                return objectList.getChildren(parent);
            }
        }

        private int[] frameIds(ThreadOverviewNode ton)
        {
            final int numFrames = ton.stack.getStackFrames().length;
            int frameIds[] = new int[numFrames];
            Arrays.fill(frameIds, -1);
            IObject to = ton.threadInfo.getThreadObject();
            for (NamedReference ref : to.getOutboundReferences())
            {
                if (ref instanceof ThreadToLocalReference)
                {
                    ThreadToLocalReference tlr = (ThreadToLocalReference)ref;
                    for (GCRootInfo gcRootInfo : tlr.getGcRootInfo())
                    {
                        if (gcRootInfo.getType() == GCRootInfo.Type.JAVA_STACK_FRAME)
                        {
                            try
                            {
                                Object fn = tlr.getObject().resolveValue("frameNumber"); //$NON-NLS-1$
                                if (fn instanceof Integer)
                                {
                                    int f = (Integer)fn;
                                    if (f >= 0 && f < numFrames)
                                    {
                                        frameIds[f] = tlr.getObjectId(); 
                                    }
                                }
                            }
                            catch (SnapshotException e)
                            {
                                // Ignore
                            }
                        }
                    }
                }
            }
            return frameIds;
        }

        /**
         * Return the id of the class associated with this stack frame.
         * @param frm
         * @return the id, or -1 if not found
         */
        private int frameId(IStackFrame frm)
        {
            String fm = frm.getText();
            if (fm != null)
            {
                // Extract the class name from the full method name
                // at package.name.Classname.method(Arguments) Classname.java(13)
                fm = fm.replaceFirst("\\s*at\\s+([^(]+)\\.[^.(]+.*", "$1"); //$NON-NLS-1$//$NON-NLS-2$
                try
                {
                    Collection<IClass>clss = snapshot.getClassesByName(fm, false);
                    // If unambiguous which class
                    if (clss != null && clss.size() == 1)
                    {
                        for (IClass cls : clss)
                        {
                            return cls.getObjectId();
                        }
                    }
                }
                catch (SnapshotException e)
                {
                }
            }
            return -1;
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
