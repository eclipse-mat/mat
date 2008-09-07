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
package org.eclipse.mat.snapshot.query;

import java.net.URL;
import java.text.FieldPosition;
import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.query.quantize.Quantize.Function;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

/**
 * Create a value or frequency distribution out of {@link IResultTable}.
 */
public final class TQuantize
{
    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    private static final String MSG_LABEL = "Group by {0}";
    private static final String MSG_GROUPED = "Grouped ''{0}'' by {1}";

    /**
     * Well-known default aggregations.
     */
    public enum Target
    {
        /** Aggregate by class loader */
        CLASSLOADER("class loader", Icons.CLASSLOADER_INSTANCE, ByClassloader.class),
        /** Aggregate by package */
        PACKAGE("package", Icons.PACKAGE, ByPackage.class);

        private final String label;
        private final URL icon;
        private final Class<? extends Calculator> calculatorClass;

        private Target(String label, URL icon, Class<? extends Calculator> calculatorClass)
        {
            this.label = label;
            this.icon = icon;
            this.calculatorClass = calculatorClass;
        }

        public String getLabel()
        {
            return MessageFormat.format(MSG_LABEL, label);
        }

        public String getTitle(String command)
        {
            return MessageFormat.format(MSG_GROUPED, command, label);
        }

        public URL getIcon()
        {
            return icon;
        }

        Class<? extends Calculator> getCalculatorClass()
        {
            return calculatorClass;
        }
    }

    /**
     * Creates a {@link TQuantize} object which aggregates the table by the
     * value of the columns.
     */
    public static Builder valueDistribution(ISnapshot snapshot, IResultTable base, int... columns)
                    throws SnapshotException
    {
        if (columns == null || columns.length == 0)
            throw new NullPointerException("columns");

        if (columns.length > 1)
        {
            Format formatter = base.getColumns()[columns[0]].getFormatter();
            Builder builder = new Builder(snapshot, base, new ByMultipleColumns(columns, formatter));
            Column[] columns2 = base.getColumns();
            for (int column : columns)
            {
                builder.column(columns2[column], column);
            }
            return builder;
        }
        else
        {
            Format formatter = base.getColumns()[columns[0]].getFormatter();
            Builder builder = new Builder(snapshot, base, new BySingleColumn(columns[0], formatter));
            builder.column(base.getColumns()[columns[0]], columns[0]);
            return builder;
        }
    }

    /**
     * Creates a {@link TQuantize} object which aggregates the table by one of
     * the well-known targets, e.g. by class loader or package.
     */
    public static Builder valueDistribution(ISnapshot snapshot, IResultTable base, Target target)
                    throws SnapshotException
    {
        try
        {
            Builder builder = new Builder(snapshot, base, target.getCalculatorClass().newInstance());
            builder.column(base.getColumns()[0], 0);
            return builder;
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }

    /**
     * A convenience methods to aggregate a table by one of the well-known
     * targets and create the sum for all other columns.
     */
    public static TQuantize defaultValueDistribution(ISnapshot snapshot, IResultTable base, Target target)
                    throws SnapshotException
    {
        try
        {
            Builder builder = new Builder(snapshot, base, target.getCalculatorClass().newInstance());

            Column[] columns = base.getColumns();
            ResultMetaData data = base.getResultMetaData();
            if (data != null)
            {
                int columnIndex = data.getPreSortedColumnIndex();
                columns[columnIndex].sorting(data.getPreSortedDirection());
            }

            builder.column(columns[0], 0);

            for (int ii = 1; ii < columns.length; ii++)
            {
                Quantize.Function.Factory ff = null;

                if (columns[ii].getCalculateTotals())
                {
                    Class<?> type = columns[ii].getType();
                    if (Long.class.isAssignableFrom(type))
                        ff = Quantize.SUM_LONG;
                    else if (long.class.isAssignableFrom(type))
                        ff = Quantize.SUM_LONG;
                    else if (Double.class.isAssignableFrom(type))
                        ff = Quantize.SUM;
                    else if (double.class.isAssignableFrom(type))
                        ff = Quantize.SUM;
                    else if (Integer.class.isAssignableFrom(type))
                        ff = Quantize.SUM_LONG;
                    else if (int.class.isAssignableFrom(type))
                        ff = Quantize.SUM_LONG;
                    else if (Float.class.isAssignableFrom(type))
                        ff = Quantize.SUM;
                    else if (float.class.isAssignableFrom(type))
                        ff = Quantize.SUM;
                }

                builder.column(columns[ii].getLabel(), columns[ii], ii, ff);
            }

            return builder.build();
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }

    // //////////////////////////////////////////////////////////////
    // builder
    // //////////////////////////////////////////////////////////////

    /**
     * {@link TQuantize} factory
     */
    public static final class Builder
    {
        private final class WrappedComparator implements Comparator<Object>
        {
            Comparator<Object> original;

            @SuppressWarnings("unchecked")
            private WrappedComparator(Comparator<?> cmp)
            {
                original = (Comparator<Object>) cmp;
            }

            public int compare(Object o1, Object o2)
            {
                boolean is1Row = o1 instanceof GroupedRow;
                boolean is2Row = o2 instanceof GroupedRow;

                if (is1Row ^ is2Row)
                    return is1Row ? -1 : 1;
                else if (is1Row)
                    return ((GroupedRow) o1).compareTo((GroupedRow) o2);
                else
                    return original.compare(o1, o2);
            }
        }

        private static final class WrappedFormat extends Format
        {
            private static final long serialVersionUID = 1L;

            Format original;

            private WrappedFormat(Format original)
            {
                this.original = original;
            }

            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
            {
                if ((obj instanceof GroupedRow) || (obj instanceof String))
                    toAppendTo.append(obj);
                else
                    original.format(obj, toAppendTo, pos);

                return toAppendTo;
            }

            @Override
            public Object parseObject(String source, ParsePosition pos)
            {
                return null;
            }

            @Override
            public Object clone()
            {
                return new WrappedFormat((Format) original.clone());
            }

        }

        private static final class WrappedDecorator implements IDecorator
        {
            private IDecorator delegate;

            public WrappedDecorator(IDecorator delegate)
            {
                this.delegate = delegate;
            }

            public String prefix(Object row)
            {
                return row instanceof GroupedRow ? null : delegate.prefix(row);
            }

            public String suffix(Object row)
            {
                return row instanceof GroupedRow ? null : delegate.suffix(row);
            }
        }

        TQuantize quantize;

        private Builder(ISnapshot snapshot, IResultTable base, Calculator calculator) throws SnapshotException
        {
            quantize = new TQuantize(snapshot, base, calculator);
        }

        Builder column(final Column col, int baseColumnIndex)
        {
            quantize.columns.add(wrap(col));
            quantize.columnIndeces.add(baseColumnIndex);
            quantize.keyLength++;

            return this;
        }

        private Column wrap(final Column col)
        {
            Comparator<?> cmp = col.getComparator();
            if (cmp != null)
            {
                cmp = new WrappedComparator(cmp);
            }

            Format fmt = col.getFormatter();
            if (fmt != null)
            {
                fmt = new WrappedFormat(fmt);
            }

            Class<?> type = col.getType();
            if (type == null)
                type = String.class;

            IDecorator dec = col.getDecorator();
            if (dec != null)
                dec = new WrappedDecorator(dec);

            return new Column(col.getLabel(), type, col.getAlign(), col.getSortDirection(), fmt, cmp).decorator(dec);
        }

        /**
         * Add a column with label and function.
         */
        public Builder column(String label, Column baseColumn, int baseColumnIndex, Quantize.Function.Factory qff)
        {
            Column col = new Column(label, baseColumn.getType(), baseColumn.getAlign(), baseColumn.getSortDirection(),
                            baseColumn.getFormatter(), baseColumn.getComparator()).decorator(baseColumn.getDecorator());
            quantize.columns.add(col);
            quantize.columnIndeces.add(baseColumnIndex);
            quantize.functions.add(qff);

            return this;
        }

        /**
         * Build a {@link TQuantize} object.
         */
        public TQuantize build()
        {
            TQuantize answer = quantize;
            quantize = null; // builder must not change quantize

            answer.init();
            return answer;
        }
    }

    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    private ISnapshot snapshot;
    private IResultTable table;

    private int keyLength;
    private ArrayInt columnIndeces;
    private List<Column> columns;
    private List<Quantize.Function.Factory> functions;
    private Calculator calculator;

    TQuantize(ISnapshot snapshot, IResultTable table, Calculator calculator) throws SnapshotException
    {
        try
        {
            this.snapshot = snapshot;
            this.table = table;
            this.calculator = calculator;
            this.calculator.init(this);

            this.columnIndeces = new ArrayInt();
            this.columns = new ArrayList<Column>();
            this.functions = new ArrayList<Quantize.Function.Factory>();
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }

    protected void init()
    {}

    /**
     * Create distribution based on the given table.
     * 
     * @param listener
     *            progress listener
     */
    public IResult process(IProgressListener listener) throws SnapshotException
    {
        try
        {
            Map<Object, GroupedRowImpl> result = new HashMap<Object, GroupedRowImpl>();
            int numColumns = table.getColumns().length;

            for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++)
            {
                Object tableRow = table.getRow(rowIndex);
                Object key = calculator.key(tableRow);

                GroupedRowImpl groupedRow = result.get(key);
                if (groupedRow == null)
                {
                    groupedRow = new GroupedRowImpl(key, calculator.label(key));
                    groupedRow.functions = createFunctions();
                    result.put(key, groupedRow);
                }

                groupedRow.addRowIndex(rowIndex);

                // avoid multiple calls to #getColumnValue for the same value
                Object[] temp = new Object[numColumns];

                for (int i = 0; i < groupedRow.functions.length; i++)
                {
                    if (groupedRow.functions[i] != null)
                    {
                        int idx = columnIndeces.get(i + keyLength);
                        if (temp[idx] == null)
                            temp[idx] = table.getColumnValue(tableRow, idx);
                        groupedRow.functions[i].add(temp[idx]);
                    }
                }
            }

            return calculator.result(result);

        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }

    }

    protected Function[] createFunctions()
    {
        try
        {
            Function[] answer = new Quantize.Function[this.functions.size()];

            int ii = 0;
            for (Quantize.Function.Factory factory : this.functions)
            {
                if (factory != null)
                    answer[ii] = factory.build();
                ii++;
            }

            return answer;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // //////////////////////////////////////////////////////////////
    // calculator
    // //////////////////////////////////////////////////////////////

    static abstract class Calculator
    {
        protected TQuantize quantize;

        public final void init(TQuantize quantize)
        {
            this.quantize = quantize;
        }

        abstract IResult result(Map<Object, GroupedRowImpl> map);

        abstract String label(Object key) throws SnapshotException;

        abstract Object key(Object row) throws SnapshotException;
    }

    interface GroupedRow extends Comparable<GroupedRow>
    {
        List<?> getSubjects();

        void addSubjectsTo(List<Object> subjects);
    }

    class GroupedRowImpl implements GroupedRow
    {
        Object key;
        Object label;
        ArrayInt rowIndex;
        Quantize.Function[] functions;

        public GroupedRowImpl(Object key, Object label)
        {
            this.key = key;
            this.label = label;
            this.rowIndex = new ArrayInt();
        }

        public void addRowIndex(int idx)
        {
            this.rowIndex.add(idx);
        }

        public int compareTo(GroupedRow o)
        {
            if (o instanceof GroupedRowImpl)
            {
                return String.valueOf(label).compareTo(String.valueOf(((GroupedRowImpl) o).label));
            }
            else
            {
                return 1;
            }
        }

        public List<?> getSubjects()
        {
            List<Object> answer = new ArrayList<Object>();
            addSubjectsTo(answer);
            return answer;
        }

        public void addSubjectsTo(List<Object> subjects)
        {
            for (int ii = 0; ii < rowIndex.size(); ii++)
            {
                int rowIdx = rowIndex.get(ii);
                subjects.add(table.getRow(rowIdx));
            }
        }

        public ArrayInt getObjectIds()
        {
            return null;
        }

    }

    static class OneColumnFlatResult implements IResultTree, IIconProvider
    {
        IResultTable table;
        List<GroupedRowImpl> elements;
        List<Column> columns;
        URL icon;

        public OneColumnFlatResult(IResultTable table, List<Column> groupedColumns, List<GroupedRowImpl> elements,
                        URL icon)
        {
            this.table = table;
            this.elements = elements;
            this.columns = groupedColumns;
            this.icon = icon;
        }

        public ResultMetaData getResultMetaData()
        {
            return wrap(table);
        }

        public Column[] getColumns()
        {
            return this.columns.toArray(new Column[0]);
        }

        public List<?> getElements()
        {
            return elements;
        }

        public boolean hasChildren(Object parent)
        {
            return parent instanceof GroupedRowImpl;
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof GroupedRowImpl)
            {
                GroupedRowImpl row = (GroupedRowImpl) parent;
                List<Object> children = new ArrayList<Object>(row.rowIndex.size());

                for (int ii = 0; ii < row.rowIndex.size(); ii++)
                {
                    int rowIndex = row.rowIndex.get(ii);
                    children.add(table.getRow(rowIndex));
                }

                return children;
            }
            else
            {
                return null;
            }
        }

        public Object getColumnValue(Object element, int columnIndex)
        {
            if (element instanceof GroupedRowImpl)
            {
                GroupedRowImpl row = (GroupedRowImpl) element;
                switch (columnIndex)
                {
                    case 0:
                        return row.label;
                    default:
                        Quantize.Function function = row.functions[columnIndex - 1];
                        return function != null ? function.getValue() : null;
                }
            }
            else
            {
                return table.getColumnValue(element, columnIndex);
            }
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof GroupedRowImpl)
            {
                final ArrayInt ctxIds = new ArrayInt();
                List<?> myChildren = getChildren(row);
                for (Object child : myChildren)
                {
                    IContextObject ctx = table.getContext(child);
                    if (ctx instanceof IContextObjectSet)
                        ctxIds.addAll(((IContextObjectSet) ctx).getObjectIds());
                    else if (ctx != null)
                        ctxIds.add(ctx.getObjectId());
                }

                if (ctxIds.size() == 0)
                {
                    return null;
                }
                else if (ctxIds.size() == 1)
                {
                    return new IContextObject()
                    {
                        public int getObjectId()
                        {
                            return ctxIds.get(0);
                        }
                    };
                }
                else
                {
                    return new IContextObjectSet()
                    {
                        public int getObjectId()
                        {
                            return -1;
                        }

                        public int[] getObjectIds()
                        {
                            return ctxIds.toArray();
                        }

                        public String getOQL()
                        {
                            return null;
                        }
                    };
                }

            }
            else
            {
                return table.getContext(row);
            }
        }

        public URL getIcon(Object row)
        {
            if (row instanceof GroupedRowImpl)
            {
                return icon;
            }
            else
            {
                return table instanceof IIconProvider ? ((IIconProvider) table).getIcon(row) : null;
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // group by class loader
    // //////////////////////////////////////////////////////////////

    static class ByClassloader extends Calculator
    {
        public IResult result(Map<Object, GroupedRowImpl> result)
        {
            return new OneColumnFlatResult(quantize.table, quantize.columns, new ArrayList<GroupedRowImpl>(result
                            .values()), Icons.CLASSLOADER_INSTANCE);
        }

        public String label(Object key) throws SnapshotException
        {
            if (key == null) { return "<NONE>"; }

            int objectId = ((Integer) key).intValue();
            if (objectId < 0)
            {
                return "<NONE>";
            }
            else
            {
                IObject object = quantize.snapshot.getObject(objectId);
                String label = object.getClassSpecificName();
                return label != null ? label : object.getTechnicalName();
            }
        }

        public Object key(Object row) throws SnapshotException
        {
            IContextObject ctx = quantize.table.getContext(row);
            if (ctx == null)
                return null;

            int objectId = ctx.getObjectId();
            if (objectId < 0)
                return -1;

            IObject obj = quantize.snapshot.getObject(objectId);
            return obj instanceof IClass ? ((IClass) obj).getClassLoaderId() : obj.getClazz().getClassLoaderId();
        }
    }

    // //////////////////////////////////////////////////////////////
    // group by package
    // //////////////////////////////////////////////////////////////

    static class ByPackage extends Calculator
    {
        Package root = new Package("<ROOT>", null);

        public IResult result(Map<Object, GroupedRowImpl> result)
        {
            // propagate values to the packages
            for (Map.Entry<Object, GroupedRowImpl> entry : result.entrySet())
            {
                GroupedRowImpl row = entry.getValue();

                Package p = (Package) entry.getKey();

                while (p != null)
                {
                    if (p.functions == null)
                        p.functions = quantize.createFunctions();

                    for (int ii = 0; ii < p.functions.length; ii++)
                    {
                        if (p.functions[ii] != null)
                            p.functions[ii].add(row.functions[ii].getValue());
                    }
                    p = p.parent;
                }

            }

            return new ByPackageResult(root, quantize.table, quantize.columns, result);
        }

        public String label(Object key) throws SnapshotException
        {
            return ((Package) key).name;
        }

        public Object key(Object row) throws SnapshotException
        {
            IContextObject ctx = quantize.table.getContext(row);
            if (ctx == null)
                return root.getOrCreateChild("<none>");

            int objectId = ctx.getObjectId();
            if (objectId < 0)
                return root.getOrCreateChild("<none>");

            IClass objClass = quantize.snapshot.getClassOf(objectId);
            if ("java.lang.Class".equals(objClass.getName()))
            {
                IObject obj = quantize.snapshot.getObject(objectId);
                if (obj instanceof IClass)
                    objClass = (IClass) obj;
            }
            String className = objClass.getName();

            int p = className.lastIndexOf('.');
            if (p < 0)
                return root;

            StringTokenizer tokenizer = new StringTokenizer(className.substring(0, p), ".");

            Package current = root;

            while (tokenizer.hasMoreTokens())
            {
                String subpack = tokenizer.nextToken();
                Package childNode = current.getOrCreateChild(subpack);
                current = childNode;
            }

            return current;
        }

    }

    static class Package implements GroupedRow
    {
        String name;
        Package parent;
        Map<String, Package> subPackages = new HashMap<String, Package>();
        Quantize.Function[] functions;

        GroupedRowImpl groupedRow;

        public Package(String name, Package parent)
        {
            this.name = name;
            this.parent = parent;
        }

        Package getOrCreateChild(String name)
        {
            Package answer = subPackages.get(name);
            if (answer == null)
                subPackages.put(name, answer = new Package(name, this));
            return answer;
        }

        public int compareTo(GroupedRow o)
        {
            if (o instanceof Package)
                return name.compareTo(((Package) o).name);
            else
                return 1;
        }

        void setGroupedRow(GroupedRowImpl row)
        {
            this.groupedRow = row;
        }

        public List<?> getSubjects()
        {
            List<Object> answer = new ArrayList<Object>();
            addSubjectsTo(answer);
            return answer;
        }

        public void addSubjectsTo(List<Object> subjects)
        {
            if (groupedRow != null)
                groupedRow.addSubjectsTo(subjects);

            if (!subPackages.isEmpty())
            {
                for (Package p : subPackages.values())
                {
                    p.addSubjectsTo(subjects);
                }
            }
        }

        public ArrayInt getObjectIds()
        {
            return null;
        }
    }

    static class ByPackageResult implements IResultTree, IIconProvider
    {
        Package root;
        IResultTable table;
        List<Column> columns;
        Map<Object, GroupedRowImpl> elements;

        public ByPackageResult(Package root, IResultTable table, List<Column> groupedColumns,
                        Map<Object, GroupedRowImpl> elements)
        {
            this.root = root;
            this.table = table;
            this.columns = groupedColumns;
            this.elements = elements;

            for (Map.Entry<Object, GroupedRowImpl> entry : elements.entrySet())
            {
                ((Package) entry.getKey()).setGroupedRow(entry.getValue());
            }
        }

        public ResultMetaData getResultMetaData()
        {
            return wrap(table);
        }

        public Column[] getColumns()
        {
            return this.columns.toArray(new Column[0]);
        }

        public List<?> getElements()
        {
            List<Object> children = new ArrayList<Object>();
            children.addAll(root.subPackages.values());

            addOriginalChildren(children, root);

            return children;
        }

        private void addOriginalChildren(List<Object> children, Object subject)
        {
            GroupedRowImpl row = elements.get(subject);
            if (row != null)
            {
                for (int ii = 0; ii < row.rowIndex.size(); ii++)
                {
                    int rowIndex = row.rowIndex.get(ii);
                    children.add(table.getRow(rowIndex));
                }
            }
        }

        public boolean hasChildren(Object parent)
        {
            return parent instanceof Package;
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof Package)
            {
                Package p = (Package) parent;
                List<Object> children = new ArrayList<Object>();

                for (Package sp : p.subPackages.values())
                {
                    children.add(sp);
                }
                addOriginalChildren(children, p);

                return children;
            }
            else
            {
                return null;
            }
        }

        public Object getColumnValue(Object element, int columnIndex)
        {
            if (element instanceof Package)
            {
                Package p = (Package) element;
                if (columnIndex == 0)
                {
                    return p.name;
                }
                else
                {
                    Quantize.Function function = p.functions[columnIndex - 1];
                    return function != null ? function.getValue() : null;
                }
            }
            else
            {
                if (columnIndex == 0)
                {
                    String label = String.valueOf(table.getColumnValue(element, columnIndex));
                    int p = label.lastIndexOf('.');
                    return p >= 0 ? label.substring(p + 1) : label;
                }
                else
                {
                    return table.getColumnValue(element, columnIndex);
                }
            }
        }

        public URL getIcon(Object row)
        {
            if (row instanceof Package)
                return Icons.PACKAGE;
            else
                return table instanceof IIconProvider ? ((IIconProvider) table).getIcon(row) : null;
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof Package)
            {
                return null; // nothing to show in inspector
            }
            else
            {
                return table.getContext(row);
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // group by single column
    // //////////////////////////////////////////////////////////////

    /* package */static class BySingleColumn extends Calculator
    {
        private int columnIndex;
        private Format formatter;

        public BySingleColumn(int columnIndex, Format formatter)
        {
            this.columnIndex = columnIndex;
            this.formatter = formatter;
        }

        public Object key(Object row) throws SnapshotException
        {
            return quantize.table.getColumnValue(row, columnIndex);
        }

        public String label(Object key) throws SnapshotException
        {
            return formatter != null ? formatter.format(key) : String.valueOf(key);
        }

        public IResult result(Map<Object, GroupedRowImpl> map)
        {
            return new OneColumnFlatResult(quantize.table, quantize.columns,
                            new ArrayList<GroupedRowImpl>(map.values()), null);
        }

    }

    // //////////////////////////////////////////////////////////////
    // group by multiple columns
    // //////////////////////////////////////////////////////////////

    /* package */static class ByMultipleColumns extends Calculator
    {
        private int[] columnIndeces;
        private Format formatter;

        public ByMultipleColumns(int[] columnIndex, Format formatter)
        {
            this.formatter = formatter;
            this.columnIndeces = columnIndex;
        }

        public Object key(Object row) throws SnapshotException
        {
            Object[] keys = new Object[columnIndeces.length];
            for (int ii = 0; ii < columnIndeces.length; ii++)
            {
                keys[ii] = quantize.table.getColumnValue(row, columnIndeces[ii]);
            }

            return new CompositeKey(keys);
        }

        public String label(Object key) throws SnapshotException
        {
            Object label = ((CompositeKey) key).keys[0];
            return formatter != null ? formatter.format(label) : String.valueOf(label);
        }

        public IResult result(Map<Object, GroupedRowImpl> map)
        {
            return new MultipleColumnsFlatResult(quantize.table, quantize.columns, new ArrayList<GroupedRowImpl>(map
                            .values()));
        }

    }

    static class CompositeKey implements Comparable<CompositeKey>
    {
        Object[] keys;

        public CompositeKey(Object[] keys)
        {
            this.keys = keys;
        }

        @SuppressWarnings("unchecked")
        public int compareTo(CompositeKey o)
        {
            // should never happen as maps contain uniformed keys
            if (this.keys.length != o.keys.length)
                return this.keys.length > o.keys.length ? -1 : 1;

            for (int ii = 0; ii < keys.length; ii++)
            {
                int c = ((Comparable) keys[ii]).compareTo(o.keys[ii]);
                if (c != 0)
                    return c;
            }

            return 0;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            CompositeKey other = (CompositeKey) obj;

            if (this.keys.length != other.keys.length)
                return false;

            for (int ii = 0; ii < keys.length; ii++)
            {
                if (!this.keys[ii].equals(other.keys[ii]))
                    return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            final int PRIME = 31;

            // size not relevant for the distribution
            int result = 0;

            for (int ii = 0; ii < keys.length; ii++)
                result = PRIME * result + keys[ii].hashCode();

            return result;
        }
    }

    static class MultipleColumnsFlatResult extends OneColumnFlatResult
    {
        public MultipleColumnsFlatResult(IResultTable table, List<Column> columns, List<GroupedRowImpl> elements)
        {
            super(table, columns, elements, null);
        }

        public Object getColumnValue(Object element, int columnIndex)
        {
            if (element instanceof GroupedRowImpl)
            {
                GroupedRowImpl row = (GroupedRowImpl) element;
                CompositeKey key = (CompositeKey) row.key;

                if (columnIndex == 0)
                {
                    return row.label;
                }
                else if (columnIndex < key.keys.length)
                {
                    return key.keys[columnIndex];
                }
                else
                {
                    Quantize.Function function = row.functions[columnIndex - key.keys.length];
                    return function != null ? function.getValue() : null;
                }
            }
            else
            {
                return table.getColumnValue(element, columnIndex);
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // misc. helpers
    // //////////////////////////////////////////////////////////////

    static class MergedObjectContext
    {
        List<IContextObject> objects = new ArrayList<IContextObject>();

        public void add(IContextObject ctx)
        {
            objects.add(ctx);
        }

        public IContextObject getContext()
        {
            if (objects.isEmpty())
                return null;

            if (objects.size() == 1)
                return objects.get(0);

            return new IContextObjectSet()
            {
                public int[] getObjectIds()
                {
                    ArrayInt answer = new ArrayInt();
                    for (IContextObject c : objects)
                    {
                        if (c instanceof IContextObjectSet)
                            answer.addAll(((IContextObjectSet) c).getObjectIds());
                        else
                            answer.add(c.getObjectId());
                    }
                    return answer.toArray();
                }

                public String getOQL()
                {
                    return null;
                }

                public int getObjectId()
                {
                    return -1;
                }
            };
        }

    }

    private static ResultMetaData wrap(IResultTable table)
    {
        ResultMetaData data = table.getResultMetaData();
        if (data == null)
            return null;

        List<ContextProvider> providers = data.getContextProviders();
        if (providers == null)
            return null;

        ResultMetaData.Builder answer = new ResultMetaData.Builder();

        for (final ContextProvider provider : providers)
        {
            answer.addContext(new ContextProvider(provider)
            {
                public IContextObject getContext(Object row)
                {
                    if (row instanceof GroupedRow)
                    {
                        MergedObjectContext merged = new MergedObjectContext();
                        for (Object subject : ((GroupedRow) row).getSubjects())
                        {
                            IContextObject obj = provider.getContext(subject);
                            merged.add(obj);
                        }
                        return merged.getContext();
                    }
                    else
                    {
                        return provider.getContext(row);
                    }
                }
            });
        }

        // do not pass on pre-sorted information as table probably is not
        // pre-sorted anymore

        Collection<DerivedOperation> derivedOperations = data.getDerivedOperations();
        if (derivedOperations != null)
            for (ContextDerivedData.DerivedOperation operation : derivedOperations)
                answer.addDerivedData(operation);

        return answer.build();
    }

}
