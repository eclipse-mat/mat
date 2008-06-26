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

import java.util.Collection;
import java.util.Collections;
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

public class RefinedResultBuilder
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
            throw new NullPointerException("context is null");

        if (subject instanceof IResultTable)
            this.refinedResult = new RefinedTable();
        else if (subject instanceof IResultTree)
            this.refinedResult = new RefinedTree();
        else
            throw new IllegalArgumentException("Unsupported type: " + subject.getClass().getName());

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

        if (refinedResult.metaData.isPreSorted())
        {
            refinedResult.sortColumn = refinedResult.metaData.getPreSortedColumnIndex();
            refinedResult.sortDirection = refinedResult.metaData.getPreSortedDirection();
            refinedResult.resultIsSorted = true;
        }
    }

    protected void setColumnData(IStructuredResult structuredResult)
    {
        Column[] columns = structuredResult.getColumns();
        ContextDerivedData derivedData = refinedResult.context.getContextDerivedData();

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

            if (refinedResult.sortColumn < 0 && columns[ii].getSortDirection() != null)
            {
                refinedResult.sortColumn = ii;
                refinedResult.sortDirection = columns[ii].getSortDirection();
            }
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
        refinedResult.sortColumn = columnIndex;
        refinedResult.sortDirection = direction != null ? direction : Column.SortDirection
                        .defaultFor(refinedResult.columns.get(columnIndex));

        refinedResult.resultIsSorted = refinedResult.metaData.isPreSorted()
                        && refinedResult.sortColumn == refinedResult.metaData.getPreSortedColumnIndex()
                        && refinedResult.sortDirection == refinedResult.metaData.getPreSortedDirection();
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
