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
package org.eclipse.mat.inspections;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;

@Icon("/META-INF/icons/heapobjects/classloader_obj.gif")
public class ClassLoaderExplorerQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // collect all class loader instances
        HashMapIntObject<Node> classLoader = new HashMapIntObject<Node>();
        Collection<IClass> classes = snapshot.getClassesByName(IClass.JAVA_LANG_CLASSLOADER, true);
        if (classes != null)
            for (IClass clazz : classes)
                for (int objectId : clazz.getObjectIds())
                    classLoader.put(objectId, new Node(objectId));

        // assign defined classes
        for (IClass clazz : snapshot.getClasses())
        {
            Node node = classLoader.get(clazz.getClassLoaderId());
            if (node == null)
            {
                // node can be null, if the class hierarchy information is not
                // contained in the heap dump (e.g. PHD)
                int objectId = clazz.getClassLoaderId();
                node = new Node(objectId);
                classLoader.put(objectId, node);
            }
            if (node.definedClasses == null)
                node.definedClasses = new ArrayList<IClass>();
            node.definedClasses.add(clazz);
            node.instantiatedObjects += clazz.getNumberOfObjects();
        }

        // create hierarchy
        List<Node> nodes = new ArrayList<Node>();
        for (Iterator<Node> iter = classLoader.values(); iter.hasNext();)
        {
            Node node = iter.next();

            if (node.definedClasses != null)
            {
                Collections.sort(node.definedClasses, new Comparator<IClass>()
                {
                    public int compare(IClass o1, IClass o2)
                    {
                        int n1 = o1.getNumberOfObjects();
                        int n2 = o2.getNumberOfObjects();
                        if (n1 > n2)
                            return -1;
                        if (n1 < n2)
                            return 1;
                        return o1.getName().compareTo(o2.getName());
                    }
                });
            }

            IClassLoader cl = (IClassLoader) snapshot.getObject(node.classLoaderId);
            node.name = cl.getClassSpecificName();
            if (node.name == null)
                node.name = cl.getTechnicalName();

            IObject parent = (IObject) cl.resolveValue("parent"); //$NON-NLS-1$
            if (parent != null)
                node.parent = classLoader.get(parent.getObjectId());

            nodes.add(node);
        }

        return new Result(snapshot, nodes);
    }

    private static class Node
    {
        Node parent;
        int classLoaderId;
        String name;
        int instantiatedObjects;

        List<IClass> definedClasses;

        public Node(int classLoaderId)
        {
            this.classLoaderId = classLoaderId;
        }
    }

    /* separate class needed for different context menus */
    private static class Parent
    {
        Node node;

        public Parent(Node node)
        {
            this.node = node;
        }
    }

    private static class Result implements IResultTree, IIconProvider, IDecorator
    {
        ISnapshot snapshot;
        List<Node> nodes;

        public Result(ISnapshot snapshot, List<Node> nodes)
        {
            this.snapshot = snapshot;
            this.nodes = nodes;
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //
                            .addContext(new ContextProvider(Messages.ClassLoaderExplorerQuery_ClassLoader)
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return !(row instanceof IClass) ? Result.this.getContext(row) : null;
                                }
                            }) //
                            .addContext(new ContextProvider(Messages.ClassLoaderExplorerQuery_Class)
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return row instanceof IClass ? Result.this.getContext(row) : null;
                                }
                            }) //
                            .addContext(new ContextProvider(Messages.ClassLoaderExplorerQuery_DefinedClasses)
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    if (row instanceof IClass)
                                        return null;

                                    final Node node = row instanceof Node ? (Node) row : ((Parent) row).node;

                                    if (node.definedClasses == null)
                                        return null;

                                    return new IContextObjectSet()
                                    {
                                        public int getObjectId()
                                        {
                                            return -1;
                                        }

                                        public int[] getObjectIds()
                                        {
                                            int[] answer = new int[node.definedClasses.size()];
                                            int index = 0;
                                            for (IClass clazz : node.definedClasses)
                                                answer[index++] = clazz.getObjectId();
                                            return answer;
                                        }

                                        public String getOQL()
                                        {
                                            return OQL.classesByClassLoaderId(node.classLoaderId);
                                        }
                                    };
                                }
                            }) //
                            .addContext(new ContextProvider(Messages.ClassLoaderExplorerQuery_Instances)
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    if (!(row instanceof IClass))
                                        return null;

                                    final IClass clazz = (IClass) row;
                                    if (clazz.getNumberOfObjects() == 0)
                                        return null;

                                    return new IContextObjectSet()
                                    {
                                        public int getObjectId()
                                        {
                                            return clazz.getObjectId();
                                        }

                                        public int[] getObjectIds()
                                        {
                                            try
                                            {
                                                return clazz.getObjectIds();
                                            }
                                            catch (SnapshotException e)
                                            {
                                                throw new RuntimeException(e);
                                            }
                                        }

                                        public String getOQL()
                                        {
                                            return OQL.forObjectsOfClass(clazz);
                                        }
                                    };
                                }
                            }) //
                            .build();
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.Column_ClassName).decorator(this), //
                            new Column(Messages.ClassLoaderExplorerQuery_Column_DefinedClasses, int.class)
                                            .sorting(Column.SortDirection.DESC), //
                            new Column(Messages.ClassLoaderExplorerQuery_Column_NoInstances, int.class) };
        }

        public List<?> getElements()
        {
            return nodes;
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof IClass)
                return false;
            Node node = element instanceof Node ? (Node) element : ((Parent) element).node;
            return node.parent != null || node.definedClasses != null;
        }

        public List<?> getChildren(Object parent)
        {
            Node node = parent instanceof Node ? (Node) parent : ((Parent) parent).node;

            int size = node.definedClasses != null ? node.definedClasses.size() + 1 : 1;
            List<Object> children = new ArrayList<Object>(size);

            if (node.parent != null)
                children.add(new Parent(node.parent));

            if (node.definedClasses != null)
                children.addAll(node.definedClasses);

            return children;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            if (row instanceof IClass)
            {
                IClass c = (IClass) row;
                if (columnIndex == 0)
                    return c.getName();
                else if (columnIndex == 2)
                    return c.getNumberOfObjects();
            }
            else
            {
                Node n = row instanceof Node ? (Node) row : ((Parent) row).node;
                if (columnIndex == 0)
                    return n.name;
                else if (columnIndex == 1)
                    return n.definedClasses != null ? n.definedClasses.size() : 0;
                else if (columnIndex == 2)
                    return n.instantiatedObjects;
            }

            return null;
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof IClass)
            {
                final IClass clazz = (IClass) row;
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return clazz.getObjectId();
                    }
                };
            }
            else
            {
                final Node node = row instanceof Node ? (Node) row : ((Parent) row).node;
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return node.classLoaderId;
                    }
                };
            }
        }

        public URL getIcon(Object row)
        {
            if (row instanceof IClass)
                return Icons.CLASS;

            Node node = row instanceof Node ? (Node) row : ((Parent) row).node;
            return node.parent != null ? Icons.outbound(snapshot, node.classLoaderId) //
                            : Icons.forObject(snapshot, node.classLoaderId);
        }

        public String prefix(Object row)
        {
            return row instanceof Parent ? "parent" : null; //$NON-NLS-1$
        }

        public String suffix(Object row)
        {
            return null;
        }
    }
}
