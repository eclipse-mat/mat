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
package org.eclipse.mat.internal.snapshot.inspections;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

import com.ibm.icu.text.DecimalFormat;

@CommandName("dominator_tree")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/dominator_tree.gif")
@HelpUrl("/org.eclipse.mat.ui.help/concepts/dominatortree.html")
public class DominatorQuery implements IQuery
{
    public enum Grouping
    {
        NONE(Messages.DominatorQuery_Group_None, Icons.OBJECT_INSTANCE), //
        BY_CLASS(Messages.DominatorQuery_Group_ByClass, Icons.CLASS), //
        BY_CLASSLOADER(Messages.DominatorQuery_Group_ByClassLoader, Icons.CLASSLOADER_INSTANCE), //
        BY_PACKAGE(Messages.DominatorQuery_Group_ByPackage, Icons.PACKAGE);

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

    @Argument(isMandatory = false)
    public Grouping groupBy = Grouping.NONE;

    public Tree execute(IProgressListener listener) throws Exception
    {
        // Force a missing dominator tree to be built
        snapshot.getTopAncestorsInDominatorTree(new int[0], listener);
        return create(new int[] { -1 }, listener);
    }

    protected Tree create(int[] roots, IProgressListener listener) throws SnapshotException
    {
        if (groupBy == null)
            groupBy = Grouping.NONE;

        switch (groupBy)
        {
            case NONE:
                return Factory.create(snapshot, roots, listener);
            case BY_CLASS:
                return Factory.groupByClass(snapshot, roots, listener);
            case BY_CLASSLOADER:
                return Factory.groupByClassLoader(snapshot, roots, listener);
            case BY_PACKAGE:
                return Factory.groupByPackage(snapshot, roots, listener);
        }

        return null;
    }

    // //////////////////////////////////////////////////////////////
    // factory
    // //////////////////////////////////////////////////////////////

    public static class Factory
    {
        public static Tree create(ISnapshot snapshot, int[] roots, IProgressListener listener) throws SnapshotException
        {
            List<Node> elements;

            if (roots.length == 1 && roots[0] == -1)
                elements = DefaultTree.prepare(snapshot, roots[0], listener);
            else
                elements = DefaultTree.prepareSet(snapshot, roots, listener);

            return new DefaultTree(snapshot, roots, elements);
        }

        public static Tree groupByClass(ISnapshot snapshot, int[] roots, IProgressListener listener)
        {
            List<ClassNode> elements;

            if (roots.length == 1 && roots[0] == -1)
                elements = ClassTree.prepare(snapshot, roots, listener);
            else
                elements = ClassTree.prepareSet(snapshot, roots, listener);

            return new ClassTree(snapshot, roots, elements);
        }

        public static Tree groupByClassLoader(ISnapshot snapshot, int[] roots, IProgressListener listener)
                        throws SnapshotException
        {
            List<?> classloader;
            if (roots.length == 1 && roots[0] == -1)
                classloader = ClassLoaderTree.prepare(snapshot, listener);
            else
                classloader = ClassLoaderTree.prepareSet(snapshot, roots, listener);

            return new ClassLoaderTree(snapshot, roots, classloader);
        }

        public static Tree groupByPackage(ISnapshot snapshot, int[] roots, IProgressListener listener)
                        throws SnapshotException
        {
            PackageNode rootNode;

            if (roots.length == 1 && roots[0] == -1)
                rootNode = PackageTree.prepare(snapshot, listener);
            else
                rootNode = PackageTree.prepareSet(snapshot, roots, listener);

            return new PackageTree(snapshot, roots, rootNode);
        }
    }

    // //////////////////////////////////////////////////////////////
    // nodes
    // //////////////////////////////////////////////////////////////

    private static class Node
    {
        int objectId;

        String label;
        long shallowHeap;
        long retainedHeap;

        public Node(int objectId)
        {
            this.objectId = objectId;
            this.shallowHeap = -1;
            this.retainedHeap = -1;
        }
    }

    private static class GroupedNode extends Node
    {
        ArrayInt objects = new ArrayInt();

        private GroupedNode(int objectId)
        {
            super(objectId);
        }
    }

    private static class ClassNode extends GroupedNode
    {
        private ClassNode(int objectId)
        {
            super(objectId);
            shallowHeap = 0;
            retainedHeap = 0;
        }
    }

    private static class PackageNode extends GroupedNode
    {
        Map<String, PackageNode> subPackages = new HashMap<String, PackageNode>();

        private PackageNode(String label)
        {
            super(-1);
            this.label = label;
            shallowHeap = 0;
            retainedHeap = 0;
        }
    }

    // //////////////////////////////////////////////////////////////
    // tree implementations
    // //////////////////////////////////////////////////////////////

    public abstract static class Tree implements IResultTree
    {
        protected ISnapshot snapshot;
        protected int[] roots;
        protected Grouping groupedBy;
        protected double totalHeap;

        public Tree(ISnapshot snapshot, int[] roots, Grouping groupedBy)
        {
            this.snapshot = snapshot;
            this.roots = roots;
            this.groupedBy = groupedBy;
            this.totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();
        }

        public Grouping getGroupedBy()
        {
            return groupedBy;
        }

        public int[] getRoots()
        {
            return roots;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }
    }

    private static class DefaultTree extends Tree implements IIconProvider, IDecorator
    {
        static List<Node> prepareSet(ISnapshot snapshot, int[] roots, IProgressListener listener)
                        throws SnapshotException
        {
            List<Node> nodes = new ArrayList<Node>();
            for (int ii = 0; ii < roots.length; ii++)
            {
                Node node = new Node(roots[ii]);
                node.retainedHeap = snapshot.getRetainedHeapSize(roots[ii]);
                nodes.add(node);
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }

            // these nodes are not sorted (result of top dominators api call)
            Collections.sort(nodes, new Comparator<Node>()
            {
                public int compare(Node o1, Node o2)
                {
                    return o1.retainedHeap < o2.retainedHeap ? 1 : o1.retainedHeap == o2.retainedHeap ? 0 : -1;
                }
            });

            return nodes;
        }

        static List<Node> prepare(ISnapshot snapshot, int id, IProgressListener listener)
        {
            try
            {
                int[] objectIds = snapshot.getImmediateDominatedIds(id);
                if (objectIds == null)
                    return null;

                List<Node> nodes = new ArrayList<Node>(objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                {
                    nodes.add(new Node(objectIds[ii]));
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                return nodes;
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        private List<Node> elements;

        private DefaultTree(ISnapshot snapshot, int[] roots, List<Node> elements)
        {
            super(snapshot, roots, Grouping.NONE);
            this.elements = elements;
        }

        @Override
        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder().setIsPreSortedBy(2, SortDirection.DESC).build();
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.Column_ClassName, String.class).decorator(this), //
                            new Column(Messages.Column_ShallowHeap, int.class).noTotals(), //
                            new Column(Messages.Column_RetainedHeap, long.class).noTotals(), //
                            new Column(Messages.Column_Percentage, double.class)
                                            .formatting(new DecimalFormat("0.00%")).noTotals() }; //$NON-NLS-1$
        }

        public List<?> getElements()
        {
            return elements;
        }

        public boolean hasChildren(Object element)
        {
            // too expensive to check up-front
            return true;
        }

        public List<?> getChildren(Object parent)
        {
            return prepare(snapshot, ((Node) parent).objectId, new VoidProgressListener());
        }

        public Object getColumnValue(Object row, int columnIndex)
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
                        }
                        return node.label;
                    case 1:
                        if (node.shallowHeap == -1)
                            node.shallowHeap = snapshot.getHeapSize(node.objectId);
                        return node.shallowHeap;
                    case 2:
                        if (node.retainedHeap == -1)
                            node.retainedHeap = snapshot.getRetainedHeapSize(node.objectId);
                        return node.retainedHeap;
                    case 3:
                        if (node.retainedHeap == -1)
                            node.retainedHeap = snapshot.getRetainedHeapSize(node.objectId);
                        return node.retainedHeap / totalHeap;
                }

                return null;
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
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

        public URL getIcon(Object row)
        {
            return Icons.forObject(snapshot, ((Node) row).objectId);
        }

        public String prefix(Object row)
        {
            return null;
        }

        public String suffix(Object row)
        {
            try
            {
                Node node = (Node) row;
                GCRootInfo[] roots = snapshot.getGCRootInfo(node.objectId);
                return roots == null ? null : GCRootInfo.getTypeSetAsString(roots);
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ClassTree extends Tree implements IIconProvider
    {
        public static List<ClassNode> prepare(ISnapshot snapshot, int[] objectIds, IProgressListener listener)
        {
            try
            {
                HashMapIntObject<ClassNode> class2node = new HashMapIntObject<ClassNode>();

                for (int ii = 0; ii < objectIds.length; ii++)
                {
                    int[] dominatedIds = snapshot.getImmediateDominatedIds(objectIds[ii]);

                    for (int jj = 0; jj < dominatedIds.length; jj++)
                    {
                        int objectId = dominatedIds[jj];
                        IClass clazz = snapshot.getClassOf(objectId);
                        ClassNode node = class2node.get(clazz.getObjectId());

                        if (node == null)
                        {
                            node = new ClassNode(clazz.getObjectId());
                            node.label = clazz.getName();
                            class2node.put(node.objectId, node);
                        }

                        node.objects.add(objectId);
                        node.shallowHeap += snapshot.getHeapSize(objectId);
                        node.retainedHeap += snapshot.getRetainedHeapSize(objectId);

                        if (listener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();
                    }
                }

                return Arrays.asList(class2node.getAllValues(new ClassNode[0]));
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public static List<ClassNode> prepareSet(ISnapshot snapshot, int[] roots, IProgressListener listener)
        {
            try
            {
                HashMapIntObject<ClassNode> class2node = new HashMapIntObject<ClassNode>();

                for (int jj = 0; jj < roots.length; jj++)
                {
                    int objectId = roots[jj];
                    IClass clazz = snapshot.getClassOf(objectId);
                    ClassNode node = class2node.get(clazz.getObjectId());

                    if (node == null)
                    {
                        node = new ClassNode(clazz.getObjectId());
                        node.label = clazz.getName();
                        class2node.put(node.objectId, node);
                    }

                    node.objects.add(objectId);
                    node.shallowHeap += snapshot.getHeapSize(objectId);
                    node.retainedHeap += snapshot.getRetainedHeapSize(objectId);

                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                return Arrays.asList(class2node.getAllValues(new ClassNode[0]));
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        private List<ClassNode> elements;

        private ClassTree(ISnapshot snapshot, int[] roots, List<ClassNode> elements)
        {
            super(snapshot, roots, Grouping.BY_CLASS);
            this.elements = elements;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.Column_ClassName, String.class), //
                            new Column(Messages.Column_Objects, int.class), //
                            new Column(Messages.Column_ShallowHeap, int.class), //
                            new Column(Messages.Column_RetainedHeap, long.class).sorting(SortDirection.DESC), //
                            new Column(Messages.Column_Percentage, double.class).formatting(new DecimalFormat("0.00%")) }; //$NON-NLS-1$
        }

        public List<?> getElements()
        {
            return elements;
        }

        public boolean hasChildren(Object element)
        {
            // too expensive to check up-front
            return true;
        }

        public List<?> getChildren(Object parent)
        {
            return prepare(snapshot, ((GroupedNode) parent).objects.toArray(), new VoidProgressListener());
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            ClassNode node = (ClassNode) row;

            switch (columnIndex)
            {
                case 0:
                    return node.label;
                case 1:
                    return node.objects.size();
                case 2:
                    return node.shallowHeap;
                case 3:
                    return node.retainedHeap;
                case 4:
                    return node.retainedHeap / totalHeap;
            }

            return null;
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObjectSet()
            {
                public int getObjectId()
                {
                    return ((ClassNode) row).objectId;
                }

                public int[] getObjectIds()
                {
                    return ((ClassNode) row).objects.toArray();
                }

                public String getOQL()
                {
                    return null;
                }
            };
        }

        public URL getIcon(Object row)
        {
            return Icons.CLASS;
        }

    }

    private static class ClassLoaderTree extends Tree implements IIconProvider
    {
        /* package */static List<?> prepareSet(ISnapshot snapshot, int[] roots, IProgressListener listener)
                        throws SnapshotException
        {
            HashMapIntObject<GroupedNode> classLoader2node = new HashMapIntObject<GroupedNode>();

            for (int ii = 0; ii < roots.length; ii++)
            {
                int dominatedId = roots[ii];

                int clId;
                if (snapshot.isClass(dominatedId))
                {
                    IClass cl = (IClass)snapshot.getObject(dominatedId);
                    clId = cl.getClassLoaderId();
                }
                else if (snapshot.isClassLoader(dominatedId))
                {
                    clId = dominatedId;
                }
                else
                {
                    clId = snapshot.getClassOf(dominatedId).getClassLoaderId();
                }

                GroupedNode node = classLoader2node.get(clId);
                if (node == null)
                {
                    node = new GroupedNode(clId);
                    IObject cl = snapshot.getObject(clId);
                    node.label = cl.getClassSpecificName();
                    if (node.label == null)
                        node.label = cl.getTechnicalName();

                    classLoader2node.put(clId, node);
                }

                node.objects.add(dominatedId);
                node.shallowHeap += snapshot.getHeapSize(dominatedId);
                node.retainedHeap += snapshot.getRetainedHeapSize(dominatedId);

                if (ii % 100 == 0 && listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }

            return Arrays.asList(classLoader2node.getAllValues());
        }

        static List<?> prepare(ISnapshot snapshot, IProgressListener listener) throws SnapshotException
        {
            return prepareSet(snapshot, snapshot.getImmediateDominatedIds(-1), listener);
        }

        private List<?> classLoader;

        private ClassLoaderTree(ISnapshot snapshot, int[] roots, List<?> classLoader)
        {
            super(snapshot, roots, Grouping.BY_CLASSLOADER);
            this.classLoader = classLoader;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.Column_ClassLoaderName, String.class), //
                            new Column(Messages.Column_Objects, int.class), //
                            new Column(Messages.Column_ShallowHeap, int.class), //
                            new Column(Messages.Column_RetainedHeap, long.class).sorting(SortDirection.DESC), //
                            new Column(Messages.Column_Percentage, double.class).formatting(new DecimalFormat("0.00%")) }; //$NON-NLS-1$
        }

        public List<?> getElements()
        {
            return classLoader;
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof GroupedNode)
                return true;
            else
                return false;
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof ClassNode)
            {
                return objects((ClassNode) parent);
            }
            else if (parent instanceof GroupedNode)
            {
                return histogram(((GroupedNode) parent).objects);
            }
            else
            {
                return null;
            }
        }

        private List<Node> objects(ClassNode parent)
        {
            List<Node> nodes = new ArrayList<Node>();
            for (IteratorInt iter = parent.objects.iterator(); iter.hasNext();)
                nodes.add(new Node(iter.next()));
            return nodes;
        }

        private List<?> histogram(ArrayInt objectIds)
        {
            try
            {
                HashMapIntObject<ClassNode> class2node = new HashMapIntObject<ClassNode>();

                for (int ii = 0; ii < objectIds.size(); ii++)
                {
                    int objectId = objectIds.get(ii);
                    IClass clazz = snapshot.getClassOf(objectId);
                    ClassNode node = class2node.get(clazz.getObjectId());

                    if (node == null)
                    {
                        node = new ClassNode(clazz.getObjectId());
                        node.label = clazz.getName();
                        class2node.put(node.objectId, node);
                    }

                    node.objects.add(objectId);
                    node.shallowHeap += snapshot.getHeapSize(objectId);
                    node.retainedHeap += snapshot.getRetainedHeapSize(objectId);
                }

                return Arrays.asList(class2node.getAllValues());
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                Node node = (Node) row;

                // reloading is done only for non-class nodes
                switch (columnIndex)
                {
                    case 0:
                        if (node.label == null)
                        {
                            IObject obj = snapshot.getObject(node.objectId);
                            node.label = obj.getDisplayName();
                        }
                        return node.label;

                    case 1:
                        return node instanceof GroupedNode ? ((GroupedNode) node).objects.size() : null;
                    case 2:
                        if (node.shallowHeap == -1)
                            node.shallowHeap = snapshot.getHeapSize(node.objectId);
                        return node.shallowHeap;
                    case 3:
                        if (node.retainedHeap == -1)
                            node.retainedHeap = snapshot.getRetainedHeapSize(node.objectId);
                        return node.retainedHeap;
                    case 4:
                        if (node.retainedHeap == -1)
                            node.retainedHeap = snapshot.getRetainedHeapSize(node.objectId);
                        return node.retainedHeap / totalHeap;
                }

                return null;
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof GroupedNode)
            {

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return ((Node) row).objectId;
                    }

                    public int[] getObjectIds()
                    {
                        return ((GroupedNode) row).objects.toArray();
                    }

                    public String getOQL()
                    {
                        return null;
                    }
                };
            }
            else
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return ((Node) row).objectId;
                    }
                };
            }
        }

        public URL getIcon(Object row)
        {
            if (row instanceof ClassNode)
                return Icons.CLASS;
            else
                return Icons.forObject(snapshot, ((Node) row).objectId);
        }

    }

    private static class PackageTree extends Tree implements IIconProvider
    {
        public static PackageNode prepare(ISnapshot snapshot, IProgressListener listener) throws SnapshotException
        {
            return prepareSet(snapshot, snapshot.getImmediateDominatedIds(-1), listener);
        }

        public static PackageNode prepareSet(ISnapshot snapshot, int[] roots, IProgressListener listener)
                        throws SnapshotException
        {
            PackageNode root = new PackageNode(Messages.DominatorQuery_LabelAll);
            PackageNode current;

            listener.beginTask(Messages.DominatorQuery_Msg_Grouping, roots.length / 100);
            int index = 0;
            for (int dominatorId : roots)
            {
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

                long retainedHeap = snapshot.getRetainedHeapSize(dominatorId);
                long shallowHeap = snapshot.getHeapSize(dominatorId);

                current = root;

                // for classes take their name instead of java.lang.Class
                IClass objClass = snapshot.isClass(dominatorId) ? (IClass) snapshot.getObject(dominatorId) : snapshot
                                .getClassOf(dominatorId);

                String className = objClass.getName();

                StringTokenizer tokenizer = new StringTokenizer(className, "."); //$NON-NLS-1$

                while (tokenizer.hasMoreTokens())
                {
                    String subpack = tokenizer.nextToken();
                    PackageNode childNode = current.subPackages.get(subpack);

                    if (childNode == null)
                    {
                        childNode = new PackageNode(subpack);
                        current.subPackages.put(subpack, childNode);
                    }

                    childNode.objects.add(dominatorId);
                    childNode.retainedHeap += retainedHeap;
                    childNode.shallowHeap += shallowHeap;

                    current = childNode;
                }

                if (++index % 100 == 0)
                    listener.worked(1);
            }

            listener.done();
            return root;
        }

        private PackageNode invisibleRoot;

        private PackageTree(ISnapshot snapshot, int[] roots, PackageNode invisibleRoot)
        {
            super(snapshot, roots, Grouping.BY_PACKAGE);
            this.invisibleRoot = invisibleRoot;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.Column_ClassName, String.class), //
                            new Column(Messages.Column_Objects, int.class), //
                            new Column(Messages.Column_ShallowHeap, int.class), //
                            new Column(Messages.Column_RetainedHeap, long.class).sorting(SortDirection.DESC), //
                            new Column(Messages.Column_Percentage, double.class).formatting(new DecimalFormat("0.00%")) }; //$NON-NLS-1$
        }

        public List<?> getElements()
        {
            return new ArrayList<PackageNode>(invisibleRoot.subPackages.values());
        }

        public boolean hasChildren(Object element)
        {
            return !((PackageNode) element).subPackages.isEmpty();
        }

        public List<?> getChildren(Object parent)
        {
            return new ArrayList<PackageNode>(((PackageNode) parent).subPackages.values());
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            GroupedNode node = (GroupedNode) row;

            switch (columnIndex)
            {
                case 0:
                    return node.label;
                case 1:
                    return node.objects.size();
                case 2:
                    return node.shallowHeap;
                case 3:
                    return node.retainedHeap;
                case 4:
                    return node.retainedHeap / totalHeap;
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
                    return ((GroupedNode) row).objects.toArray();
                }

                public String getOQL()
                {
                    return null;
                }
            };
        }

        public URL getIcon(Object row)
        {
            return ((PackageNode) row).subPackages.isEmpty() ? Icons.CLASS : Icons.PACKAGE;
        }
    }

}
