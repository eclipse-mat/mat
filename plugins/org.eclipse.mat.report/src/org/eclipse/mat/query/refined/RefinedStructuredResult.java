/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextDerivedData.DerivedCalculator;
import org.eclipse.mat.query.ContextDerivedData.DerivedColumn;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.SilentProgressListener;
import org.eclipse.mat.util.SimpleMonitor;
import org.eclipse.mat.util.VoidProgressListener;

/**
 * The result from refining a table or tree.
 */
public abstract class RefinedStructuredResult implements IStructuredResult, //
                ISelectionProvider, IIconProvider, Filter.FilterChangeListener
{
    // //////////////////////////////////////////////////////////////
    // factory
    // //////////////////////////////////////////////////////////////

    public static class DerivedDataJobDefinition
    {
        private ContextProvider provider;
        private DerivedOperation operation;

        public DerivedDataJobDefinition(ContextProvider provider, DerivedOperation operation)
        {
            this.provider = provider;
            this.operation = operation;
        }

        public ContextProvider getContextProvider()
        {
            return provider;
        }

        public DerivedOperation getOperation()
        {
            return operation;
        }

        public void setOperation(DerivedOperation operation)
        {
            this.operation = operation;
        }
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    /* package */interface ValueAccessor
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
        private Filter.ValueConverter converter;

        public NaturalComparator(RefinedStructuredResult refinedResult, int sortColumn)
        {
            this.refinedResult = refinedResult;
            this.sortColumn = sortColumn;
            this.converter = (Filter.ValueConverter)refinedResult.columns.get(sortColumn).getData(Filter.ValueConverter.class);
        }

        @SuppressWarnings("unchecked")
        public int compare(Object o1, Object o2)
        {
            Object d1 = refinedResult.getColumnValue(o1, sortColumn);
            Object d2 = refinedResult.getColumnValue(o2, sortColumn);
            if (converter != null)
            {
                if (d1 instanceof Bytes)
                    d1 = converter.convert(((Bytes)d1).getValue());
                else if (d1 instanceof Number)
                    d1 = converter.convert(((Number)d1).doubleValue());
                if (d2 instanceof Bytes)
                    d2 = converter.convert(((Bytes)d2).getValue());
                else if (d2 instanceof Number)
                    d2 = converter.convert(((Number)d2).doubleValue());
            }

            // Compare nulls - sort first
            if (d1 == null)
                return d2 == null ? 0 : -1;
            else if (d2 == null)
                return 1;
            else if (d1 instanceof Comparable && d2 instanceof Comparable)
            {
                // Compare using Comparable
                try
                {
                    return ((Comparable<Object>) d1).compareTo(d2);
                }
                catch (ClassCastException e)
                {
                    // See if different types of number
                    if (d1 instanceof Number && d2 instanceof Number)
                    {
                        int ret = Double.compare(((Number)d1).doubleValue(), ((Number)d2).doubleValue());
                        if (ret == 0)
                        {
                            long l1 = ((Number)d1).longValue();
                            long l2 = ((Number)d2).longValue();
                            if (l1 < l2)
                                return -1;
                            else if (l1 > l2)
                                return 1;
                            else
                                return 0;
                        }
                        else
                        {
                            return ret;
                        }
                    }
                    else if (d1 instanceof Number)
                        return -1;
                    else if (d2 instanceof Number)
                        return 1;
                    else
                        return d1.getClass().getName().compareTo(d2.getClass().getName());
                }
            }
            else if (d1 instanceof Comparable)
                return -1;
            else if (d2 instanceof Comparable)
                return 1;
            else
                return d1.toString().compareTo(d2.toString());
        }
    }

    /* package */static final class MultiColumnComparator implements Comparator<Object>
    {
        private List<Comparator<Object>> list;

        public MultiColumnComparator(List<Comparator<Object>> list)
        {
            this.list = list;
        }

        public int compare(Object o1, Object o2)
        {
            for (Comparator<Object> c : list)
            {
                int result = c.compare(o1, o2);
                if (result != 0)
                    return result;
            }
            return 0;
        }
    }

    // //////////////////////////////////////////////////////////////
    // member variables
    // //////////////////////////////////////////////////////////////

    /** original result wrapped by this refined result */
    protected IStructuredResult subject;

    /** original meta data */
    protected ResultMetaData metaData;

    /** query context -> calculate values like retained size */
    protected IQueryContext context;

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

    private boolean resultIsSorted = false;
    private int sortColumn = -1;
    private Column.SortDirection sortDirection;
    private Comparator<Object> comparator;

    protected boolean inlineJobs = false;
    protected List<DerivedDataJobDefinition> jobs = new ArrayList<DerivedDataJobDefinition>();

    // //////////////////////////////////////////////////////////////
    // initialization
    // //////////////////////////////////////////////////////////////

    /* package */RefinedStructuredResult()
    {}

    protected void init()
    {}

    void addColumn(Column column, ValueAccessor accessor)
    {
        columns.add(column);
        filters.add(Filter.Factory.build(column, this));
        accessors.add(accessor);
        decorators.add(column.getDecorator());
    }

    public void setSelectionProvider(ISelectionProvider provider)
    {
        this.selectionProvider = provider != null ? provider : ISelectionProvider.EMPTY;
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
        return elements instanceof FilteredList<?> ? ((FilteredList<?>) elements).getFilteredCount() : 0;
    }

    public void filterChanged(Filter filter)
    {}

    // //////////////////////////////////////////////////////////////
    // sorting
    // //////////////////////////////////////////////////////////////

    /*
     * @return -1 if the result is not sorted
     * */
    public int getSortColumn()
    {
        return sortColumn;
    }

    public Column.SortDirection getSortDirection()
    {
        return sortDirection;
    }

    /* package */void internalSetSortOrder(int columnIndex, SortDirection direction, boolean isPreSorted,
                    Comparator<Object> cmp)
    {
        this.sortColumn = columnIndex;
        this.sortDirection = direction;
        this.resultIsSorted = isPreSorted;

        if (cmp == null)
            cmp = buildComparator(columnIndex, direction);

        this.comparator = cmp;
    }

    @SuppressWarnings("unchecked")
    /* package */Comparator<Object> buildComparator(int columnIndex, Column.SortDirection direction)
    {
        Comparator<Object> cmp = (Comparator<Object>) columns.get(columnIndex).getComparator();
        if (cmp == null)
            cmp = new NaturalComparator(this, columnIndex);
        if (direction == Column.SortDirection.DESC)
            cmp = Collections.reverseOrder(cmp);
        return cmp;
    }

    public void setSortOrder(Column queryColumn, Column.SortDirection direction)
    {
        internalSetSortOrder(columns.indexOf(queryColumn), direction, false, null);
    }

    public void sort(List<?> elements)
    {
        if (comparator != null)
            Collections.sort(elements, comparator);
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

    public List<DerivedDataJobDefinition> getJobs()
    {
        return jobs;
    }

    public synchronized Column addDerivedDataColumn(ContextProvider provider, DerivedColumn derivedColumn)
    {
        CalculatedColumnAccessor accessor = getAccessorFor(provider, derivedColumn);
        if (accessor != null)
            return accessor.column;

        ContextDerivedData derivedData = context.getContextDerivedData();
        Column column = derivedData.columnFor(derivedColumn, subject, provider);

        DerivedCalculator calculator = (DerivedCalculator) column.getData(DerivedCalculator.class);

        addColumn(column, new CalculatedColumnAccessor(column, provider, derivedColumn, calculator));
        this.totalsCalculator = TotalsCalculator.create(this);

        return column;
    }

    public Column getColumnFor(ContextProvider provider, DerivedColumn derivedColumn)
    {
        CalculatedColumnAccessor a = getAccessorFor(provider, derivedColumn);
        return a != null ? a.column : null;
    }

    private CalculatedColumnAccessor getAccessorFor(ContextProvider provider, DerivedColumn derivedColumn)
    {
        for (ValueAccessor a : this.accessors)
        {
            // to pick the column, it has to be an retained size accessor and
            // the labels must match (compare labels because of provider
            // wrapping)

            if (a instanceof CalculatedColumnAccessor)
            {
                CalculatedColumnAccessor rsa = (CalculatedColumnAccessor) a;
                if (!rsa.derivedColumn.equals(derivedColumn))
                    continue;
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

    public void calculate(ContextProvider provider, //
                    DerivedOperation operation, //
                    List<?> elements, //
                    ICalculationProgress progress, //
                    IProgressListener listener) throws SnapshotException
    {
        ContextDerivedData derivedData = context.getContextDerivedData();
        DerivedColumn derivedColumn = derivedData.lookup(operation);

        CalculatedColumnAccessor accessor = getAccessorFor(provider, derivedColumn);
        if (accessor == null)
        {
            addDerivedDataColumn(provider, derivedColumn);
            accessor = getAccessorFor(provider, derivedColumn);
        }

        int work = elements.size();
        SimpleMonitor sm;
        IProgressListener l1, l2;
        if (work == 1)
        {
            // Just one item, so use the listener directly
            l1 = null;
            l2 = listener;
            sm = null;
        }
        if (work > 1 && work <= 10)
        {
            // Just a few items, so use a SimpleMonitor to track
            // the progress of each item in one bar
            l1 = null;
            l2 = null;
            int wk[] = new int[work];
            for (int i = 0; i < work; ++i)
            {
                wk[i] = 100;
            }
            sm = new SimpleMonitor(Messages.RefinedStructuredResult_Calculating, listener, wk);
        }
        else
        {
            // Many items, so just track progress by item
            l1 = listener;
            l1.beginTask(Messages.RefinedStructuredResult_Calculating, work);
            l2 = new SilentProgressListener(listener);
            sm = null;
        }
        int index = 0;
        // Don't iterate over the elements in case the order is changed by the user sorting the table.
        // Just index to avoid ConcurrentModificationException.
        for (; index < elements.size(); )
        {
            Object row = elements.get(index);
            if (sm != null)
                l2 = sm.nextMonitor();
            accessor.calculator.calculate(operation, row, l2);

            if (progress != null)
                progress.done(index, row);
            if (l1 != null)
                l1.worked(1);

            index++;

            if (listener.isCanceled())
                return;
        }
        if (l1 != null)
            l1.done();
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
        Object v = getColumnValue(row, columnIndex);
        if (v == null)
            return ""; //$NON-NLS-1$
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
            for (DerivedDataJobDefinition job : jobs)
                calculate(job.getContextProvider(), job.getOperation(), elements, null, new VoidProgressListener());

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
        for (DerivedDataJobDefinition job : jobs)
            calculate(job.getContextProvider(), job.getOperation(), elements, null, new VoidProgressListener());

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
            return ""; //$NON-NLS-1$

        Format f = columns.get(columnIndex).getFormatter();
        if (f != null)
        {
            try
            {
                return f.format(value);
            }
            catch (IllegalArgumentException e)
            {
            }
        }
        return String.valueOf(value);
    }

    // //////////////////////////////////////////////////////////////
    // accessors
    // //////////////////////////////////////////////////////////////

    class DefaultValueAccessor implements ValueAccessor
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

    /* package */static class CalculatedColumnAccessor implements ValueAccessor
    {
        Column column;
        ContextProvider provider;
        DerivedColumn derivedColumn;
        DerivedCalculator calculator;

        public CalculatedColumnAccessor(Column column, //
                        ContextProvider provider, //
                        DerivedColumn derivedColumn, //
                        DerivedCalculator calculator)
        {
            this.column = column;
            this.provider = provider;
            this.derivedColumn = derivedColumn;
            this.calculator = calculator;
        }

        public Object getValue(Object row)
        {
            return this.calculator.lookup(row);
        }
    }

}
