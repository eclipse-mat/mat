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

package org.eclipse.mat.query.refined;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.ContextDerivedData.DerivedCalculator;
import org.eclipse.mat.query.ContextDerivedData.DerivedColumn;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.refined.RefinedStructuredResult.CalculatedColumnAccessor;
import org.eclipse.mat.query.refined.RefinedStructuredResult.DerivedDataJobDefinition;
import org.eclipse.mat.query.refined.RefinedStructuredResult.ValueAccessor;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

public final class RefinedResultBuilder
{
    private static final class DefaultContextProvider extends ContextProvider
    {
        IStructuredResult result = null;

        private DefaultContextProvider(IStructuredResult result)
        {
            super((String) null);
            this.result = result;
        }

        @Override
        public IContextObject getContext(Object row)
        {
            return result.getContext(row);
        }
    }

    RefinedStructuredResult refinedResult;

    public RefinedResultBuilder(IQueryContext context, IStructuredResult subject)
    {
        if (context == null)
            throw new NullPointerException("context is null"); //$NON-NLS-1$

        if (subject instanceof IResultTable)
            this.refinedResult = new RefinedTable();
        else if (subject instanceof IResultTree)
            this.refinedResult = new RefinedTree();
        else
            throw new IllegalArgumentException(MessageUtil.format(Messages.RefinedResultBuilder_Error_UnsupportedType,
                            subject.getClass().getName()));

        this.refinedResult.subject = subject;

        refinedResult.context = context;
        refinedResult.metaData = subject.getResultMetaData();
        if (refinedResult.metaData == null)
            refinedResult.metaData = new ResultMetaData.Builder().build();

        refinedResult.iconProvider = subject instanceof IIconProvider ? (IIconProvider) subject : IIconProvider.EMPTY;
        refinedResult.selectionProvider = subject instanceof ISelectionProvider ? (ISelectionProvider) subject
                        : ISelectionProvider.EMPTY;

        setColumnData(subject);
        addPreConfiguredDerivedColumns(refinedResult.metaData);
    }

    private void setColumnData(IStructuredResult structuredResult)
    {
        Column[] columns = structuredResult.getColumns();
        ContextDerivedData derivedData = refinedResult.context.getContextDerivedData();

        boolean foundSortColumn = false;

        for (int ii = 0; ii < columns.length; ii++)
        {
            DerivedCalculator calculator = (DerivedCalculator) columns[ii].getData(DerivedCalculator.class);

            ValueAccessor accessor;

            if (calculator != null)
            {
                DerivedColumn derivedColumn = (DerivedColumn) columns[ii].getData(DerivedColumn.class);

                // determine the context provider (as the provider might be
                // wrapped, we cannot attach the provider to the column
                // itself)

                ContextProvider provider = null;

                for (ContextProvider p : refinedResult.metaData.getContextProviders())
                {
                    String ll = derivedData.labelFor(derivedColumn, p);
                    if (columns[ii].getLabel().equals(ll))
                        provider = p;
                }

                if (provider == null)
                    provider = new DefaultContextProvider(structuredResult);

                accessor = new CalculatedColumnAccessor(columns[ii], provider, derivedColumn, calculator);
            }
            else
            {
                accessor = refinedResult.new DefaultValueAccessor(ii);
            }

            refinedResult.addColumn(columns[ii], accessor);

            if (!foundSortColumn && columns[ii].getSortDirection() != null)
            {
                refinedResult.setSortOrder(columns[ii], columns[ii].getSortDirection());
                foundSortColumn = true;
            }
        }

        if (refinedResult.metaData.isPreSorted())
        {
            refinedResult.internalSetSortOrder(refinedResult.metaData.getPreSortedColumnIndex(), // 
                            refinedResult.metaData.getPreSortedDirection(), //
                            true, null);
        }
    }

    private void addPreConfiguredDerivedColumns(ResultMetaData metaData)
    {
        Collection<DerivedOperation> derivedOperations = metaData.getDerivedOperations();
        if (derivedOperations == null)
            return;

        for (DerivedOperation ops : derivedOperations)
            addDefaultContextDerivedColumn(ops);

        for (ContextProvider provider : metaData.getContextProviders())
        {
            for (DerivedOperation ops : provider.getOperations())
                addContextDerivedColumn(provider, ops);
        }
    }

    public int getColumnIndexByName(String columnName)
    {
        for (int ii = 0; ii < refinedResult.columns.size(); ii++)
        {
            Column column = refinedResult.columns.get(ii);
            if (columnName.equals(column.getLabel()))
                return ii;
        }
        return -1;
    }

    public void setSortOrder(int columnIndex, SortDirection direction)
    {
        if (direction == null)
            direction = Column.SortDirection.defaultFor(refinedResult.columns.get(columnIndex));

        boolean isPreSorted = refinedResult.metaData.isPreSorted()
                        && columnIndex == refinedResult.metaData.getPreSortedColumnIndex()
                        && direction == refinedResult.metaData.getPreSortedDirection();

        refinedResult.internalSetSortOrder(columnIndex, direction, isPreSorted, null);
    }

    public void setSortOrder(int[] indices, SortDirection[] directions)
    {
        if (indices.length != directions.length)
            throw new IllegalArgumentException(Messages.RefinedResultBuilder_Error_ColumnsSorting);

        if (indices.length == 1)
        {
            setSortOrder(indices[0], directions[0]);
        }
        else
        {
            Column.SortDirection direction = directions[0];
            if (direction == null)
                direction = Column.SortDirection.defaultFor(refinedResult.columns.get(indices[0]));

            List<Comparator<Object>> comparators = new ArrayList<Comparator<Object>>(indices.length);

            for (int ii = 0; ii < indices.length; ii++)
                comparators.add(refinedResult.buildComparator(indices[ii], directions[ii]));

            Comparator<Object> cmp = new RefinedStructuredResult.MultiColumnComparator(comparators);
            refinedResult.internalSetSortOrder(indices[0], direction, false, cmp);
        }
    }

    public void addDefaultContextDerivedColumn(DerivedOperation operation)
    {
        addContextDerivedColumn(new DefaultContextProvider(refinedResult.subject), operation);
    }

    public void addContextDerivedColumn(ContextProvider provider, DerivedOperation operation)
    {
        DerivedColumn derivedColumn = refinedResult.context.getContextDerivedData().lookup(operation);
        refinedResult.addDerivedDataColumn(provider, derivedColumn);
        refinedResult.jobs.add(new DerivedDataJobDefinition(provider, operation));
    }

    public void setFilter(int columnIndex, String criteria) throws IllegalArgumentException
    {
        refinedResult.filters.get(columnIndex).setCriteria(criteria);
    }

    public List<Column> getColumns()
    {
        return Collections.unmodifiableList(refinedResult.columns);
    }

    public void setInlineRetainedSizeCalculation(boolean inline)
    {
        refinedResult.inlineJobs = inline;
    }

    public RefinedStructuredResult build()
    {
        // ensure builder is not accidently reused
        RefinedStructuredResult result = refinedResult;
        refinedResult = null;

        result.totalsCalculator = TotalsCalculator.create(result);
        result.init();

        return result;
    }
}
