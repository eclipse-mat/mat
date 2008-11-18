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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.mat.SnapshotException;
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
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.report.ITestResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.ObjectComparators;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;

@Name("Duplicate Classes")
@Category("Java Basics")
@Help("Extract classes loaded multiple times.")
public class DuplicatedClassesQuery implements IQuery, IResultTree, IIconProvider, ITestResult, IDecorator,
                ISelectionProvider
{
    @Argument
    public ISnapshot snapshot;

    private List<List<IClass>> problems;

    // //////////////////////////////////////////////////////////////
    // IQuery
    // //////////////////////////////////////////////////////////////

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        IClass[] allClasses = snapshot.getClasses().toArray(new IClass[0]);
        int length = allClasses.length;
        listener.beginTask("Checking for duplicate Classes", length / 100);

        Arrays.sort(allClasses, ObjectComparators.getComparatorForTechnicalNameAscending());

        problems = new ArrayList<List<IClass>>();

        String previousName = allClasses[0].getName();

        for (int ii = 1; ii < length; ii++)
        {
            if (previousName.equals(allClasses[ii].getName()))
            {
                List<IClass> duplicates = new ArrayList<IClass>();
                problems.add(duplicates);

                for (ii--; ii < length && allClasses[ii].getName().equals(previousName); ii++)
                {
                    duplicates.add(allClasses[ii]);
                }
            }

            if (ii < length)
                previousName = allClasses[ii].getName();

            if (ii % 100 == 0)
                listener.worked(1);

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        return this;
    }

    // //////////////////////////////////////////////////////////////
    // ITestResult
    // //////////////////////////////////////////////////////////////

    public Status getStatus()
    {
        return problems.isEmpty() ? Status.SUCCESS : Status.WARNING;
    }

    // //////////////////////////////////////////////////////////////
    // IResultTree
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public Column[] getColumns()
    {
        return new Column[] { new Column("Name").decorator(this), //
                        new Column("Count", int.class).sorting(Column.SortDirection.DESC), //
                        new Column("Defined Classes", int.class).noTotals(), //
                        new Column("No. of Instances", int.class).noTotals() };
    }

    public List<?> getElements()
    {
        return problems;
    }

    public boolean hasChildren(Object parent)
    {
        return parent instanceof List;
    }

    public List<?> getChildren(Object parent)
    {
        if (parent instanceof List)
            return (List<?>) parent;
        else
            return null;
    }

    public Object getColumnValue(Object element, int columnIndex)
    {
        if (element instanceof List)
        {
            switch (columnIndex)
            {
                case 0:
                    return ((IClass) ((List<?>) element).get(0)).getName();
                case 1:
                    return ((List<?>) element).size();
            }
        }
        else if (element instanceof IClass)
        {
            if (columnIndex != 1)
            {
                try
                {
                    IClassLoader classLoader = (IClassLoader) snapshot.getObject(((IClass) element).getClassLoaderId());

                    switch (columnIndex)
                    {
                        case 0:
                            String loaderName = classLoader.getClassSpecificName();

                            if (loaderName != null)
                                return loaderName + " @ 0x" + Long.toHexString(classLoader.getObjectAddress());
                            else
                                return classLoader.getTechnicalName();
                        case 1:
                            return null;
                        case 2:
                            return classLoader.getDefinedClasses().size();
                        case 3:
                            int instantiatedObjects = 0;
                            for (IClass clazz : classLoader.getDefinedClasses())
                                instantiatedObjects += clazz.getNumberOfObjects();
                            return instantiatedObjects;
                    }

                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(MessageFormat.format("ClassLoader of 0x{0} not found",
                                    new Object[] { Long.toHexString(((IClass) element).getObjectAddress()) }), e);
                }
            }
        }

        return null;
    }

    public String prefix(Object row)
    {
        return null;
    }

    public String suffix(Object row)
    {
        if (!(row instanceof IClass))
            return null;

        try
        {
            GCRootInfo[] roots = ((IClass) row).getGCRootInfo();
            return roots != null ? GCRootInfo.getTypeSetAsString(roots) : null;
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    public URL getIcon(Object element)
    {
        if (element instanceof List)
            return Icons.CLASS;
        else if (element instanceof IClass)
            return Icons.forObject(snapshot, ((IClass) element).getClassLoaderId());
        else
            return null;
    }

    public IContextObject getContext(final Object element)
    {
        if (element instanceof List)
        {
            return new IContextObjectSet()
            {

                public int getObjectId()
                {
                    return -1;
                }

                @SuppressWarnings("unchecked")
                public int[] getObjectIds()
                {
                    List<IClass> classes = (List<IClass>) element; // unchecked
                    int[] answer = new int[classes.size()];
                    int ii = 0;
                    for (IClass c : classes)
                        answer[ii++] = c.getObjectId();
                    return answer;
                }

                public String getOQL()
                {
                    return OQL.forObjectIds(getObjectIds());
                }

            };
        }
        else if (element instanceof IClass)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((IClass) element).getClassLoaderId();
                }
            };
        }
        else
        {
            return null;
        }
    }

    public boolean isExpanded(Object row)
    {
        return problems.isEmpty() ? false : row == problems.get(0);
    }

    public boolean isSelected(Object row)
    {
        return false;
    }

}
