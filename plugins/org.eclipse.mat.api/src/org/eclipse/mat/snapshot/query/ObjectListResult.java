/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - fix FindBugs warnings
 *******************************************************************************/
package org.eclipse.mat.snapshot.query;

import java.lang.reflect.Array;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;

/**
 * Describes a set of objects returned from a query, as a tree of inbound or outbound nodes.
 */
public final class ObjectListResult
{
    /**
     * Helper class which describes a tree of objects by inbound references.
     */
    public static class Inbound extends Tree
    {
        /**
         * Construct a inbound references tree
         * @param snapshot the snapshot
         * @param objectIds the set of objects to form roots of the trees
         */
        public Inbound(ISnapshot snapshot, int[] objectIds)
        {
            super(snapshot, objectIds);
        }

        protected int[] children(Node node) throws SnapshotException
        {
            return snapshot.getInboundRefererIds(node.objectId);
        }

        protected void fillInAttribute(LinkedNode node) throws SnapshotException
        {
            IObject heapObject = snapshot.getObject(node.objectId);
            long parentAddress = snapshot.mapIdToAddress(node.parent.objectId);
            node.attribute = extractAttribute(heapObject, parentAddress);
        }

        /**
         * Get the URL for a row of the tree.
         * Returns either an arrow item or the base icon if no children.
         * @return the URL of the icon
         */
        public URL getIcon(Object row)
        {
            if (row instanceof LinkedNode)
                return Icons.inbound(snapshot, ((Node) row).objectId);
            else
                return Icons.forObject(snapshot, ((Node) row).objectId);
        }
    }

    /**
     * Helper class which describes a tree of objects by outbound references.
     */
    public static class Outbound extends Tree
    {
        /**
         * Construct a outbound references tree
         * @param snapshot the snapshot
         * @param objectIds the set of objects to form roots of the trees
         */
        public Outbound(ISnapshot snapshot, int[] objectIds)
        {
            super(snapshot, objectIds);
        }

        protected int[] children(Node node) throws SnapshotException
        {
            return snapshot.getOutboundReferentIds(node.objectId);
        }

        protected void fillInAttribute(LinkedNode node) throws SnapshotException
        {
            IObject heapObject = snapshot.getObject(node.parent.objectId);
            long parentAddress = snapshot.mapIdToAddress(node.objectId);
            node.attribute = extractAttribute(heapObject, parentAddress);
        }

        /**
         * Get the URL for a row of the tree.
         * Returns either an arrow item or the base icon if no children.
         * @return the URL of the icon
         */
        public URL getIcon(Object row)
        {
            return Icons.outbound(snapshot, ((Node) row).objectId);
        }
    }

    private abstract static class Tree implements IResultTree, IIconProvider, IDecorator
    {
        protected ISnapshot snapshot;
        private List<?> objects;

        /**
         * Build a tree from objects as roots
         * @param snapshot
         * @param objectIds
         */
        public Tree(ISnapshot snapshot, int[] objectIds)
        {
            this.snapshot = snapshot;
            this.objects = new LazyList(objectIds);
        }

        /**
         * Enhance the tree with extra data.
         */
        public final ResultMetaData getResultMetaData()
        {
            return null;
        }

        /**
         * Get the columns, which are the class name, the shallow heap and the retained heap.
         */
        public final Column[] getColumns()
        {
            return new Column[] { new Column(Messages.Column_ClassName).decorator(this), //
                            new Column(Messages.Column_ShallowHeap, Bytes.class).noTotals(), //
                            new Column(Messages.Column_RetainedHeap, Bytes.class).noTotals() };
        }

        /**
         * Get the actual rows.
         */
        public final List<?> getElements()
        {
            return objects;
        }

        private final List<?> asList(Node parent, int[] ids)
        {
            List<LinkedNode> objects = new ArrayList<LinkedNode>(ids.length);
            for (int ii = 0; ii < ids.length; ii++)
                objects.add(new LinkedNode(parent, ids[ii]));
            return objects;
        }

        public final List<?> getChildren(Object parent)
        {
            try
            {
                Node node = (Node) parent;
                int[] outbounds = children(node);
                return asList(node, outbounds);
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        protected abstract int[] children(Node node) throws SnapshotException;

        public final boolean hasChildren(Object element)
        {
            return true;
        }

        public final Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                Node node = (Node) row;

                switch (columnIndex)
                {
                    case 0:
                        if (node.label == null)
                        {
                            IObject obj = snapshot.getObject(node.objectId);
                            node.label = obj.getDisplayName();
                            node.shallowHeap = new Bytes(obj.getUsedHeapSize());
                        }
                        return node.label;
                    case 1:
                        if (node.shallowHeap.getValue() == -1)
                            node.shallowHeap = new Bytes(snapshot.getHeapSize(node.objectId));
                        return node.shallowHeap;
                    case 2:
                        if (node.retainedHeap.getValue() == -1)
                            node.retainedHeap = new Bytes(snapshot.getRetainedHeapSize(node.objectId));
                        return node.retainedHeap;
                }
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }

            return null;
        }

        public final IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((Node) row).objectId;
                }
            };
        }

        public final String prefix(Object row)
        {
            if (row instanceof LinkedNode)
            {
                LinkedNode node = (LinkedNode) row;
                if (node.attribute == null)
                {
                    try
                    {
                        fillInAttribute(node);
                    }
                    catch (SnapshotException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                return node.attribute;
            }
            else
            {
                return null;
            }
        }

        protected abstract void fillInAttribute(LinkedNode node) throws SnapshotException;

        public final String suffix(Object row)
        {
            Node node = (Node) row;
            if (node.gcRoots == null)
            {
                try
                {
                    GCRootInfo[] gc = snapshot.getGCRootInfo(node.objectId);
                    node.gcRoots = gc != null ? GCRootInfo.getTypeSetAsString(gc) : Node.NOT_A_GC_ROOT;
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }

            return node.gcRoots == Node.NOT_A_GC_ROOT ? null : node.gcRoots;
        }

        protected String extractAttribute(IObject heapObject, long parentAddress)
        {
            StringBuilder s = new StringBuilder(64);

            List<NamedReference> refs = heapObject.getOutboundReferences();
            for (NamedReference reference : refs)
            {
                if (reference.getObjectAddress() == parentAddress)
                {
                    if (s.length() > 0)
                        s.append(", "); //$NON-NLS-1$
                    s.append(reference.getName());
                }
            }

            return s.toString();
        }
    }

    // //////////////////////////////////////////////////////////////
    // helper classes
    // //////////////////////////////////////////////////////////////

    private static class Node
    {
        /** A string guaranteed not to be equal by identity to any other String */
        public static final String NOT_A_GC_ROOT = new String("$ not a gc root $"); //$NON-NLS-1$

        int objectId;
        String label;
        String gcRoots;
        Bytes shallowHeap;
        Bytes retainedHeap;

        private Node(int objectId)
        {
            this.objectId = objectId;
            this.shallowHeap = new Bytes(-1);
            this.retainedHeap = new Bytes(-1);
        }
    }

    private static class LinkedNode extends Node
    {
        Node parent;
        String attribute;

        private LinkedNode(Node parent, int objectId)
        {
            super(objectId);
            this.parent = parent;
        }
    }

    private static class LazyList implements List<Node>, RandomAccess
    {
        int created = 0;

        private int[] objectIds;
        private Node[] elements;

        private LazyList(int[] objectIds)
        {
            this.objectIds = objectIds;
            this.elements = new Node[objectIds.length];
        }

        public Node get(int index)
        {
            if (index < 0 || index >= objectIds.length)
                throw new ArrayIndexOutOfBoundsException(index);

            if (elements[index] == null)
            {
                elements[index] = new Node(objectIds[index]);
                created++;
            }

            return elements[index];
        }

        public Node set(int index, Node node)
        {
            if (index < 0 || index >= objectIds.length)
                throw new ArrayIndexOutOfBoundsException(index);

            Node retValue = elements[index];
            if (retValue == null)
                retValue = new Node(objectIds[index]);

            elements[index] = node;
            objectIds[index] = node.objectId;

            return retValue;
        }

        public void add(int index, Node element)
        {
            throw new UnsupportedOperationException();
        }

        public boolean add(Node o)
        {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends Node> c)
        {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(int index, Collection<? extends Node> c)
        {
            throw new UnsupportedOperationException();
        }

        public void clear()
        {
            throw new UnsupportedOperationException();
        }

        public boolean contains(Object o)
        {
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        public int indexOf(Object o)
        {
            throw new UnsupportedOperationException();
        }

        public boolean isEmpty()
        {
            return objectIds.length == 0;
        }

        public Iterator<Node> iterator()
        {
            return new Iterator<Node>()
            {
                int index = 0;

                public boolean hasNext()
                {
                    return index < objectIds.length;
                }

                public Node next()
                {
                    if (index >= objectIds.length)
                        throw new NoSuchElementException();
                    return get(index++);
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public int lastIndexOf(Object o)
        {
            throw new UnsupportedOperationException();
        }

        public ListIterator<Node> listIterator()
        {
            return listIterator(0);
        }

        public ListIterator<Node> listIterator(final int index)
        {
            return new ListIterator<Node>()
            {
                int pos = index;
                int last = -1;

                public void add(Node o)
                {
                    throw new UnsupportedOperationException();
                }

                public boolean hasNext()
                {
                    return pos < elements.length;
                }

                public boolean hasPrevious()
                {
                    return pos > 0;
                }

                public Node next()
                {
                    if (pos >= elements.length)
                        throw new NoSuchElementException();
                    Node n = get(pos);
                    last = pos++;
                    return n;
                }

                public int nextIndex()
                {
                    return pos + 1;
                }

                public Node previous()
                {
                    Node n = get(pos);
                    last = pos--;
                    return n;
                }

                public int previousIndex()
                {
                    return pos - 1;
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }

                public void set(Node o)
                {
                    if (last == -1)
                        throw new IllegalStateException();

                    LazyList.this.set(last, o);
                }

            };
        }

        public Node remove(int index)
        {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o)
        {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        public int size()
        {
            return objectIds.length;
        }

        public List<Node> subList(int fromIndex, int toIndex)
        {
            throw new UnsupportedOperationException();
        }

        public Object[] toArray()
        {
            createNodesIfNecessary();

            Object[] copy = new Object[elements.length];
            System.arraycopy(elements, 0, copy, 0, elements.length);
            return copy;
        }

        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a)
        {
            createNodesIfNecessary();

            if (a.length < elements.length)
                a = (T[]) Array.newInstance(a.getClass().getComponentType(), elements.length);
            System.arraycopy(elements, 0, a, 0, elements.length);
            if (a.length > elements.length)
                a[elements.length] = null;
            return a;
        }

        private void createNodesIfNecessary()
        {
            // for creation of nodes (if necessary)
            if (created != objectIds.length)
            {
                for (int ii = 0; ii < elements.length; ii++)
                    if (elements[ii] == null)
                        get(ii);
            }
        }
    }

    private ObjectListResult()
    {}

}
