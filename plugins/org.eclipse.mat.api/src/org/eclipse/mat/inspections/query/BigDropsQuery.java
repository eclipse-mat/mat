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
package org.eclipse.mat.inspections.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


@Name("Big Drops in Dominator Tree")
@Category("Leak Identification")
@Help("Display memory accumulation points in the dominator tree.\n\n"
                + "Displayed are objects where the difference in the retained size of the parent"
                + " and the children is very big. These are places where the memory of many small"
                + " objects is accumulated under one object. The first \"interesting\" dominator "
                + "of the accumulation point is also shown.")
public class BigDropsQuery implements IQuery, IResultTree
{
    private final static int ROOT_ID = -1;
    private final static String ROOT_LABEL = "<ROOT>";

    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.CLASS_NAME_PATTERN, isMandatory = false, flag = "skip")
    @Help("Specifies which dominators of the big drop object (accumulation point) should be skipped")
    public Pattern pattern = Pattern.compile("java.*|com\\.sun.\\.*");

    // @Argument(isMandatory = false, flag = "t")
    // @Help("Differences in the retained sizes of the parent and the children
    // bigger than this threshold are considered big drops")
    public int thresholdPercent = 1;

    BigDropEntry rootEntry;

    public IResult execute(IProgressListener listener) throws Exception
    {
        buildTree(thresholdPercent * snapshot.getSnapshotInfo().getUsedHeapSize() / 100, listener);
        return this;
    }

    private void buildTree(long threshold, IProgressListener listener) throws SnapshotException
    {
        /*
         * The algorithm implies that the children are sorted by their retained
         * size descending. This is how the data is stored in the index
         */

        StackEntry entry = new StackEntry(ROOT_ID, snapshot.getSnapshotInfo().getUsedHeapSize(), snapshot
                        .getImmediateDominatedIds(ROOT_ID), 0);
        Stack<StackEntry> stack = new Stack<StackEntry>();
        stack.push(entry);

        rootEntry = new BigDropEntry(ROOT_ID, "<root>", snapshot.getSnapshotInfo().getUsedHeapSize(),
                        entry.children.length, ROOT_ID, ROOT_LABEL, snapshot.getSnapshotInfo().getUsedHeapSize());
        Stack<BigDropEntry> dropsStack = new Stack<BigDropEntry>();
        dropsStack.push(rootEntry);

        int iterations = 0;
        while (stack.size() > 0)
        {
            /* check for cancellation */
            iterations++;
            if ((iterations & 0xfff) == 0)
            {
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            }
            // peek at the arguments
            entry = stack.peek();

            if (entry.nextChild == 0) // first time we see the parent
            {
                if (entry.children.length == 0) // a leaf node
                {
                    if (entry.parentSize > threshold)
                    {
                        // add to result
                        if (stack.size() > 1) // not a top-level
                        {
                            // construct the new entry
                            IObject obj = snapshot.getObject(entry.parentId);
                            IObject dominatorObject = getDominator(obj);
                            BigDropEntry newBigDrop = null;
                            if (dominatorObject == null) // add to the root
                            {
                                newBigDrop = new BigDropEntry(obj.getObjectId(), obj.getDisplayName(), obj
                                                .getRetainedHeapSize(), entry.children.length, ROOT_ID, ROOT_LABEL,
                                                rootEntry.objectRetainedSize);
                            }
                            else
                            {
                                newBigDrop = new BigDropEntry(obj.getObjectId(), obj.getDisplayName(), obj
                                                .getRetainedHeapSize(), entry.children.length, dominatorObject
                                                .getObjectId(), dominatorObject.getDisplayName(), dominatorObject
                                                .getRetainedHeapSize());
                            }
                            BigDropEntry dropParent = dropsStack.peek();
                            dropParent.children.add(newBigDrop);
                        }

                    }
                    // the leaf node is processed
                    stack.pop();
                    continue;

                }
                else
                // there are some children -> check for big drop
                {
                    long childRetainedSize = snapshot.getRetainedHeapSize(entry.children[entry.nextChild]);
                    if (entry.parentSize - childRetainedSize > threshold)
                    {
                        // add to result
                        if (stack.size() > 1) // not a top-level
                        {
                            // construct the new entry
                            IObject obj = snapshot.getObject(entry.parentId);
                            IObject dominatorObject = getDominator(obj);
                            BigDropEntry newBigDrop = null;
                            if (dominatorObject == null)
                            {
                                newBigDrop = new BigDropEntry(obj.getObjectId(), obj.getDisplayName(), obj
                                                .getRetainedHeapSize(), entry.children.length, ROOT_ID, ROOT_LABEL,
                                                rootEntry.objectRetainedSize);
                            }
                            else
                            {
                                newBigDrop = new BigDropEntry(obj.getObjectId(), obj.getDisplayName(), obj
                                                .getRetainedHeapSize(), entry.children.length, dominatorObject
                                                .getObjectId(), dominatorObject.getDisplayName(), dominatorObject
                                                .getRetainedHeapSize());
                            }
                            BigDropEntry dropParent = dropsStack.peek();
                            dropParent.children.add(newBigDrop);
                            dropsStack.push(newBigDrop);
                        }
                    }
                    else
                    // skip this object
                    {
                        stack.pop();
                        stack.push(new StackEntry(entry.children[entry.nextChild], childRetainedSize, snapshot
                                        .getImmediateDominatedIds(entry.children[entry.nextChild]), 0));
                        continue;
                    }
                }
            }

            /*
             * at this point there should be printed parent with some children
             */
            if (entry.nextChild < entry.children.length)
            {
                long childRetainedSize = snapshot.getRetainedHeapSize(entry.children[entry.nextChild]);
                if (childRetainedSize > threshold)
                {
                    // one of the children is big enough to get attention

                    stack.push(new StackEntry(entry.children[entry.nextChild], childRetainedSize, snapshot
                                    .getImmediateDominatedIds(entry.children[entry.nextChild]), 0));
                    entry.nextChild++;
                }
                else
                {
                    // pop this parent - it's processed
                    stack.pop();
                    if (entry.parentId == dropsStack.peek().objectId)
                    {
                        dropsStack.pop();
                    }
                }
            }
            else
            // pop this parent - it's processed
            {
                stack.pop();
                if (entry.parentId == dropsStack.peek().objectId)
                {
                    dropsStack.pop();
                }
            }

        }
    }

    private static class StackEntry
    {
        int parentId;
        int[] children;
        int nextChild;
        long parentSize;

        public StackEntry(int parentId, long parentSize, int[] children, int nextChild)
        {
            this.parentId = parentId;
            this.parentSize = parentSize;
            this.children = children;
            this.nextChild = nextChild;
        }
    }

    /*
     * returns the first dominator not matching the pattern or null (for the
     * <ROOT>)
     */
    private IObject getDominator(IObject object) throws SnapshotException
    {
        while (pattern.matcher(object.getTechnicalName()).matches())
        {
            int dominatorId = snapshot.getImmediateDominatorId(object.getObjectId());
            if (dominatorId == ROOT_ID) { return null; }
            object = snapshot.getObject(dominatorId);
        }
        return object;
    }

    // //////////////////////////////////////////////////////////////
    //
    // IResultTree implementation
    //
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public Column[] getColumns()
    {
        return new Column[] { new Column("Accumulation Point"), //
                        new Column("Acc.Pt. Size", Long.class), //
                        new Column("# Children", Long.class), //
                        new Column("Dominator"), //
                        new Column("Dom. Retained Size", Long.class) };
    }

    public List<?> getChildren(Object parent)
    {
        return ((BigDropEntry) parent).children;
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        BigDropEntry element = (BigDropEntry) row;
        switch (columnIndex)
        {
            case 0:
                return element.objectLabel;
            case 1:
                return element.objectRetainedSize;
            case 2:
                return element.numberOfChildren;
            case 3:
                return element.dominatorLabel;
            case 4:
                return element.dominatorRetainedSize;
        }

        return null;
    }

    public List<?> getElements()
    {
        return rootEntry.children;
    }

    public boolean hasChildren(Object parent)
    {
        return ((BigDropEntry) parent).children.size() > 0;
    }

    public IContextObject getContext(final Object row)
    {
        return new IContextObject()
        {
            public int getObjectId()
            {
                return ((BigDropEntry) row).objectId;
            }
        };
    }

    public ContextProvider[] getContextProviders()
    {
        return new ContextProvider[] { new ContextProvider("Accumulation Point")
        {
            @Override
            public IContextObject getContext(Object row)
            {
                return getAccumulationPoint(row);
            }

        }, new ContextProvider("Dominator")
        {
            @Override
            public IContextObject getContext(Object row)
            {
                return getDominator(row);
            }

        } };
    }

    IContextObject getAccumulationPoint(final Object row)
    {
        return new IContextObject()
        {
            public int getObjectId()
            {
                return ((BigDropEntry) row).objectId;
            }
        };
    }

    IContextObject getDominator(final Object row)
    {
        return new IContextObject()
        {
            public int getObjectId()
            {
                return ((BigDropEntry) row).dominatorId;
            }
        };
    }

    // ////////////////////////////////////////////
    //
    // Some structure to hold the data
    //
    // //////////////////////////////////////////////

    public static class BigDropEntry
    {
        int objectId;
        String objectLabel;
        long objectRetainedSize;
        int numberOfChildren;
        int dominatorId;
        String dominatorLabel;
        long dominatorRetainedSize;

        List<BigDropEntry> children = new ArrayList<BigDropEntry>(1);

        public BigDropEntry(int objectId, String objectLabel, long objectRetainedSize, int numberOfChildren,
                        int dominatorId, String dominatorLabel, long dominatorRetainedSize)
        {
            this.objectId = objectId;
            this.objectLabel = objectLabel;
            this.objectRetainedSize = objectRetainedSize;
            this.numberOfChildren = numberOfChildren;
            this.dominatorId = dominatorId;
            this.dominatorLabel = dominatorLabel;
            this.dominatorRetainedSize = dominatorRetainedSize;
        }

    }

}
