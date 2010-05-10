/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("thread_stacks")
@Icon("/META-INF/icons/threads.gif")
public class ThreadStackQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED, isMandatory = false)
    public IHeapObjectArgument objects;

    private static URL THREAD_ICON_URL = ThreadStackQuery.class.getResource("/META-INF/icons/threads.gif"); //$NON-NLS-1$

    public IResult execute(IProgressListener listener) throws Exception
    {
        List<IThreadStack> stacks = new ArrayList<IThreadStack>();
        SetInt roots = new SetInt();

        if (objects == null) // take all threads by default
        {
            Collection<IClass> classes = snapshot.getClassesByName("java.lang.Thread", true); //$NON-NLS-1$
            if (classes != null)
                for (IClass clazz : classes)
                {
                    int[] objectIds = clazz.getObjectIds();
                    addThreadStacks(objectIds, stacks, roots);
                }
        }
        else
        // work with the provided objects
        {
            for (int[] objectIds : objects)
            {
                addThreadStacks(objectIds, stacks, roots);
            }
        }

        return new Result(stacks, roots.toArray());
    }

    private void addThreadStacks(int[] objectIds, List<IThreadStack> stacks, SetInt roots) throws SnapshotException
    {
        for (int objectId : objectIds)
        {
            IThreadStack stack = snapshot.getThreadStack(objectId);

            // add all GC roots referenced from this thread stack
            if (stack != null)
            {
                stacks.add(stack);

                IStackFrame[] frames = stack.getStackFrames();
                if (frames != null)
                {
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
                    } // for (IStackFrame IStackFrame : frames)
                } // if (frames != null)
            }// if (stack != null)
        } // for (int objectId : objectIds)
    }

    private class Result implements IResultTree, IIconProvider, IDecorator
    {
        List<IThreadStack> callstacks;

        private HashMapIntObject<Object> root2element;
        private ObjectListResult.Outbound objectList;

        public Result(List<IThreadStack> callstacks, int[] roots)
        {
            this.callstacks = callstacks;

            root2element = new HashMapIntObject<Object>();
            objectList = new ObjectListResult.Outbound(snapshot, roots);
            for (Object o : objectList.getElements())
                root2element.put(objectList.getContext(o).getObjectId(), o);
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof IThreadStack)
            {
                IStackFrame[] frames = ((IThreadStack) parent).getStackFrames();
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

        public List<?> getElements()
        {
            return callstacks;
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof IThreadStack)
            {
                IStackFrame[] frames = ((IThreadStack) element).getStackFrames();
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

        public Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                if (row instanceof IThreadStack)
                {
                    IThreadStack stack = (IThreadStack) row;
                    switch (columnIndex)
                    {
                        case 0:
                            IObject threadObject = snapshot.getObject(stack.getThreadId());
                            return threadObject.getDisplayName();
                        case 1:
                            return snapshot.getHeapSize(stack.getThreadId());
                        case 2:
                            return snapshot.getRetainedHeapSize(stack.getThreadId());

                        default:
                            break;
                    }
                }
                else if (row instanceof IStackFrame)
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
                    return objectList.getColumnValue(row, columnIndex);
                }
            }
            catch (SnapshotException e)
            {

            }
            return null;
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.ThreadStackQuery_Column_ObjectStackFrame).decorator(this).comparing(
                                            new NoCompareComparator()), //
                            new Column(Messages.Column_ShallowHeap, long.class).noTotals(), //
                            new Column(Messages.Column_RetainedHeap, long.class).noTotals().sorting(SortDirection.DESC) };
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof IThreadStack) { return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((IThreadStack) row).getThreadId();
                }
            }; }
            if (row instanceof IStackFrame)
                return null;

            return objectList.getContext(row);
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public URL getIcon(Object row)
        {
            if (row instanceof IThreadStack)
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
            if (row instanceof IThreadStack)
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
            if (row instanceof IThreadStack)
                return null;
            else if (row instanceof IStackFrame)
                return null;
            else
                return objectList.suffix(row);
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
