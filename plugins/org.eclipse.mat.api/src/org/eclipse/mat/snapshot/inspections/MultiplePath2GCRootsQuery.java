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
package org.eclipse.mat.snapshot.inspections;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Menu.Entry;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

@Name("exclude custom field...")
@CommandName("merge_shortest_paths")
@Category("4|Merge Shortest Paths to GC Roots")
@Icon("/META-INF/icons/mpaths_from_gc.gif")
@Menu( { @Entry(label = "1|with all references", //
                options = "-excludes \"\""), //

                @Entry(label = "2|exclude weak references", //
                options = "-excludes java.lang.ref.WeakReference:referent"), //

                @Entry(label = "3|exclude soft references", //
                options = "-excludes java.lang.ref.SoftReference:referent"), //

                @Entry(label = "4|exclude weak/soft references", //
                options = "-excludes java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent") //
})
public class MultiplePath2GCRootsQuery implements IQuery
{
    public enum Grouping
    {
        FROM_GC_ROOTS("Merge Paths from GC Roots", Icons.getURL("mpaths_from_gc.gif")), //
        FROM_GC_ROOTS_BY_CLASS("Merge Paths from GC Roots on Class", Icons.getURL("mpaths_from_gc_by_class.gif")), //
        FROM_OBJECTS_BY_CLASS("Merge to GC Roots on Class", Icons.getURL("mpaths_to_gc_by_class.gif"));

        String label;
        URL icon;

        private Grouping(String label, URL icon)
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

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public List<String> excludes = Arrays.asList( //
                    new String[] { "java.lang.ref.WeakReference:referent", "java.lang.ref.SoftReference:referent" });

    @Argument(isMandatory = false)
    public Grouping groupBy = Grouping.FROM_GC_ROOTS;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // convert excludes into the required format
        Map<IClass, Set<String>> excludeMap = Path2GCRootsQuery.convert(snapshot, excludes);

        // calculate the shortest path for each object
        IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(objects.getIds(listener),
                        excludeMap);

        Object[] paths = computer.getAllPaths(listener);

        List<int[]> result = new ArrayList<int[]>(paths.length);
        for (int ii = 0; ii < paths.length; ii++)
            result.add((int[]) paths[ii]);

        if (groupBy == null)
            groupBy = Grouping.FROM_GC_ROOTS;

        return create(groupBy, snapshot, result);
    }

    public static Tree create(ISnapshot snapshot, IMultiplePathsFromGCRootsComputer computer, int[] selection)
                    throws SnapshotException
    {
        Object[] paths = computer.getAllPaths(new VoidProgressListener());

        List<int[]> result = new ArrayList<int[]>(paths.length);
        for (int ii = 0; ii < paths.length; ii++)
            result.add((int[]) paths[ii]);

        return selection != null ? new TreeByObjectSelected(snapshot, result, selection) : new TreeByObject(snapshot,
                        result);
    }

    private static Tree create(Grouping groupBy, ISnapshot snapshot, List<int[]> paths)
    {
        switch (groupBy)
        {
            case FROM_GC_ROOTS:
                return new TreeByObject(snapshot, paths);
            case FROM_GC_ROOTS_BY_CLASS:
                return new TreeByClass(snapshot, paths, true);
            case FROM_OBJECTS_BY_CLASS:
                return new TreeByClass(snapshot, paths, false);
        }

        return null;
    }

    public static abstract class Tree implements IResultTree
    {
        protected ISnapshot snapshot;
        protected List<int[]> paths;

        protected Tree(ISnapshot snapshot, List<int[]> paths)
        {
            this.snapshot = snapshot;
            this.paths = paths;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public List<?> getElements()
        {
            return prepare(0, paths);
        }

        protected abstract List<Node> prepare(int level, List<int[]> paths);

        public boolean hasChildren(Object element)
        {
            // to expensive to calculate
            return true;
        }

        public List<?> getChildren(Object parent)
        {
            Node node = (Node) parent;
            return prepare(node.level + 1, node.paths);
        }

        public abstract Grouping getGroupedBy();

        public Tree groupBy(Grouping groupBy)
        {
            if (groupBy == getGroupedBy())
                return this;

            return create(groupBy, snapshot, paths);
        }
    }

    private static class Node
    {
        int objectId;
        int level;
        List<int[]> paths = new ArrayList<int[]>();

        String attribute;
        String label;
        String gcRoots;
        int shallowHeap;
        long refShallowHeap;
        long retainedHeap;

        protected Node(int objectId, int level)
        {
            this.objectId = objectId;
            this.level = level;

            this.shallowHeap = -1;
            this.refShallowHeap = -1;
            this.retainedHeap = -1;
        }

        int[] getReferencedObjects()
        {
            int[] result = new int[paths.size()];

            int ii = 0;
            for (int[] path : paths)
                result[ii++] = path[0];

            return result;
        }
    }

    private static class ClassNode extends Node
    {
        private SetInt distinctObjects;

        private ClassNode(IClass clazz, int level)
        {
            super(clazz.getObjectId(), level);
            this.label = clazz.getName();
        }

        public SetInt getDistinctObjects(boolean mergeFromRoots)
        {
            if (distinctObjects == null) // lazy init
            {
                distinctObjects = new SetInt();
                for (int[] path : paths)
                {
                    int index = mergeFromRoots ? path.length - level - 1 : level;
                    distinctObjects.add(path[index]);
                }
            }

            return distinctObjects;
        }

    }

    /* package */static class TreeByObject extends Tree implements IIconProvider, IDecorator
    {
        private TreeByObject(ISnapshot snapshot, List<int[]> paths)
        {
            super(snapshot, paths);
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Class Name").decorator(this), //
                            new Column("Ref. Objects", int.class), //
                            new Column("Shallow Heap", long.class), //
                            new Column("Ref. Shallow Heap", long.class).sorting(Column.SortDirection.DESC), //
                            new Column("Retained Heap", long.class).noTotals() };
        }

        protected List<Node> prepare(int level, List<int[]> paths)
        {
            HashMapIntObject<Node> id2node = new HashMapIntObject<Node>();

            for (int ii = 0; ii < paths.size(); ii++)
            {
                int[] path = paths.get(ii);
                if (path.length - level > 0)
                {
                    int objectId = path[path.length - level - 1];
                    Node n = id2node.get(objectId);
                    if (n == null)
                        id2node.put(objectId, n = new Node(objectId, level));

                    n.paths.add(path);
                }
            }

            return Arrays.asList(id2node.getAllValues(new Node[0]));
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
                            node.shallowHeap = obj.getUsedHeapSize();
                        }
                        return node.label;
                    case 1:
                        return node.paths.size();
                    case 2:
                        if (node.shallowHeap == -1)
                            node.shallowHeap = snapshot.getHeapSize(node.objectId);
                        return node.shallowHeap;
                    case 3:
                        if (node.refShallowHeap == -1)
                            node.refShallowHeap = snapshot.getHeapSize(node.getReferencedObjects());
                        return node.refShallowHeap;
                    case 4:
                        if (node.retainedHeap == -1)
                            node.retainedHeap = snapshot.getRetainedHeapSize(node.objectId);
                        return node.retainedHeap;
                }
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }

            return null;
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((Node) row).objectId;
                }
            };
        }

        public String prefix(Object row)
        {
            Node n = (Node) row;
            if (n.level > 0 && n.attribute == null)
                fillInAttribute(n);

            return n.attribute;
        }

        private void fillInAttribute(Node node)
        {
            try
            {
                // get parent object -> it doesn't matter which path
                int[] aPath = node.paths.get(0);
                IObject heapObject = snapshot.getObject(aPath[aPath.length - node.level]);

                long parentAddress = snapshot.mapIdToAddress(node.objectId);

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
                node.attribute = s.toString();
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public String suffix(Object row)
        {
            Node node = (Node) row;
            if (node.gcRoots == null && snapshot.isGCRoot(node.objectId))
            {
                try
                {
                    node.gcRoots = GCRootInfo.getTypeSetAsString(snapshot.getGCRootInfo(node.objectId));
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }

            return node.gcRoots;
        }

        public URL getIcon(Object row)
        {
            Node n = (Node) row;
            return n.level == 0 ? Icons.forObject(snapshot, n.objectId) : Icons.outbound(snapshot, n.objectId);
        }

        @Override
        public Grouping getGroupedBy()
        {
            return Grouping.FROM_GC_ROOTS;
        }
    }

    /* package */static class TreeByObjectSelected extends TreeByObject implements ISelectionProvider
    {
        int[] selection;

        private TreeByObjectSelected(ISnapshot snapshot, List<int[]> paths, int[] selection)
        {
            super(snapshot, paths);
            this.selection = selection;
        }

        public boolean isExpanded(Object row)
        {
            Node node = (Node) row;

            if (node.level >= selection.length)
                return false;

            return eval(node);
        }

        public boolean isSelected(Object row)
        {
            Node node = (Node) row;

            if (node.level != selection.length - 1)
                return false;

            return eval(node);
        }

        private boolean eval(Node node)
        {
            boolean selected = true;
            int[] path = node.paths.get(0);
            for (int ii = 0; selected && ii < selection.length && ii < node.level; ii++)
                selected = selection[ii] == path[path.length - ii - 1];

            return selected;
        }
    }

    /* package */static class TreeByClass extends Tree implements IIconProvider
    {
        boolean mergeFromRoots = true;

        private TreeByClass(ISnapshot snapshot, List<int[]> paths, boolean mergeFromRoots)
        {
            super(snapshot, paths);
            this.mergeFromRoots = mergeFromRoots;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Class Name"), //
                            new Column("Objects", int.class).noTotals(), //
                            new Column("Ref. Objects", int.class), //
                            new Column("Ref. Shallow Heap", long.class).sorting(Column.SortDirection.DESC) };
        }

        protected List<Node> prepare(int level, List<int[]> paths)
        {
            try
            {
                HashMapIntObject<ClassNode> id2node = new HashMapIntObject<ClassNode>();

                for (int ii = 0; ii < paths.size(); ii++)
                {
                    int[] path = paths.get(ii);
                    if (path.length - level > 0)
                    {
                        int objectId = path[mergeFromRoots ? path.length - level - 1 : level];
                        IClass clazz = snapshot.getClassOf(objectId);
                        ClassNode n = id2node.get(clazz.getObjectId());
                        if (n == null)
                            id2node.put(clazz.getObjectId(), n = new ClassNode(clazz, level));

                        n.paths.add(path);
                    }
                }

                return Arrays.asList(id2node.getAllValues(new Node[0]));
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public final Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                ClassNode node = (ClassNode) row;

                switch (columnIndex)
                {
                    case 0:
                        return node.label;
                    case 1:
                        return node.getDistinctObjects(mergeFromRoots).size();
                    case 2:
                        return node.paths.size();
                    case 3:
                        if (node.refShallowHeap == -1)
                            node.refShallowHeap = snapshot.getHeapSize(node.getReferencedObjects());
                        return node.refShallowHeap;
                }
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }

            return null;
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObjectSet()
            {
                public int getObjectId()
                {
                    return ((Node) row).objectId;
                }

                public int[] getObjectIds()
                {
                    return ((ClassNode) row).getDistinctObjects(mergeFromRoots).toArray();
                }

                public String getOQL()
                {
                    return null;
                }
            };
        }

        public URL getIcon(Object row)
        {
            Node n = (Node) row;
            if (mergeFromRoots)
                return Icons.CLASS_OUT;
            else
                return n.level == 0 ? Icons.CLASS : Icons.CLASS_IN;
        }

        @Override
        public Grouping getGroupedBy()
        {
            return mergeFromRoots ? Grouping.FROM_GC_ROOTS_BY_CLASS : Grouping.FROM_OBJECTS_BY_CLASS;
        }
    }
}
