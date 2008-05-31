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
package org.eclipse.mat.impl.result;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;


public abstract class RefinedStructuredResult implements IStructuredResult, ISelectionProvider, IIconProvider,
                Filter.FilterChangeListener
{
    // //////////////////////////////////////////////////////////////
    // factory
    // //////////////////////////////////////////////////////////////

    public static class Builder
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

        public Builder(ISnapshot snapshot, IStructuredResult subject)
        {
            if (subject instanceof Histogram)
                this.refinedResult = new RefinedHistogramTable();
            else if (subject instanceof Histogram.ClassLoaderTree)
                this.refinedResult = new RefinedHistogramClassLoaderTree();
            else if (subject instanceof IResultTable)
                this.refinedResult = new RefinedTable();
            else if (subject instanceof IResultTree)
                this.refinedResult = new RefinedTree();
            else
                throw new IllegalArgumentException("Unsupported type: " + subject.getClass().getName());

            this.refinedResult.subject = subject;

            refinedResult.snapshot = snapshot;
            refinedResult.metaData = subject.getResultMetaData();
            if (refinedResult.metaData == null)
                refinedResult.metaData = new ResultMetaData.Builder().build();

            refinedResult.iconProvider = subject instanceof IIconProvider ? (IIconProvider) subject
                            : IIconProvider.EMPTY;
            refinedResult.selectionProvider = subject instanceof ISelectionProvider ? (ISelectionProvider) subject
                            : ISelectionProvider.EMPTY;

            setColumnData(subject);
            addPreConfiguredRetainedSizeColumns(refinedResult.metaData);

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

            for (int ii = 0; ii < columns.length; ii++)
            {
                RetainedSizeCalculator calculator = (RetainedSizeCalculator) columns[ii]
                                .getData(RetainedSizeCalculator.class);

                ValueAccessor accessor;

                if (calculator != null)
                {
                    // determine the context provider (as the provider might be
                    // wrapped, we cannot attach the provider to the column
                    // itself)

                    ContextProvider provider = null;

                    for (ContextProvider p : refinedResult.metaData.getContextProviders())
                    {
                        if (columns[ii].getLabel().equals(p.getColumnLabel()))
                            provider = p;
                    }

                    if (provider == null)
                        provider = new DefaultContextProvider(structuredResult);

                    accessor = new RetainedSizeAccessor(columns[ii], provider, calculator);
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

        private void addPreConfiguredRetainedSizeColumns(ResultMetaData metaData)
        {
            if ((metaData.isShowPreciseRetainedSize() || metaData.isShowApproximateRetainedSize()))
                addDefaultRetainedSizeColumn(metaData.isShowApproximateRetainedSize());

            for (ContextProvider provider : metaData.getContextProviders())
            {
                if (provider.isDefaultShowPrecise() || provider.isDefaultShowApproximation())
                    addRetainedSizeColumn(provider, provider.isDefaultShowApproximation());
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

        public void addDefaultRetainedSizeColumn(boolean approximate)
        {
            addRetainedSizeColumn(new DefaultContextProvider(refinedResult.subject), approximate);

        }

        public void addRetainedSizeColumn(ContextProvider p, boolean approximate)
        {
            refinedResult.addRetainedSizeColumn(p);
            refinedResult.jobs.add(new CalculationJobDefinition(p, approximate));
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

    public static class CalculationJobDefinition
    {
        private ContextProvider provider;
        private boolean approximation;

        public CalculationJobDefinition(ContextProvider provider, boolean approximate)
        {
            this.provider = provider;
            this.approximation = approximate;
        }

        public ContextProvider getContextProvider()
        {
            return provider;
        }

        public boolean isApproximation()
        {
            return approximation;
        }

        public void setApproximation(boolean approximation)
        {
            this.approximation = approximation;
        }
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private interface ValueAccessor
    {
        Object getValue(Object row);
    }

    /* package */static final class FilteredList<E> extends ArrayList<E>
    {
        private static final long serialVersionUID = 1L;

        private int filteredCount;

        public int getFilteredCount()
        {
            return filteredCount;
        }

        public void setFilteredCount(int filteredCount)
        {
            this.filteredCount = filteredCount;
        }
    }

    /* package */static final class NaturalComparator implements Comparator<Object>
    {
        private RefinedStructuredResult refinedResult;
        private int sortColumn;

        public NaturalComparator(RefinedStructuredResult refinedResult, int sortColumn)
        {
            this.refinedResult = refinedResult;
            this.sortColumn = sortColumn;
        }

        @SuppressWarnings("unchecked")
        public int compare(Object o1, Object o2)
        {
            Object d1 = refinedResult.getColumnValue(o1, sortColumn);
            Object d2 = refinedResult.getColumnValue(o2, sortColumn);

            if (d1 == null)
                return d2 == null ? 0 : -1;
            else if (d2 == null)
                return 1;
            else if (d1 instanceof Comparable)
                return ((Comparable) d1).compareTo(d2);
            else
                return d1.toString().compareTo(d2.toString());
        }
    }

    // //////////////////////////////////////////////////////////////
    // member variables
    // //////////////////////////////////////////////////////////////

    /** original result wrapped by this refined result */
    protected IStructuredResult subject;

    /** original meta data */
    protected ResultMetaData metaData;

    /** source snapshot -> to calculate retained sizes */
    protected ISnapshot snapshot;

    /** wrapped selection provider */
    protected ISelectionProvider selectionProvider;

    /** wrapped icon provider */
    protected IIconProvider iconProvider;

    /** totals calculator */
    protected TotalsCalculator totalsCalculator;

    protected List<Column> columns = new ArrayList<Column>();
    protected List<ValueAccessor> accessors = new ArrayList<ValueAccessor>();
    protected List<Filter> filters = new ArrayList<Filter>();
    protected List<IDecorator> decorators = new ArrayList<IDecorator>();

    protected boolean resultIsSorted = false;
    protected int sortColumn = -1;
    protected Column.SortDirection sortDirection;

    protected boolean inlineJobs = false;
    protected List<CalculationJobDefinition> jobs = new ArrayList<CalculationJobDefinition>();

    // //////////////////////////////////////////////////////////////
    // initialization
    // //////////////////////////////////////////////////////////////

    public RefinedStructuredResult()
    {
        super();
    }

    protected void init()
    {}

    private void addColumn(Column column, ValueAccessor accessor)
    {
        columns.add(column);
        filters.add(Filter.Factory.build(snapshot, column, this));
        accessors.add(accessor);
        decorators.add(column.getDecorator());
    }

    // //////////////////////////////////////////////////////////////
    // decoration
    // //////////////////////////////////////////////////////////////

    public boolean isDecorated(int columnIndex)
    {
        return decorators.get(columnIndex) != null;
    }

    // //////////////////////////////////////////////////////////////
    // filter
    // //////////////////////////////////////////////////////////////

    public Filter[] getFilter()
    {
        return filters.toArray(new Filter[0]);
    }

    public boolean hasActiveFilter()
    {
        for (Filter f : filters)
        {
            if (f.isActive())
                return true;
        }
        return false;
    }

    public int getFilteredCount(List<?> elements)
    {
        return elements instanceof FilteredList ? ((FilteredList<?>) elements).getFilteredCount() : 0;
    }

    public void filterChanged(Filter filter)
    {}

    // //////////////////////////////////////////////////////////////
    // sorting
    // //////////////////////////////////////////////////////////////

    /** -1 if the result is not sorted */
    public int getSortColumn()
    {
        return sortColumn;
    }

    public Column.SortDirection getSortDirection()
    {
        return sortDirection;
    }

    public void setSortOrder(Column queryColumn, Column.SortDirection direction)
    {
        sortColumn = columns.indexOf(queryColumn);
        sortDirection = direction;
        resultIsSorted = false;
    }

    @SuppressWarnings("unchecked")
    public void sort(List<?> elements)
    {
        Comparator<Object> cmp = (Comparator<Object>) columns.get(sortColumn).getComparator();
        if (cmp == null)
            cmp = new NaturalComparator(this, sortColumn);

        if (sortDirection == Column.SortDirection.DESC)
            cmp = Collections.reverseOrder(cmp);

        Collections.sort(elements, cmp);
    }

    // //////////////////////////////////////////////////////////////
    // totals
    // //////////////////////////////////////////////////////////////

    public TotalsRow buildTotalsRow(List<?> elements)
    {
        TotalsRow row = new TotalsRow();
        row.setNumberOfItems(elements.size());
        row.setFilteredItems(getFilteredCount(elements));
        return row;
    }

    public void calculateTotals(List<?> elements, TotalsRow totals, IProgressListener listener)
    {
        totals.setTotals(this.totalsCalculator.calculate(subject, elements, listener));
    }

    // //////////////////////////////////////////////////////////////
    // retained sizes
    // //////////////////////////////////////////////////////////////

    public List<CalculationJobDefinition> getJobs()
    {
        return jobs;
    }

    public synchronized Column addRetainedSizeColumn(ContextProvider provider)
    {
        RetainedSizeAccessor accessor = getAccessorFor(provider);
        if (accessor != null)
            return accessor.column;

        RetainedSizeCalculator calculator = calculatorFor(provider);

        String label = provider.getColumnLabel();

        Column column = new Column(label, long.class) //  
                        .comparing(new RetainedSizeComparator(calculator))//
                        .formatting(new RetainedSizeFormat()) //
                        .noTotals();
        column.setData(RetainedSizeCalculator.class, calculator);

        addColumn(column, new RetainedSizeAccessor(column, provider, calculator));
        this.totalsCalculator = TotalsCalculator.create(this);

        return column;
    }

    protected RetainedSizeCalculator calculatorFor(ContextProvider provider)
    {
        return new RetainedSizeCalculator.ArbitrarySet();
    }

    public Column getColumnFor(ContextProvider provider)
    {
        RetainedSizeAccessor a = getAccessorFor(provider);
        return a != null ? a.column : null;
    }

    private RetainedSizeAccessor getAccessorFor(ContextProvider provider)
    {
        for (ValueAccessor a : this.accessors)
        {
            // to pick the column, it has to be an retained size accessor and
            // the labels must match (compare labels because of provider
            // wrapping)

            if (a instanceof RetainedSizeAccessor)
            {
                RetainedSizeAccessor rsa = (RetainedSizeAccessor) a;
                if (provider.getLabel() == null && rsa.provider.getLabel() == null)
                    return rsa;
                if (provider.getLabel() != null && provider.getLabel().equals(rsa.provider.getLabel()))
                    return rsa;
            }
        }
        return null;
    }

    public interface ICalculationProgress
    {
        void done(int index, Object row);
    }

    public void calculate(ContextProvider provider, List<?> elements, boolean approximation,
                    ICalculationProgress progress, IProgressListener listener) throws SnapshotException
    {
        RetainedSizeAccessor accessor = getAccessorFor(provider);
        if (accessor == null)
        {
            addRetainedSizeColumn(provider);
            accessor = getAccessorFor(provider);
        }

        int index = 0;
        for (Object row : elements)
        {
            accessor.calculator.calculate(snapshot, provider, row, approximation, listener);

            if (progress != null)
                progress.done(index, row);

            index++;

            if (listener.isCanceled())
                return;
        }
    }

    // //////////////////////////////////////////////////////////////
    // access to the underlying original result
    // //////////////////////////////////////////////////////////////

    public IStructuredResult unwrap()
    {
        return subject;
    }

    // //////////////////////////////////////////////////////////////
    // IStructuredResult implementation
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return metaData;
    }

    public Column[] getColumns()
    {
        return columns.toArray(new Column[0]);
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        return accessors.get(columnIndex).getValue(row);
    }

    public String getFormattedColumnValue(Object row, int columnIndex)
    {
        // TODO: decoration!
        Object v = getColumnValue(row, columnIndex);
        if (v == null)
            return "";
        else if (v.getClass() == String.class) // is this shortcut okay?
            return (String) v;
        else
            return format(v, columnIndex);
    }

    public IContextObject getContext(Object row)
    {
        return subject.getContext(row);
    }

    public URL getIcon(Object row)
    {
        return iconProvider.getIcon(row);
    }

    public boolean isExpanded(Object row)
    {
        return selectionProvider.isExpanded(row);
    }

    public boolean isSelected(Object row)
    {
        return selectionProvider.isSelected(row);
    }

    // //////////////////////////////////////////////////////////////
    // result manipulation
    // //////////////////////////////////////////////////////////////

    protected List<?> refine(List<?> elements) throws SnapshotException
    {
        if (inlineJobs)
            return refineInlined(elements);

        int[] active = getActiveFilterIndeces();
        if (active.length > 0)
            elements = filter(elements, active);

        if (!resultIsSorted && sortColumn != -1 && elements != null)
            sort(elements);

        return elements;
    }

    protected List<?> refineUnfiltered(List<?> elements) throws SnapshotException
    {
        if (inlineJobs)
            for (CalculationJobDefinition job : jobs)
                calculate(job.getContextProvider(), elements, job.isApproximation(), null, new VoidProgressListener());
        
        if (!resultIsSorted && sortColumn != -1 && elements != null)
            sort(elements);

        return elements;
    }

    private int[] getActiveFilterIndeces()
    {
        ArrayInt active = new ArrayInt();
        for (int ii = 0; ii < filters.size(); ii++)
            if (filters.get(ii).isActive())
                active.add(ii);
        return active.toArray();
    }

    private List<?> refineInlined(List<?> elements) throws SnapshotException
    {
        for (CalculationJobDefinition job : jobs)
            calculate(job.getContextProvider(), elements, job.isApproximation(), null, new VoidProgressListener());
        
        int[] active = getActiveFilterIndeces();
        if (active.length > 0)
            elements = filter(elements, active);

        if (!resultIsSorted && sortColumn != -1)
            sort(elements);

        return elements;
    }

    private List<?> filter(List<?> elements, int[] active)
    {
        FilteredList<Object> answer = new FilteredList<Object>();

        Filter[] filter = new Filter[active.length];
        for (int ii = 0; ii < active.length; ii++)
            filter[ii] = filters.get(active[ii]);

        for (Object object : elements)
        {
            boolean accept = true;
            for (int ii = 0; accept && ii < active.length; ii++)
            {
                Object v = getColumnValue(object, active[ii]);
                if (filter[ii].acceptObject())
                    accept = filter[ii].accept(v);
                else
                    accept = filter[ii].accept(format(v, active[ii]));
            }

            if (accept)
                answer.add(object);
        }

        answer.setFilteredCount(elements.size() - answer.size());

        return answer;
    }

    private String format(Object value, int columnIndex)
    {
        if (value == null)
            return "";

        Format f = columns.get(columnIndex).getFormatter();
        return f != null ? f.format(value) : String.valueOf(value);
    }

    // //////////////////////////////////////////////////////////////
    // accessors
    // //////////////////////////////////////////////////////////////

    private class DefaultValueAccessor implements ValueAccessor
    {
        int columnIndex;

        public DefaultValueAccessor(int columnIndex)
        {
            this.columnIndex = columnIndex;
        }

        public Object getValue(Object row)
        {
            return subject.getColumnValue(row, columnIndex);
        }
    }

    private static class RetainedSizeAccessor implements ValueAccessor
    {
        Column column;
        ContextProvider provider;
        RetainedSizeCalculator calculator;

        public RetainedSizeAccessor(Column column, ContextProvider provider, RetainedSizeCalculator calculator)
        {
            this.column = column;
            this.provider = provider;
            this.calculator = calculator;
        }

        public Object getValue(Object row)
        {
            return this.calculator.getValue(row);
        }
    }

    public static final class RetainedSizeFormat extends Format
    {
        private static final long serialVersionUID = 1L;
        private static final Format formatter = new DecimalFormat("#,##0");

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
        {
            Long v = (Long) obj;

            if (v.longValue() < 0)
            {
                toAppendTo.append(">= ");
                formatter.format(-v.longValue(), toAppendTo, pos);
            }
            else
            {
                formatter.format(v, toAppendTo, pos);
            }

            return toAppendTo;
        }

        @Override
        public Object parseObject(String source, ParsePosition pos)
        {
            return null;
        }
    }

    /* package */static final class RetainedSizeComparator implements Comparator<Object>
    {
        private RetainedSizeCalculator calculator;

        public RetainedSizeComparator(RetainedSizeCalculator calculator)
        {
            this.calculator = calculator;
        }

        public int compare(Object o1, Object o2)
        {
            Long retainedSize_o1 = (Long) calculator.getValue(o1);
            Long retainedSize_o2 = (Long) calculator.getValue(o2);

            if (retainedSize_o1 == null)
                return retainedSize_o2 == null ? 0 : -1;
            else if (retainedSize_o2 == null)
                return 1;
            else
            {
                long retained_o1 = retainedSize_o1.longValue();
                long retained_o2 = retainedSize_o2.longValue();

                if (retained_o1 < 0)
                    retained_o1 = -retained_o1;
                if (retained_o2 < 0)
                    retained_o2 = -retained_o2;
                long diff = retained_o1 - retained_o2;
                return ((diff == 0) ? 0 : ((diff > 0) ? +1 : -1));
            }
        }
    }

}
