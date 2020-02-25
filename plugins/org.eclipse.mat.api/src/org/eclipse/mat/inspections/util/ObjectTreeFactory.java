/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - use com.ibm.icu for formatting
 *******************************************************************************/
package org.eclipse.mat.inspections.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.Icons;

import com.ibm.icu.text.NumberFormat;

public final class ObjectTreeFactory
{
    public static class TreePathBuilder
    {
        private Node root = new Node(-1);
        private Node branch = null;
        private long base;
        private Boolean incoming;

        public TreePathBuilder()
        {}

        public TreePathBuilder(long base)
        {
            this.base = base;
        }

        public TreePathBuilder setIsIncoming()
        {
            incoming = Boolean.TRUE;
            return this;
        }

        public TreePathBuilder setIsOutgoing()
        {
            incoming = Boolean.FALSE;
            return this;
        }

        public TreePathBuilder addBranch(int objectId)
        {
            branch = root.getOrCreateChild(objectId);
            return this;
        }

        public TreePathBuilder addChild(int objectId, boolean select)
        {
            if (branch == null)
                throw new RuntimeException(Messages.ObjectTreeFactory_ErrorMsg_addChild);

            branch.isExpanded = true;

            branch = branch.getOrCreateChild(objectId);
            branch.isSelected = select;

            return this;
        }

        public TreePathBuilder addChildren(int[] objectIds)
        {
            if (branch == null)
                throw new RuntimeException(Messages.ObjectTreeFactory_ErrorMsg_addChildren);

            branch.isExpanded = true;

            for (int id : objectIds)
                branch = branch.getOrCreateChild(id);

            return this;
        }

        public TreePathBuilder addSibling(int objectId, boolean select)
        {
            if (branch == null)
                throw new RuntimeException(Messages.ObjectTreeFactory_ErrorMsg_addChild);

            branch.isExpanded = true;
            // Select the new sibling child
            branch.getOrCreateChild(objectId).isSelected = select;

            return this;
        }

        public IResultTree build(ISnapshot snapshot)
        {
            return new NodeResult(snapshot, root, base, false, incoming);
        }
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private static class Node
    {
        int ownId;
        String attributeName;
        List<Node> children;
        boolean isExpanded;
        boolean isSelected;

        // private HashMapIntObject id2child;

        private Node(int ownId)
        {
            this.ownId = ownId;
        }

        /* package */Node getOrCreateChild(int childId)
        {
            Node child = null;

            if (children == null)
            {
                children = new ArrayList<Node>();
            }
            else
            {
                for (Node c : children)
                {
                    if (c.ownId == childId)
                    {
                        child = c;
                        break;
                    }
                }
            }

            if (child == null)
            {
                child = new Node(childId);
                children.add(child);
            }

            return child;
        }
    }

    private static class NodeResult implements IResultTree, IIconProvider, ISelectionProvider
    {
        private static final Column COL_HEAP = new Column(Messages.Column_ShallowHeap, Bytes.class);
        private static final Column COL_RETAINED = new Column(Messages.Column_RetainedHeap, Bytes.class);
        private static Column col_percent()
        {
            // Return a new column each time as the formatter is not thread-safe
            NumberFormat nf = NumberFormat.getPercentInstance();
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return new Column(Messages.ObjectTreeFactory_Column_Percentage, double.class).formatting(nf);
        }

        private ISnapshot snapshot;
        private Node invisibleRoot;
        private long base;
        private boolean decorateWithAttributeName;
        private Boolean incoming;

        private NodeResult(ISnapshot snapshot, Node root, long base, boolean decorateWithAttributeName, Boolean incoming)
        {
            this.snapshot = snapshot;
            this.invisibleRoot = root;
            this.base = base;
            this.decorateWithAttributeName = decorateWithAttributeName;
            this.incoming = incoming;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public Column[] getColumns()
        {
            Column classNameCol = new Column(Messages.Column_ClassName, String.class);
            if (decorateWithAttributeName)
                classNameCol.decorator(new IDecorator()
                {
                    public String prefix(Object row)
                    {
                        return ((Node) row).attributeName;
                    }

                    public String suffix(Object row)
                    {
                        return null;
                    }
                });

            if (base > 0)
                return new Column[] { classNameCol, COL_HEAP, COL_RETAINED, col_percent() };
            else
                return new Column[] { classNameCol, COL_HEAP, COL_RETAINED };
        }

        public List<?> getElements()
        {
            return invisibleRoot.children;
        }

        public boolean hasChildren(Object element)
        {
            return ((Node) element).children != null;
        }

        public List<?> getChildren(Object parent)
        {
            return ((Node) parent).children;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                Node node = (Node) row;

                switch (columnIndex)
                {
                    case 0:
                        return snapshot.getObject(node.ownId).getDisplayName();
                    case 1:
                        return new Bytes(snapshot.getHeapSize(node.ownId));
                    case 2:
                        return new Bytes(snapshot.getRetainedHeapSize(node.ownId));
                    case 3:
                        long size = snapshot.getRetainedHeapSize(node.ownId);
                        return ((double) size / (double) base);
                }

                return null;

            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public URL getIcon(Object row)
        {
            if (incoming == null)
            {
                return Icons.forObject(snapshot, ((Node) row).ownId);
            }
            else if (incoming.booleanValue())
            {
                boolean isFirstLevel = invisibleRoot.children.contains(row);
                return isFirstLevel ? Icons.forObject(snapshot, ((Node) row).ownId) // 
                                : Icons.inbound(snapshot, ((Node) row).ownId);
            }
            else
            {
                return Icons.outbound(snapshot, ((Node) row).ownId);
            }
        }

        public boolean isExpanded(Object row)
        {
            return ((Node) row).isExpanded;
        }

        public boolean isSelected(Object row)
        {
            return ((Node) row).isSelected;
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((Node) row).ownId;
                }
            };
        }
    }
}
