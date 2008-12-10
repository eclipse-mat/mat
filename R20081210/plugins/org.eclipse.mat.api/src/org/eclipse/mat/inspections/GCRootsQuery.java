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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapIntObject.Entry;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Name("GC Roots")
@Category("Java Basics")
@Icon("/META-INF/icons/roots.gif")
@Help("List Garbage Collection (GC) Roots by GC Root type.")
public class GCRootsQuery implements IQuery
{
    private static final URL ICON = GCRootsQuery.class.getResource("/META-INF/icons/roots.gif");
    
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] roots = snapshot.getGCRoots();

        HashMapIntObject<HashMapIntObject<ArrayInt>> rootsByType = new HashMapIntObject<HashMapIntObject<ArrayInt>>();

        for (int root : roots)
        {
            GCRootInfo[] info = snapshot.getGCRootInfo(root);
            int classId = snapshot.getClassOf(root).getObjectId();

            for (GCRootInfo rootInfo : info)
            {
                HashMapIntObject<ArrayInt> type = rootsByType.get(rootInfo.getType());
                if (type == null)
                    rootsByType.put(rootInfo.getType(), type = new HashMapIntObject<ArrayInt>());

                ArrayInt byClass = type.get(classId);
                if (byClass == null)
                    type.put(classId, byClass = new ArrayInt());
                byClass.add(root);
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        List<GCType> types = new ArrayList<GCType>();
        for (Iterator<Entry<HashMapIntObject<ArrayInt>>> iter = rootsByType.entries(); iter.hasNext();)
        {
            Entry<HashMapIntObject<ArrayInt>> entry = iter.next();
            GCType type = new GCType(GCRootInfo.getTypeAsString(entry.getKey()));
            types.add(type);

            for (Iterator<Entry<ArrayInt>> iterObjects = entry.getValue().entries(); iterObjects.hasNext();)
            {
                Entry<ArrayInt> entryObjects = iterObjects.next();
                ClassRecord record = new ClassRecord((IClass) snapshot.getObject(entryObjects.getKey()), //
                                entryObjects.getValue().toArray());
                type.records.add(record);

                type.count += record.objectIds.length;
            }

            Collections.sort(type.records);
        }

        Collections.sort(types);

        return new Result(snapshot, roots, types);
    }

    // //////////////////////////////////////////////////////////////
    // internal classes
    // //////////////////////////////////////////////////////////////

    private static class GCType implements Comparable<GCType>
    {
        String name;
        int count;
        List<ClassRecord> records = new ArrayList<ClassRecord>();

        public GCType(String name)
        {
            this.name = name;
        }

        public int compareTo(GCType other)
        {
            return count > other.count ? -1 : count < other.count ? 1 : 0;
        }
    }

    private static class ClassRecord implements Comparable<ClassRecord>
    {
        final int classId;
        final String name;

        final int[] objectIds;

        public ClassRecord(IClass object, int[] objectIds)
        {
            this.classId = object.getObjectId();
            this.name = object.getName();
            this.objectIds = objectIds;
        }

        public int compareTo(ClassRecord o)
        {
            return objectIds.length > o.objectIds.length ? -1 : objectIds.length < o.objectIds.length ? 1 : 0;
        }
    }

    private static class Result implements IResultTree, IIconProvider, IDecorator
    {
        private List<GCType> rootTypes;

        private HashMapIntObject<Object> root2element;
        private ObjectListResult.Outbound objectList;

        public Result(ISnapshot snapshot, int[] roots, List<GCType> rootTypes)
        {
            this.rootTypes = rootTypes;

            root2element = new HashMapIntObject<Object>();
            objectList = new ObjectListResult.Outbound(snapshot, roots);
            for (Object o : objectList.getElements())
                root2element.put(objectList.getContext(o).getObjectId(), o);
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //
                            .setIsPreSortedBy(1, SortDirection.DESC) //
                            .build();
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Class Name").decorator(this), //
                            new Column("Objects", int.class), //
                            new Column("Shallow Heap", long.class).noTotals(), //
                            new Column("Retained Heap", long.class).noTotals() };
        }

        public List<?> getElements()
        {
            return rootTypes;
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof GCType)
                return true;
            if (element instanceof ClassRecord)
                return true;
            return objectList.hasChildren(element);
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof GCType)
                return ((GCType) parent).records;
            if (parent instanceof ClassRecord)
                return asList(((ClassRecord) parent).objectIds);
            return objectList.getChildren(parent);
        }

        private List<?> asList(int[] objectIds)
        {
            List<Object> answer = new ArrayList<Object>(objectIds.length);
            for (int id : objectIds)
                answer.add(root2element.get(id));
            return answer;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            if (row instanceof GCType)
            {
                GCType type = (GCType) row;
                switch (columnIndex)
                {
                    case 0:
                        return type.name;
                    case 1:
                        return type.count;
                    default:
                        return null;
                }
            }
            else if (row instanceof ClassRecord)
            {
                ClassRecord record = (ClassRecord) row;
                switch (columnIndex)
                {
                    case 0:
                        return record.name;
                    case 1:
                        return record.objectIds.length;
                    default:
                        return null;
                }
            }
            else
            {
                switch (columnIndex)
                {
                    case 0:
                        return objectList.getColumnValue(row, columnIndex);
                    case 1:
                        return null;
                    default:
                        return objectList.getColumnValue(row, columnIndex - 1);
                }
            }
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof GCType)
            {
                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return -1;
                    }

                    public int[] getObjectIds()
                    {
                        ArrayInt roots = new ArrayInt();
                        for (ClassRecord record : ((GCType) row).records)
                            roots.addAll(record.objectIds);
                        return roots.toArray();
                    }

                    public String getOQL()
                    {
                        return null;
                    }
                };
            }
            else if (row instanceof ClassRecord)
            {
                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return ((ClassRecord) row).classId;
                    }

                    public int[] getObjectIds()
                    {
                        return ((ClassRecord) row).objectIds;
                    }

                    public String getOQL()
                    {
                        return null;
                    }
                };
            }
            else
            {
                return objectList.getContext(row);
            }
        }

        public String prefix(Object row)
        {
            if (row instanceof GCType)
                return null;
            else if (row instanceof ClassRecord)
                return null;
            else
                return objectList.prefix(row);
        }

        public String suffix(Object row)
        {
            if (row instanceof GCType)
                return null;
            else if (row instanceof ClassRecord)
                return null;
            else
                return objectList.suffix(row);
        }

        public URL getIcon(Object row)
        {
            if (row instanceof GCType)
                return ICON;
            else if (row instanceof ClassRecord)
                return Icons.CLASS;
            else
                return objectList.getIcon(row);
        }
    }

}
