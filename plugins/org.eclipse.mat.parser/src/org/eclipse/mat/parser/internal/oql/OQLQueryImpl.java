/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - bug fixes for instanceof, big changes for tables
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql;

import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl;
import org.eclipse.mat.parser.internal.oql.compiler.EvaluationContext;
import org.eclipse.mat.parser.internal.oql.compiler.Expression;
import org.eclipse.mat.parser.internal.oql.compiler.Query;
import org.eclipse.mat.parser.internal.oql.compiler.Query.FromClause;
import org.eclipse.mat.parser.internal.oql.compiler.Query.SelectClause;
import org.eclipse.mat.parser.internal.oql.compiler.Query.SelectItem;
import org.eclipse.mat.parser.internal.oql.parser.OQLParser;
import org.eclipse.mat.parser.internal.oql.parser.ParseException;
import org.eclipse.mat.parser.internal.oql.parser.TokenMgrError;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;
import org.eclipse.mat.util.SilentProgressListener;
import org.eclipse.mat.util.SimpleMonitor;
import org.eclipse.mat.util.VoidProgressListener;

public class OQLQueryImpl implements IOQLQuery
{
    Query query;
    EvaluationContext ctx;

    // //////////////////////////////////////////////////////////////
    // result set implementations
    // //////////////////////////////////////////////////////////////

    private interface CustomTableResultSet extends IOQLQuery.Result, IResultTable, List<AbstractCustomTableResultSet.RowMap>
    {}

    /**
     * Find the distinct rows in the table.
     * @param table
     * @param listener
     * @return an array of indexes of unique rows
     */
    private static ArrayInt distinctList(CustomTableResultSet table, IProgressListener listener)
    {
        LinkedHashSet<Object> hs = new LinkedHashSet<Object>();
        ArrayInt newrows = new ArrayInt();
        int i = 0;
        for (Object row : table)
        {
            if (hs.add(row))
            {
                newrows.add(i);
            }
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            ++i;
        }
        hs.clear();
        return newrows;
    }

    /**
     * A table holding the result of a query.
     *
     */
    private static abstract class AbstractCustomTableResultSet extends AbstractList<AbstractCustomTableResultSet.RowMap> implements CustomTableResultSet
    {
        /**
         * Holds a row from a sub-select with columns ready for a select.
         */
        private static class RowMap extends AbstractMap<String,Object>
        {
            IStructuredResult isr;
            IResultTable irtb;
            IResultTree irtr;
            int index;
            int subindex;
            public RowMap(IStructuredResult irt, int index)
            {
                this(irt, index, -1);
            }
            public RowMap(IStructuredResult irt, int index, int subindex)
            {
                this.isr = irt;
                if (isr instanceof IResultTable)
                    this.irtb = (IResultTable)irt;
                if (isr instanceof IResultTree)
                    this.irtr = (IResultTree)irt;
                this.index = index;
                this.subindex = subindex;
            }

            @Override
            public int size()
            {
                return isr.getColumns().length;
            }

            @Override
            public Object get(Object colname)
            {
                for (int col = 0; col < isr.getColumns().length; ++col)
                {
                    if (isr.getColumns()[col].getLabel().equals(colname))
                    {
                        Object row;
                        if (irtb != null)
                            row = irtb.getRow(index);
                        else if (irtr != null)
                            row = irtr.getElements().get(index);
                        else
                            return null;
                        return isr.getColumnValue(row, col);
                    }
                }
                return null;
            }

            public int getObjectId()
            {
                IContextObject cx = isr.getContext(index);
                if (cx != null)
                    return cx.getObjectId();
                return -1;
            }

            @Override
            public Set<Entry<String, Object>> entrySet()
            {
                Set<Entry<String, Object>> set = new LinkedHashSet<Entry<String, Object>>();
                for (int col = 0; col < isr.getColumns().length; ++col)
                {
                    String key = isr.getColumns()[col].getLabel();
                    set.add(new Entry<String, Object>() {

                        final Object NULL_VALUE = new Object();

                        Object value;

                        public Object getValue()
                        {
                            Object o = value;
                            if (o == NULL_VALUE)
                                return null;
                            else if (o != null)
                                return o;
                            o = get(getKey());
                            value = o;
                            return o;
                        }

                        public Object setValue(Object o)
                        {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getKey()
                        {
                            return key;
                        }

                        public int hashCode()
                        {
                            return Objects.hash(getKey(), getValue());
                        }

                        public boolean equals(Object o)
                        {
                            if ((o instanceof Entry<?,?>))
                            {
                                Entry<?,?>ox = (Entry<?,?>)o;
                                return Objects.equals(getKey(), ox.getKey()) &&
                                       Objects.equals(getValue(), ox.getValue());
                            }
                            {
                                return false;
                            }
                        }
                    });
                }
                return Collections.unmodifiableSet(set);
            }
        }

        public AbstractCustomTableResultSet(OQLQueryImpl source)
        {
            this.source = source;
        }

        /**
         * Find the object ID of an IObject or a row backed by an IObject
         * @param o
         * @return
         */
        static int getObjectId(Object o)
        {
            if (o instanceof IObject)
                return ((IObject)o).getObjectId();
            //else if (o instanceof RowMap)
            //    return ((RowMap)o).getObjectId();
            return -1;
        }

        protected OQLQueryImpl source;
        protected Column[] columns;

        @Override
        public RowMap get(int index)
        {
            // Always return a map not a single item for consistency.
            // Except if the alias is "" as this used for CompareTablesQuery.
            if (getColumns().length == 1 && getColumns()[0].getLabel().length() == 0)
            {
                Object ret = getColumnValue(getRow(index), 0);
                if (ret instanceof RowMap)
                    return (RowMap)ret;
            }
            // Delay resolving columns until needed
            return new RowMap(this, index);
        }

        @Override
        public int size()
        {
            return getRowCount();
        }

        /**
         * Get more details for a table.
         * Allows extra menu options for columns holding objects.
         * @param rt
         * @param columns
         * @param source
         * @return
         */
        protected ResultMetaData getResultMetaData(OQLQueryImpl source)
        {
            Query query = source.query;
            Column columns[] = getColumns();
            int objectCount = source.ctx.getSnapshot().getSnapshotInfo().getNumberOfObjects();
            ResultMetaData.Builder builder = new ResultMetaData.Builder();
            int prov = 0;
            for (int ii = 0; ii < columns.length; ++ii)
            {
                // Find an example object for each column
                Object sample = getRowCount() > 0 ? getColumnValue(getRow(0), ii) : null;
                // See if it is one or more objects from the dump
                if (getObjectId(sample) != -1 ||
                                sample instanceof Iterable<?> && ((Iterable<?>)sample).iterator().hasNext() && getObjectId(((Iterable<?>)sample).iterator().next()) != -1 ||
                                sample instanceof int[])
                {
                    // Also add the underlying row
                    if (prov == 0 && getContext(getRow(0)) != null)
                    {
                        String label;
                        FromClause fc = query.getFromClause();
                        String alias = fc.getAlias();
                        if (alias != null)
                            label = alias;
                        else
                            label = fc.toString();
                        // Distinguish the select for all the columns
                        label = "SELECT ... " + label; //$NON-NLS-1$
                        // Use a null label to provide a default context without a sub-menu
                        builder.addContext(new ContextProvider(label) {
                            @Override
                            public IContextObject getContext(Object row)
                            {
                                return AbstractCustomTableResultSet.this.getContext(row);
                            }
                        });
                        ++prov;
                    };
                    int columnIndex = ii;
                    builder.addContext(new ContextProvider(columns[ii].getLabel()) {

                        @Override
                        public IContextObject getContext(Object row)
                        {
                            Object o = getColumnValue(row, columnIndex);
                            int objectId  = getObjectId(o);
                            boolean goodContext = false;
                            if (objectId == -1 && !(o instanceof Iterable<?> || o instanceof int[]))
                            {
                                IContextObject cx = AbstractCustomTableResultSet.this.getContext(row);
                                if (cx != null)
                                {
                                    int selectId = cx.getObjectId();
                                    if (selectId != -1)
                                        goodContext = true;
                                    else if (cx instanceof IContextObjectSet)
                                    {
                                        if (((IContextObjectSet)cx).getOQL() != null)
                                            goodContext = true;
                                    }
                                }
                            }
                            if (objectId != -1 || goodContext)
                            {
                                return new IContextObjectSet() {

                                    @Override
                                    public int getObjectId()
                                    {

                                        return objectId;
                                    }

                                    @Override
                                    public int[] getObjectIds()
                                    {
                                        if (objectId != -1)
                                            return new int[] {objectId};
                                        else
                                            return new int[0];
                                    }

                                    @Override
                                    public String getOQL()
                                    {
                                        String alias = query.getFromClause().getAlias();
                                        String alias2;
                                        if (alias == null)
                                            alias2 = ""; //$NON-NLS-1$
                                        else
                                            alias2 = " "+alias; //$NON-NLS-1$
                                        Query.SelectItem column = query.getSelectClause().getSelectList().get(columnIndex);
                                        IContextObject cx = AbstractCustomTableResultSet.this.getContext(row);
                                        if (cx != null)
                                        {
                                            int selectId = cx.getObjectId();
                                            if (selectId != -1)
                                                return "SELECT " + column.toString() + " FROM OBJECTS " + selectId + alias2; //$NON-NLS-1$ //$NON-NLS-2$
                                            else if (cx instanceof IContextObjectSet)
                                            {
                                                IContextObjectSet cs = (IContextObjectSet)cx;
                                                String oqlsource = cs.getOQL();
                                                if (oqlsource != null)
                                                {
                                                    try
                                                    {
                                                        OQLQueryImpl q2 = new OQLQueryImpl(oqlsource);
                                                        q2.query.getSelectClause().setSelectList(Collections.emptyList());
                                                        String oqlsource2 = q2.query.toString();
                                                        return "SELECT "+column.toString()+" FROM OBJECTS (" + oqlsource2+") "+alias2; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                    }
                                                    catch (OQLParseException e)
                                                    {
                                                    }
                                                }
                                            }
                                        }
                                        if (objectId != -1)
                                            return OQL.forObjectId(objectId);
                                        else
                                            return OQLforSubject("*", o, ""); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                };
                            }
                            else if (o instanceof Iterable<?> || o instanceof int[])
                            {
                                return new IContextObjectSet() {

                                    @Override
                                    public int getObjectId()
                                    {
                                        IContextObject cx = AbstractCustomTableResultSet.this.getContext(row);
                                        if (cx != null)
                                            return cx.getObjectId();
                                        else
                                            return -1;
                                    }

                                    @Override
                                    public int[] getObjectIds()
                                    {
                                        if (o instanceof int[])
                                        {
                                            for (int ix : (int[])o)
                                            {
                                                if (ix < 0 || ix >= objectCount)
                                                    return new int[0];
                                            }
                                            return (int[])o;
                                        }
                                        ArrayInt ai = new ArrayInt();
                                        if (o instanceof Iterable<?>)
                                        {
                                            Iterable<?>l = (Iterable<?>)o;
                                            for (Object o : l)
                                            {
                                                int objectId = AbstractCustomTableResultSet.getObjectId(o);
                                                if (objectId != -1)
                                                {
                                                    ai.add(objectId);
                                                }
                                            }
                                        }
                                        return ai.toArray();
                                    }

                                    @Override
                                    public String getOQL()
                                    {
                                        String alias = query.getFromClause().getAlias();
                                        String alias2;
                                        if (alias == null)
                                            alias2 = ""; //$NON-NLS-1$
                                        else
                                            alias2 = " "+alias; //$NON-NLS-1$
                                        Query.SelectItem column = query.getSelectClause().getSelectList().get(columnIndex);
                                        int selectId = getObjectId();
                                        if (selectId != -1)
                                            return "SELECT "+column.toString()+" FROM OBJECTS " + selectId + alias2; //$NON-NLS-1$ //$NON-NLS-2$
                                        else
                                        {
                                            IContextObject cx =  AbstractCustomTableResultSet.this.getContext(row);
                                            if (cx instanceof IContextObjectSet)
                                            {
                                                IContextObjectSet cs = (IContextObjectSet)cx;
                                                String oqlsource = cs.getOQL();
                                                if (oqlsource != null)
                                                {
                                                    try
                                                    {
                                                        OQLQueryImpl q2 = new OQLQueryImpl(oqlsource);
                                                        q2.query.getSelectClause().setSelectList(Collections.emptyList());
                                                        String oqlsource2 = q2.query.toString();
                                                        return "SELECT " + column.toString() + " FROM OBJECTS (" + oqlsource2 + ") " + alias2; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                    }
                                                    catch (OQLParseException e)
                                                    {
                                                    }
                                                }
                                            }
                                        }
                                        return OQL.forObjectIds(getObjectIds());
                                    }
                                };
                            }
                            return null;
                        }

                    });
                    ++prov;
                }
            }
            return builder.build();
        }

        /**
         * Simple toString() which does not evaluate all the rows and columns,
         * which otherwise a subclass of AbstractList would do.
         */
        public String toString()
        {
            int ncols = getColumns() != null ? getColumns().length : 0;
            StringBuilder sb = new StringBuilder();
            sb.append(this.getClass().getSimpleName()).append('@').append(Integer.toHexString(System.identityHashCode(this)));
            sb.append('[').append(getRowCount()).append(']');
            sb.append('[').append(ncols).append(']');
            sb.append('[');
            final int maxRows = 2;
            for (int i = 0; i < Math.min(maxRows,  getRowCount()); ++i)
            {
                sb.append(get(i).toString());
                if (i + 1 < getRowCount())
                    sb.append(',');
            }
            if (getRowCount() > maxRows)
                sb.append("..."); //$NON-NLS-1$
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Result from a select with select list where the from clause returned a list or array of non-heap objects.
     * Each row is backed by a non-heap object.
     */
    private static class ObjectResultSet extends AbstractCustomTableResultSet implements CustomTableResultSet
    {
        private static final Object NULL_VALUE = new Object();

        private static class ValueHolder
        {
            Object subject;
            Object[] values;

            public ValueHolder(Object subject, Object[] values)
            {
                this.subject = subject;
                this.values = values;
            }
        }

        Object[] objects;

        ObjectResultSet(OQLQueryImpl source, List<Object> objects) throws SnapshotException
        {
            this(source, objects.toArray());
        }

        ObjectResultSet(OQLQueryImpl source, Object[] objects) throws SnapshotException
        {
            super(source);
            this.objects = objects;

            List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
            columns = new Column[selectList.size()];

            try
            {
                for (int ii = 0; ii < columns.length; ii++)
                    columns[ii] = buildColumn(selectList.get(ii), getRowCount() > 0 ? getColumnValue(getRow(0), ii) : null);
            }
            catch (RuntimeException e)
            {
                throw SnapshotException.rethrow(e);
            }
        }

        public ResultMetaData getResultMetaData()
        {
            return getResultMetaData(source);
        }

        public Column[] getColumns()
        {
            return columns;
        }

        public int getRowCount()
        {
            return objects.length;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            int index = (Integer) row;
            if (!(objects[index] instanceof ValueHolder))
                resolve(index);

            ValueHolder holder = ((ValueHolder) objects[index]);

            if (holder.values[columnIndex] == null)
            {
                try
                {
                    source.ctx.setSubject(holder.subject);
                    // Don't track progress here for reading the cell
                    IProgressListener old = source.ctx.getProgressListener();
                    source.ctx.setProgressListener(new SilentProgressListener(old));
                    Query.SelectItem column = source.query.getSelectClause().getSelectList().get(columnIndex);
                    Object v = column.getExpression().compute(source.ctx);

                    source.ctx.setProgressListener(old);
                    holder.values[columnIndex] = v == null ? NULL_VALUE : v;
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }

            return holder.values[columnIndex] == NULL_VALUE ? null : holder.values[columnIndex];
        }

        public IContextObjectSet getContext(Object row)
        {
            final int index = (Integer) row;
            if (!(objects[index] instanceof ValueHolder))
                resolve(index);

            Object subject = ((ValueHolder) objects[index]).subject;
            int objectId = getObjectId(subject);
            if (objectId != -1 || subject == null || subject instanceof Character || subject instanceof Integer
                            || subject instanceof Long || subject instanceof Float || subject instanceof Double
                            || subject instanceof Boolean || subject instanceof String || subject instanceof AbstractCustomTableResultSet.RowMap)
            {
                if (objectId == -1)
                {
                    if (OQLforSubject("*", subject, "") == null) //$NON-NLS-1$ //$NON-NLS-2$
                        return null;
                }
                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return objectId;
                    }

                    public int[] getObjectIds()
                    {
                        if (getObjectId() != -1)
                            return new int[] {getObjectId()};
                        else
                            return new int[0];
                    }

                    public String getOQL()
                    {
                        String alias = source.query.getFromClause().getAlias();
                        String alias2;
                        if (alias == null)
                            alias2 = ""; //$NON-NLS-1$
                        else
                            alias2 = " "+alias; //$NON-NLS-1$
                        SelectClause sc = source.query.getSelectClause();
                        if (sc.isRetainedSet())
                        {
                            // Remove asRetainedSet() as we just have a single object here
                            SelectClause sc2 = new SelectClause();
                            sc2.setAsObjects(sc.isAsObjects());
                            sc2.setSelectList(sc.getSelectList());
                            sc = sc2;
                        }

                        String from;
                        if (getObjectId() != -1)
                            from = String.valueOf(getObjectId());
                        else
                        {
                            // OQL literals (not Byte or Short)
                            String oql = OQLforSubject("*", subject, alias2); //$NON-NLS-1$
                            from = "(" + oql + ")";//$NON-NLS-1$ //$NON-NLS-2$
                        }
                        return "SELECT "+sc.toString() +" FROM OBJECTS " + from + alias2; //$NON-NLS-1$ //$NON-NLS-2$
                    }
                };
            }
            else
            {
                return null;
            }
        }

        public Object getRow(int index)
        {
            return index;
        }

        public String getOQLQuery()
        {
            return source.toString();
        }

        private void resolve(int index)
        {
            ValueHolder answer = new ValueHolder(objects[index], new Object[columns.length]);
            objects[index] = answer;
        }
    }

    /**
     * Result from a select with select list where the from clause returned an array of object IDs.
     * Each row is backed by a heap object, so will appear in the inspector view.
     */
    private static class ResultSet extends AbstractCustomTableResultSet implements CustomTableResultSet
    {
        private static final Object NULL_VALUE = new Object();

        private static class ValueHolder
        {
            Object[] values;

            public ValueHolder(Object[] values)
            {
                this.values = values;
            }
        }

        int[] objectIds;
        ValueHolder[] objects;

        public ResultSet(OQLQueryImpl source, int[] objectIds) throws SnapshotException
        {
            super(source);
            this.objectIds = objectIds;
            this.objects = new ValueHolder[objectIds.length];

            List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
            columns = new Column[selectList.size()];

            try
            {
                for (int ii = 0; ii < columns.length; ii++)
                    columns[ii] = buildColumn(selectList.get(ii), getRowCount() > 0 ? getColumnValue(getRow(0), ii) : null);
            }
            catch (RuntimeException e)
            {
                throw SnapshotException.rethrow(e);
            }
        }

        public ResultMetaData getResultMetaData()
        {
            return getResultMetaData(source);
        }

        public int getRowCount()
        {
            return objectIds.length;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            int index = (Integer) row;

            if (objects[index] == null)
                objects[index] = new ValueHolder(new Object[columns.length]);

            if (objects[index].values[columnIndex] == null)
            {
                // each column value is calculated separately, because I do not
                // want sorting to resolve all values in all rows

                // NULL_VALUE is used to keep track of column values which have
                // been calculated but returned a null value

                try
                {
                    IObject object = source.ctx.getSnapshot().getObject(objectIds[index]);
                    source.ctx.setSubject(object);
                    // Don't track progress here for reading the cell
                    IProgressListener old = source.ctx.getProgressListener();
                    source.ctx.setProgressListener(new SilentProgressListener(old));
                    List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
                    Object value = selectList.get(columnIndex).getExpression().compute(source.ctx);

                    source.ctx.setProgressListener(old);
                    objects[index].values[columnIndex] = value != null ? value : NULL_VALUE;
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }

            Object value = objects[index].values[columnIndex];
            return value == NULL_VALUE ? null : value;
        }

        public IContextObjectSet getContext(final Object row)
        {
            return new IContextObjectSet()
            {
                public int getObjectId()
                {
                    return objectIds[(Integer) row];
                }

                public int[] getObjectIds()
                {
                    return new int[] {getObjectId()};
                }

                public String getOQL()
                {
                    String alias = source.query.getFromClause().getAlias();
                    String alias2;
                    if (alias == null)
                        alias2 = ""; //$NON-NLS-1$
                    else
                        alias2 = " "+alias; //$NON-NLS-1$
                    SelectClause sc = source.query.getSelectClause();
                    if (sc.isRetainedSet())
                    {
                        // Remove asRetainedSet() as we just have a single object here
                        SelectClause sc2 = new SelectClause();
                        sc2.setAsObjects(sc.isAsObjects());
                        sc2.setSelectList(sc.getSelectList());
                        sc = sc2;
                    }
                    return "SELECT " + sc.toString() + " FROM OBJECTS " + getObjectId() + alias2; //$NON-NLS-1$ //$NON-NLS-2$
                }
            };
        }

        public Object getRow(int index)
        {
            return index;
        }

        public Column[] getColumns()
        {
            return columns;
        }

        public String getOQLQuery()
        {
            return source.query.toString();
        }
    }

    private static Column buildColumn(SelectItem column, Object columnValue)
    {
        String name = column.getName();
        if (name == null)
            name = column.getExpression().toString();

        Class<?> type = columnValue != null ? columnValue.getClass() : Object.class;
        return new Column(name, type).noTotals();
    }

    public static String OQLLiteral(Object subject)
    {
        // OQL literals (not Byte or Short)
        if (subject instanceof Character)
            return "'" + subject + "'"; //$NON-NLS-1$ //$NON-NLS-2$
        else if (subject instanceof String)
            return "\"" + subject + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        else if (subject instanceof Boolean || subject instanceof Integer
                        || subject instanceof Float || subject instanceof Double || subject == null)
            return String.valueOf(subject);
        else if (subject instanceof Long)
            return Long.toHexString((Long) subject) + "L)"; //$NON-NLS-1$
        else if (subject instanceof IObject)
            return "${snapshot}.getObject(" + Integer.toString(((IObject) subject).getObjectId()) + ")";  //$NON-NLS-1$//$NON-NLS-2$
        else
            return null;
    }

    public static String OQLforSubject(String select, Object subject, String alias)
    {
        String from;
        // OQL literals (not Byte or Short)
        String literal = OQLLiteral(subject);
        if (literal != null)
            from = "(" + literal + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        else if (subject instanceof AbstractCustomTableResultSet.RowMap)
        {
            AbstractCustomTableResultSet.RowMap rm = ( AbstractCustomTableResultSet.RowMap)subject;
            StringBuilder buf = new StringBuilder();
            for (Map.Entry<String,Object> e : rm.entrySet())
            {
                Object val = e.getValue();
                String v1;
                if (val == null)
                {
                    // "null" doesn't work
                    v1 = "toString(\"\").toCharArray()[0:-1]"; //$NON-NLS-1$
                }
                else
                {
                    v1 = OQLLiteral(val);
                }
                if (v1 == null)
                {
                    v1 = OQLforSubject("*", val, ""); //$NON-NLS-1$ //$NON-NLS-2$
                    if (v1 == null)
                        return null;
                    v1 = "(" + v1 + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (buf.length() == 0)
                    buf.append("SELECT "); //$NON-NLS-1$
                else
                    buf.append(", "); //$NON-NLS-1$
                buf.append(v1).append(" AS "); //$NON-NLS-1$
                String name = e.getKey();
                boolean quote = !name.matches("[A-Za-z$_][A-Za-z0-9$_]*"); //$NON-NLS-1$
                if (quote)
                    buf.append('"');
                buf.append(name);
                if (quote)
                    buf.append('"');
            }
            buf.append(" FROM OBJECTS (null)").append(alias); //$NON-NLS-1$
            from = buf.toString();
            if (select.equals("*")) //$NON-NLS-1$
                return from;
        }
        else
            return null;
        return "SELECT " + select + " FROM OBJECTS " + from + alias;  //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static class UnionResultSet extends AbstractCustomTableResultSet implements Result, IResultTable
    {
        private static class ValueHolder
        {
            Query query;
            IResultTable source;
            Object row;

            public ValueHolder(Query query, IResultTable source, Object row)
            {
                this.query = query;
                this.source = source;
                this.row = row;
            }

        }

        int size = 0;
        List<Query> queries = new ArrayList<Query>();
        List<CustomTableResultSet> resultSets = new ArrayList<CustomTableResultSet>();
        ArrayInt sizes = new ArrayInt(5);

        public UnionResultSet(OQLQueryImpl source)
        {
            super(source);
        }

        public void addResultSet(Query query, CustomTableResultSet resultSet)
        {
            queries.add(query);
            sizes.add(size);
            size += resultSet.getRowCount();
            resultSets.add(resultSet);
        }

        /**
         * Metadata for object columns for UNION queries
         */
        public ResultMetaData getResultMetaData()
        {
            Column columns[] = getColumns();
            int objectCount = source.ctx.getSnapshot().getSnapshotInfo().getNumberOfObjects();
            ResultMetaData.Builder builder = new ResultMetaData.Builder();
            int prov = 0;
            for (int ii = 0; ii < columns.length; ++ii)
            {
                // Find an example object for each column
                Object sample = null;
                IContextObject sampleContext = null;
                // Look in each table in case single columns have blanks
                for (IResultTable tab : resultSets)
                {
                    Object o = tab.getRowCount() > 0 ? tab.getColumnValue(tab.getRow(0), ii) : null;
                    if (o != null)
                    {
                        sample = o;
                        if (prov == 0)
                            sampleContext = tab.getContext(tab.getRow(0));
                        break;
                    }
                }
                // See if it is one or more objects from the dump
                if (getObjectId(sample) != -1 ||
                                sample instanceof Iterable<?> && ((Iterable<?>)sample).iterator().hasNext() && getObjectId(((Iterable<?>)sample).iterator().next()) != -1 ||
                                sample instanceof int[])
                {
                    // Also add the underlying row
                    if (prov == 0 && sampleContext != null)
                    {
                        String label;
                        FromClause fc = queries.get(0).getFromClause();
                        String alias = fc.getAlias();
                        if (alias != null)
                            label = alias;
                        else
                            label = fc.toString();
                        // Distinguish the select for all the columns
                        label = "SELECT ... " + label; //$NON-NLS-1$
                        // Use a null label to provide a default context without a sub-menu
                        builder.addContext(new ContextProvider(label) {
                            @Override
                            public IContextObject getContext(Object row)
                            {
                                return UnionResultSet.this.getContext(row);
                            }
                        });
                        ++prov;
                    };
                    int columnIndex = ii;
                    builder.addContext(new ContextProvider(columns[ii].getLabel()) {

                        @Override
                        public IContextObject getContext(Object row)
                        {
                            Object o = getColumnValue(row, columnIndex);
                            int objectId = getObjectId(o);
                            boolean goodContext = false;
                            if (objectId == -1  && !(o instanceof Iterable<?> || o instanceof int[]))
                            {
                                IContextObject cx = UnionResultSet.this.getContext(row);
                                if (cx != null)
                                {
                                    int selectId = cx.getObjectId();
                                    if (selectId != -1)
                                        goodContext = true;
                                    else if (cx instanceof IContextObjectSet)
                                    {
                                        if (((IContextObjectSet)cx).getOQL() != null)
                                            goodContext = true;
                                    }
                                }
                            }
                            if (objectId != -1 || goodContext)
                            {
                                return new IContextObjectSet() {

                                    @Override
                                    public int getObjectId()
                                    {
                                        return objectId;
                                    }

                                    @Override
                                    public int[] getObjectIds()
                                    {
                                        if (objectId != -1)
                                            return new int[] {objectId};
                                        else
                                            return new int[0];
                                    }

                                    @Override
                                    public String getOQL()
                                    {
                                        String alias = ((ValueHolder)row).query.getFromClause().getAlias();
                                        String alias2;
                                        if (alias == null)
                                            alias2 = ""; //$NON-NLS-1$
                                        else
                                            alias2 = " "+alias; //$NON-NLS-1$
                                        Query.SelectItem column = ((ValueHolder)row).query.getSelectClause().getSelectList().get(columnIndex);
                                        IContextObject cx = UnionResultSet.this.getContext(row);
                                        if (cx != null)
                                        {
                                            int selectId = cx.getObjectId();
                                            if (selectId != -1)
                                                return "SELECT "+column.toString()+" FROM OBJECTS " + selectId+alias2; //$NON-NLS-1$ //$NON-NLS-2$
                                            else if (cx instanceof IContextObjectSet)
                                            {
                                                IContextObjectSet cs = (IContextObjectSet)cx;
                                                String oqlsource = cs.getOQL();
                                                if (oqlsource != null)
                                                {
                                                    try
                                                    {
                                                        OQLQueryImpl q2 = new OQLQueryImpl(oqlsource);
                                                        q2.query.getSelectClause().setSelectList(Collections.emptyList());
                                                        String oqlsource2 = q2.query.toString();
                                                        return "SELECT "+column.toString()+" FROM OBJECTS (" + oqlsource2+") "+alias2; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                    }
                                                    catch (OQLParseException e)
                                                    {
                                                    }
                                                }
                                            }
                                        }
                                        if (objectId != -1)
                                            return OQL.forObjectId(objectId);
                                        else
                                            return OQLforSubject("*", o, ""); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                };
                            }
                            else if (o instanceof Iterable<?> || o instanceof int[])
                            {
                                return new IContextObjectSet() {

                                    @Override
                                    public int getObjectId()
                                    {
                                        IContextObject cx = UnionResultSet.this.getContext(row);
                                        if (cx != null)
                                            return cx.getObjectId();
                                        else
                                            return -1;
                                    }

                                    @Override
                                    public int[] getObjectIds()
                                    {
                                        if (o instanceof int[])
                                        {
                                            for (int ix : (int[])o)
                                            {
                                                if (ix < 0 || ix >= objectCount)
                                                    return new int[0];
                                            }
                                            return (int[])o;
                                        }
                                        ArrayInt ai = new ArrayInt();
                                        if (o instanceof Iterable<?>)
                                        {
                                            Iterable<?>l = (Iterable<?>)o;
                                            for (Object o : l)
                                            {
                                                int objectId = UnionResultSet.getObjectId(o);
                                                if (objectId != -1)
                                                {
                                                    ai.add(objectId);
                                                }
                                            }
                                        }
                                        return ai.toArray();
                                    }

                                    @Override
                                    public String getOQL()
                                    {
                                        String alias = ((ValueHolder)row).query.getFromClause().getAlias();
                                        String alias2;
                                        if (alias == null)
                                            alias2 = ""; //$NON-NLS-1$
                                        else
                                            alias2 = " "+alias; //$NON-NLS-1$
                                        Query.SelectItem column = ((ValueHolder)row).query.getSelectClause().getSelectList().get(columnIndex);
                                        int selectId = getObjectId();
                                        if (selectId != -1)
                                            return "SELECT "+column.toString()+" FROM OBJECTS " + selectId+alias2; //$NON-NLS-1$ //$NON-NLS-2$
                                        else
                                        {
                                            IContextObject cx =  UnionResultSet.this.getContext(row);
                                            if (cx instanceof IContextObjectSet)
                                            {
                                                IContextObjectSet cs = (IContextObjectSet)cx;
                                                String oqlsource = cs.getOQL();
                                                if (oqlsource != null)
                                                {
                                                    try
                                                    {
                                                        OQLQueryImpl q2 = new OQLQueryImpl(oqlsource);
                                                        q2.query.getSelectClause().setSelectList(Collections.emptyList());
                                                        String oqlsource2 = q2.query.toString();
                                                        return "SELECT " + column.toString() + " FROM OBJECTS (" + oqlsource2 + ") " + alias2; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                    }
                                                    catch (OQLParseException e)
                                                    {
                                                    }
                                                }
                                            }
                                        }
                                        return OQL.forObjectIds(getObjectIds());
                                    }
                                };
                            }
                            return null;
                        }

                    });
                    ++prov;
                }
            }
            return builder.build();
        }

        public Column[] getColumns()
        {
            return resultSets.get(0).getColumns();
        }

        public int getRowCount()
        {
            return size;
        }

        public Object getRow(int index)
        {
            int ii = findPageFor(index);
            Query query = queries.get(ii);
            IResultTable rs = resultSets.get(ii);
            Object value = rs.getRow(index - sizes.get(ii));
            return new ValueHolder(query, rs, value);
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            ValueHolder holder = (ValueHolder) row;
            return holder.source.getColumnValue(holder.row, columnIndex);
        }

        public IContextObject getContext(Object row)
        {
            ValueHolder holder = (ValueHolder) row;
            return holder.source.getContext(holder.row);
        }

        private int findPageFor(int rowNo)
        {
            int pageIndex = 0;
            while (pageIndex + 1 < sizes.size() && rowNo >= sizes.get(pageIndex + 1))
                pageIndex++;
            return pageIndex;
        }

        public String getOQLQuery()
        {
            StringBuilder buf = new StringBuilder();
            for (Result resultSet : resultSets)
                OQL.union(buf, resultSet.getOQLQuery());
            return buf.toString();
        }
    }

    private interface IntIterator
    {
        int nextInt();

        boolean hasNext();
    }

    private interface IntResult
    {
        void add(int id);

        void addAll(int[] ids);

        void addAll(IntResult intResult);

        int size();

        int[] toArray();

        boolean isEmpty();

        IntIterator iterator();
    }

    private static class IntArrayResult implements IntResult
    {
        ArrayInt arrayInt;

        public IntArrayResult(int capacity)
        {
            this.arrayInt = new ArrayInt(capacity);
        }

        public IntArrayResult(int[] initialValues)
        {
            this.arrayInt = new ArrayInt(initialValues);
        }

        public IntArrayResult(ArrayInt values)
        {
            this.arrayInt = values;
        }

        public void add(int id)
        {
            this.arrayInt.add(id);
        }

        public void addAll(int[] ids)
        {
            this.arrayInt.addAll(ids);
        }

        public void addAll(IntResult intResult)
        {
            if (intResult instanceof IntArrayResult)
            {
                this.arrayInt.addAll(((IntArrayResult) intResult).arrayInt);
            }
            else
            {
                for (IntIterator iter = intResult.iterator(); iter.hasNext();)
                    this.arrayInt.add(iter.nextInt());
            }
        }

        public int size()
        {
            return this.arrayInt.size();
        }

        public int[] toArray()
        {
            return this.arrayInt.toArray();
        }

        public boolean isEmpty()
        {
            return this.arrayInt.isEmpty();
        }

        public IntIterator iterator()
        {
            return new IntIterator()
            {

                int nextIndex = 0;

                public boolean hasNext()
                {
                    return nextIndex < arrayInt.size();
                }

                public int nextInt()
                {
                    return arrayInt.get(nextIndex++);
                }

            };
        }

    }

    private static class IntSetResult implements IntResult
    {
        SetInt setInt;

        public IntSetResult(int capacity)
        {
            this.setInt = new SetInt(capacity);
        }

        public void add(int id)
        {
            this.setInt.add(id);
        }

        public void addAll(int[] ids)
        {
            for (int id : ids)
                this.setInt.add(id);
        }

        public void addAll(IntResult intResult)
        {
            for (IntIterator iter = intResult.iterator(); iter.hasNext();)
                this.setInt.add(iter.nextInt());
        }

        public int size()
        {
            return this.setInt.size();
        }

        public int[] toArray()
        {
            return this.setInt.toArray();
        }

        public boolean isEmpty()
        {
            return this.setInt.isEmpty();
        }

        public IntIterator iterator()
        {
            return new IntIterator()
            {

                IteratorInt intEnum = setInt.iterator();

                public boolean hasNext()
                {
                    return intEnum.hasNext();
                }

                public int nextInt()
                {
                    return intEnum.next();
                }

            };
        }

    }

    // //////////////////////////////////////////////////////////////
    // oql execution
    // //////////////////////////////////////////////////////////////

    public OQLQueryImpl(EvaluationContext parent, Query query)
    {
        init(parent, query);
    }

    public OQLQueryImpl(String queryString) throws OQLParseException
    {
        try
        {
            OQLParser p = new OQLParser(new StringReader(queryString));
            p.setCompiler(new CompilerImpl());
            Query query = p.ParseQuery();

            init(null, query);
        }
        catch (ParseException e)
        {
            int line = e.currentToken.next.beginLine;
            int column = e.currentToken.next.beginColumn;

            // stack of no additional use but clutters UI
            throw new OQLParseException(e.getMessage(), null, line, column);
        }
        catch (TokenMgrError e)
        {
            String msg = e.getMessage();
            int line = 1, column = 1;

            Pattern pattern = Pattern.compile("Lexical error at line ([0-9]*), column ([0-9]*)\\..*");//$NON-NLS-1$
            Matcher matcher = pattern.matcher(msg);
            if (matcher.matches())
            {
                line = Integer.parseInt(matcher.group(1));
                column = Integer.parseInt(matcher.group(2));
            }

            // stack of no additional use but clutters UI
            throw new OQLParseException(msg, null, line, column);
        }
    }

    private void init(EvaluationContext parent, Query query)
    {
        this.query = query;

        this.ctx = new EvaluationContext(parent);

        if (query.getFromClause() != null)
            this.ctx.setAlias(query.getFromClause().getAlias());
    }

    private void initSnapshot(ISnapshot snapshot)
    {
        this.ctx.setSnapshot(snapshot);
    }

    public Object execute(ISnapshot snapshot, IProgressListener monitor) throws SnapshotException
    {
        if (snapshot == null)
            throw new NullPointerException(Messages.OQLQueryImpl_Error_MissingSnapshot);
        initSnapshot(snapshot);

        if (monitor == null)
        {
            if (this.ctx.getProgressListener() != null)
                monitor = new SilentProgressListener(this.ctx.getProgressListener());
            else
                monitor = new VoidProgressListener();
        }

        IProgressListener old = this.ctx.getProgressListener();
        this.ctx.setProgressListener(monitor);
        Object result = internalExecute(monitor);
        this.ctx.setProgressListener(old);
        return result instanceof IntResult ? ((IntResult) result).toArray() : result;
    }

    protected Object internalExecute(IProgressListener monitor) throws SnapshotException
    {
        int percentages[] = new int[(1 + (query.getUnionQueries() != null ? query.getUnionQueries().size() : 0))];
        Arrays.fill(percentages, 100);
        SimpleMonitor listener = new SimpleMonitor(query.toString(), monitor, percentages);

        // process query
        Object result = null;

        if (query.getFromClause().getSubSelect() != null)
        {
            result = doSubQuery(listener.nextMonitor());
        }
        else if (query.getFromClause().getCall() != null)
        {
            result = doMethodCall(listener.nextMonitor());
        }
        else
        {
            result = doFromItem(listener.nextMonitor());
        }

        if (query.getUnionQueries() != null)
        {
            result = union(listener, result);
        }

        monitor.done();
        return result;

    }

    private Object union(SimpleMonitor monitor, Object result) throws SnapshotException, OQLParseException
    {
        // one of those will hold the result
        UnionResultSet unionResultSet = null;
        IntResult unionIntResult = null;

        if (result instanceof CustomTableResultSet)
        {
            unionResultSet = new UnionResultSet(this);
            unionResultSet.addResultSet(query, (CustomTableResultSet) result);
        }
        else if (result instanceof IntResult)
        {
            IntResult intResult = (IntResult) result;
            unionIntResult = new IntArrayResult(intResult.size());
            unionIntResult.addAll(intResult);
        }
        else
        {
            // Create a dummy result to hold the query for redisplay later
            if (!(this.query.getSelectClause().getSelectList().isEmpty() || this.query.getSelectClause().isAsObjects()))
            {
                unionResultSet = new UnionResultSet(this);
                unionResultSet.addResultSet(query, new ResultSet(getSelectQuery(), new int[0]));
            }
        }

        for (Query q : query.getUnionQueries())
        {
            // check the compatibility of UNION queries
            if (this.query.getSelectClause().getSelectList().isEmpty() || this.query.getSelectClause().isAsObjects())
            {
                if (!q.getSelectClause().getSelectList().isEmpty() && !q.getSelectClause().isAsObjects()) { throw new SnapshotException(
                                MessageUtil.format(Messages.OQLQueryImpl_Error_QueryMustReturnObjects,
                                                new Object[] { q })); }
            }
            else
            {
                if (q.getSelectClause().getSelectList().size() != this.query.getSelectClause().getSelectList().size()) { throw new SnapshotException(
                                MessageUtil.format(Messages.OQLQueryImpl_Error_QueryMustHaveIdenticalSelectItems,
                                                new Object[] { q })); }
            }

            OQLQueryImpl unionQuery = new OQLQueryImpl(this.ctx, q);
            Object unionResult = unionQuery.internalExecute(monitor.nextMonitor());

            if (unionResult != null)
            {
                if (unionResultSet != null)
                {
                    unionResultSet.addResultSet(q, (CustomTableResultSet) unionResult);
                }
                else if (unionIntResult != null)
                {
                    unionIntResult.addAll((IntResult) unionResult);
                }
                // If no combined result has been created then get one now.
                else if (this.query.getSelectClause().getSelectList().isEmpty() || this.query.getSelectClause().isAsObjects())
                {
                    unionIntResult = new IntArrayResult(0);
                    unionIntResult.addAll((IntResult) unionResult);
                }
                else
                {
                    unionResultSet = new UnionResultSet(this);
                    unionResultSet.addResultSet(q, (CustomTableResultSet) unionResult);
                }
            }
            else
            {
                // Create a dummy result to hold the query for redisplay later
                if (unionResultSet != null)
                {
                    unionResultSet.addResultSet(q, new ResultSet(unionQuery, new int[0]));
                }
            }
        }

        return unionResultSet != null ? (unionResultSet.getRowCount() > 0 ? unionResultSet : null) : unionIntResult;
    }

    private Object doSubQuery(IProgressListener monitor) throws SnapshotException
    {
        int percentages[] = new int[] {300,100};
        SimpleMonitor listener = new SimpleMonitor(query.toString(), monitor, percentages);
        OQLQueryImpl subQuery = new OQLQueryImpl(this.ctx, query.getFromClause().getSubSelect());
        Object result = subQuery.internalExecute(listener.nextMonitor());
        monitor = listener.nextMonitor();

        if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses())
        {
            if (result == null)
            {
                return null;
            }
            else if (result instanceof AbstractCustomTableResultSet)
            {
                /*
                 * Experiment - flatten sub-select containing selects in select items
                 */
                return filterAndSelect((AbstractCustomTableResultSet)result, monitor);
            }
            else if (result instanceof Iterable)
            {
                List<Object> r = new ArrayList<Object>();

                for (Object obj : (Iterable<?>) result)
                {
                    if (accept(obj, monitor))
                        r.add(obj);
                    if (monitor.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                return r.isEmpty() ? null : select(r, monitor);
            }
            else if (result.getClass().isArray())
            {
                List<Object> r = new ArrayList<Object>();

                int length = Array.getLength(result);
                for (int ii = 0; ii < length; ii++)
                {
                    Object obj = Array.get(result, ii);
                    if (accept(obj, monitor))
                        r.add(obj);
                    if (monitor.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }
                return r.isEmpty() ? null : select(r, monitor);
            }
            else if (result instanceof IResultTable || result instanceof IResultTree)
            {
                return filterAndSelect((IStructuredResult)result, monitor);
            }
            else if (!(result instanceof IntResult))
            {
                return accept(result, monitor) ? select(result, monitor) : null;
            }
        }

        if (!(result instanceof IntResult))
            throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_MustReturnObjectList,
                            new Object[] { query.getFromClause().getSubSelect() }));

        IntResult baseSet = (IntResult) result;

        if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses())
        {
            return filterAndSelect(baseSet, monitor);
        }
        else
        {
            // result must contain only classes
            // convert and process as usual

            try
            {
                List<IClass> classes = new ArrayList<IClass>(baseSet.size());
                for (IntIterator iter = baseSet.iterator(); iter.hasNext();)
                {
                    int id = iter.nextInt();
                    IObject o = ctx.getSnapshot().getObject(id);
                    if (!(o instanceof IClass))
                        throw new SnapshotException(Messages.OQLQueryImpl_Error_ClassCastExceptionOccured);
                    IClass subjectClass = (IClass)o;
                    classes.add(subjectClass);
                    if (query.getFromClause().includeSubClasses())
                    {
                        classes.addAll(subjectClass.getAllSubclasses());
                    }
                }

                return filterClasses(monitor, classes);
            }
            catch (ClassCastException e)
            {
                throw new SnapshotException(Messages.OQLQueryImpl_Error_ClassCastExceptionOccured, e);
            }

        }
    }

    /**
     * Does the FROM clause of a select with classes/objects.
     * Input from:
     * <pre>
     * classname
     * classnamepattern
     * object ids
     * object addresses
     * </pre>
     * @param listener
     * @return
     * @throws SnapshotException
     */
    private Object doFromItem(IProgressListener listener) throws SnapshotException
    {
        Collection<IClass> classes = null;

        if (query.getFromClause().getClassName() != null)
        {
            // [a] class name given
            classes = ctx.getSnapshot().getClassesByName(query.getFromClause().getClassName(),
                            query.getFromClause().includeSubClasses());
        }
        else if (query.getFromClause().getClassNamePattern() != null)
        {
            // [b] class name pattern given
            try
            {
                Pattern pattern = Pattern.compile(PatternUtil.smartFix(query.getFromClause().getClassNamePattern(),
                                false));
                classes = ctx.getSnapshot().getClassesByName(pattern, query.getFromClause().includeSubClasses());
            }
            catch (PatternSyntaxException e)
            {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_InvalidClassNamePattern,
                                new Object[] { query.getFromClause().getClassNamePattern() }), e);
            }
        }
        else if (query.getFromClause().getObjectIds() != null)
        {
            if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses())
            {
                return filterAndSelect(new IntArrayResult(query.getFromClause().getObjectIds().toArray()), listener);
            }
            else
            {
                classes = new ArrayList<IClass>();
                for (IteratorInt ee = query.getFromClause().getObjectIds().iterator(); ee.hasNext();)
                {
                    IObject subject = ctx.getSnapshot().getObject(ee.next());
                    if (subject instanceof IClass)
                    {
                        IClass subjectClass = (IClass) subject;
                        classes.add(subjectClass);
                        if (query.getFromClause().includeSubClasses())
                        {
                            classes.addAll(subjectClass.getAllSubclasses());
                        }
                    }
                    else
                    {
                        throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Errot_IsNotClass,
                                        new Object[] { Long.toHexString(subject.getObjectAddress()) }));
                    }

                }
            }
        }
        else if (query.getFromClause().getObjectAddresses() != null)
        {
            ArrayLong objectAddresses = query.getFromClause().getObjectAddresses();
            IntArrayResult result = new IntArrayResult(objectAddresses.size());

            for (IteratorLong ee = objectAddresses.iterator(); ee.hasNext();)
                result.add(ctx.getSnapshot().mapAddressToId(ee.next()));

            if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses())
            {
                return filterAndSelect(result, listener);
            }
            else
            {
                classes = new ArrayList<IClass>();
                for (IntIterator iter = result.iterator(); iter.hasNext();)
                {
                    IObject subject = ctx.getSnapshot().getObject(iter.nextInt());
                    if (subject instanceof IClass)
                    {
                        IClass subjectClass = (IClass) subject;
                        classes.add(subjectClass);
                        if (query.getFromClause().includeSubClasses())
                        {
                            classes.addAll(subjectClass.getAllSubclasses());
                        }
                    }
                    else
                    {
                        throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Errot_IsNotClass,
                                        new Object[] { Long.toHexString(subject.getObjectAddress()) }));
                    }

                }
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        if (classes == null || classes.isEmpty())
            return null;

        return filterClasses(listener, classes);
    }

    /**
     * Does a method call in a FROM clause of a select.
     * <pre>
     * OBJECTS
     *   null
     *   Iterable
     *   array
     * NONE/INSTANCEOF
     *   null
     *   Iterable/array
     *      null
     *      Integer
     *      int[]
     *      IClass
     * </pre>
     * @param listener
     * @return
     * @throws SnapshotException
     */
    private Object doMethodCall(IProgressListener listener) throws SnapshotException
    {
        int percentages[] = new int[] {300,100};
        SimpleMonitor smlistener = new SimpleMonitor(query.toString(), listener, percentages);
        Expression method = query.getFromClause().getCall();
        this.ctx.setSubject(this.ctx.getSnapshot());
        IProgressListener old = ctx.getProgressListener();
        this.ctx.setProgressListener(smlistener.nextMonitor());
        Object result = method.compute(this.ctx);
        this.ctx.setProgressListener(old);
        listener = smlistener.nextMonitor();

        if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses())
        {
            if (result == null)
            {
                return null;
            }
            else if (result instanceof Iterable)
            {
                List<Object> r = new ArrayList<Object>();

                for (Object obj : (Iterable<?>) result)
                {
                    if (accept(obj, listener))
                        r.add(obj);
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                return r.isEmpty() ? null : select(r, listener);
            }
            else if (result.getClass().isArray())
            {
                List<Object> r = new ArrayList<Object>();

                int length = Array.getLength(result);
                for (int ii = 0; ii < length; ii++)
                {
                    Object obj = Array.get(result, ii);
                    if (accept(obj, listener))
                        r.add(obj);
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                return r.isEmpty() ? null : select(r, listener);
            }
            else if (result instanceof IResultTable || result instanceof IResultTree)
            {
                return filterAndSelect((IStructuredResult)result, listener);
            }
            else
            {
                return accept(result, listener) ? select(result, listener) : null;
            }
        }
        else
        {
            // result must contain only classes
            // convert and process as usual

            List<IClass> classes = new ArrayList<IClass>();

            try
            {
                if (result == null)
                {
                    return null;
                }
                else if (result instanceof Iterable)
                {
                    for (Object obj : (Iterable<?>) result)
                    {
                        if (obj == null)
                        {
                            // allowed value
                        }
                        else if (obj instanceof Integer)
                        {
                            IObject o =  this.ctx.getSnapshot().getObject(((Integer) obj).intValue());
                            if (!(o instanceof IClass))
                                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass,
                                                query.getFromClause().toString(), o.getClass().getName()));
                            IClass subjectClass = (IClass)o;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses())
                            {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                        else if (obj instanceof int[])
                        {
                            for (int id : (int[]) obj)
                            {
                                IObject o =  this.ctx.getSnapshot().getObject(id);
                                if (!(o instanceof IClass))
                                    throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass,
                                                    query.getFromClause().toString(), o.getClass().getName()));
                                IClass subjectClass = (IClass)o;
                                classes.add(subjectClass);
                                if (query.getFromClause().includeSubClasses())
                                {
                                    classes.addAll(subjectClass.getAllSubclasses());
                                }
                            }
                        }
                        else if (obj instanceof IClass)
                        {
                            IClass subjectClass = (IClass) obj;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses())
                            {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                        else
                        {
                            throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass, query.getFromClause().toString(), obj.getClass().getName()));
                        }
                    }
                }
                else if (result.getClass().isArray())
                {
                    int length = Array.getLength(result);
                    for (int ii = 0; ii < length; ii++)
                    {
                        Object obj = Array.get(result, ii);
                        if (obj == null)
                        {
                            // allowed value
                        }
                        else if (obj instanceof Integer)
                        {
                            IObject o = this.ctx.getSnapshot().getObject(((Integer) obj).intValue());
                            if (!(o instanceof IClass))
                                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass,
                                                query.getFromClause().toString(), o.getClass().getName()));
                            IClass subjectClass = (IClass)o;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses())
                            {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                        else if (obj instanceof int[])
                        {
                            for (int id : (int[]) obj)
                            {
                                IClass subjectClass = (IClass) this.ctx.getSnapshot().getObject(id);
                                classes.add(subjectClass);
                                if (query.getFromClause().includeSubClasses())
                                {
                                    classes.addAll(subjectClass.getAllSubclasses());
                                }
                            }
                        }
                        else if (obj instanceof IClass)
                        {
                            IClass subjectClass = (IClass) obj;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses())
                            {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                        else
                        {
                            throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass, query.getFromClause().toString(), obj.getClass().getName()));
                        }
                    }
                }
                else if (result instanceof IClass)
                {
                    IClass subjectClass = (IClass) result;
                    classes.add(subjectClass);
                    if (query.getFromClause().includeSubClasses())
                    {
                        classes.addAll(subjectClass.getAllSubclasses());
                    }
                }
                else
                {
                    throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass, query.getFromClause().toString(), result.getClass().getName()));
                }
            }
            catch (ClassCastException e)
            {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass,
                                query.getFromClause().toString(), e.getMessage()), e);
            }

            return filterClasses(listener, classes);
        }
    }

    private Object filterClasses(IProgressListener listener, Collection<IClass> classes) throws SnapshotException
    {
        if (query.getFromClause().includeObjects())
        {
            listener.beginTask(Messages.OQLQueryImpl_SelectingObjects, classes.size());

            IntResult filteredSet = createIntResult(classes.size());
            for (IClass clasz : classes)
            {
                if (accept(clasz.getObjectId(), listener))
                    filteredSet.add(clasz.getObjectId());

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
                listener.worked(1);
            }
            return filteredSet.isEmpty() ? null : select(filteredSet, listener);

        }
        else
        {
            // Keep track of progress via classes or objects
            int work = classes.size();
            boolean countObjs = work < 1000;
            if (countObjs)
            {
                for (IClass clasz : classes)
                {
                    work += clasz.getNumberOfObjects();
                }
                if (work < classes.size())
                {
                    // Original way
                    countObjs = false;
                    work = classes.size();
                }
            }

            listener.beginTask(Messages.OQLQueryImpl_CollectingObjects, work);

            IntResult filteredSet = createIntResult(classes.size() * 100);
            for (IClass clasz : classes)
            {
                listener.subTask(MessageUtil.format(Messages.OQLQueryImpl_CheckingClass,
                                new Object[] { clasz.getName() }));

                int[] ids = clasz.getObjectIds();
                for (int id : ids)
                {
                    if (accept(id, listener))
                        filteredSet.add(id);

                    if (countObjs)
                        listener.worked(1);
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
                if (!countObjs)
                    listener.worked(1);
            }

            return filteredSet.isEmpty() ? null : select(filteredSet, listener);
        }
    }

    private boolean accept(int objectId, IProgressListener mon) throws SnapshotException
    {
        if (query.getWhereClause() == null)
            return true;

        return accept(ctx.getSnapshot().getObject(objectId), mon);
    }

    private boolean accept(Object object, IProgressListener mon) throws SnapshotException
    {
        if (query.getWhereClause() == null)
            return true;

        ctx.setSubject(object);
        // We don't track work for the WHERE clause
        IProgressListener old = ctx.getProgressListener();
        ctx.setProgressListener(new SilentProgressListener(mon));

        Boolean result = (Boolean) query.getWhereClause().compute(ctx);

        ctx.setProgressListener(old);

        return result == null ? false : result.booleanValue();
    }

    private Object filterAndSelect(IntResult objectIds, IProgressListener listener) throws SnapshotException
    {
        IntResult filteredSet = createIntResult(objectIds.size());

        for (IntIterator iter = objectIds.iterator(); iter.hasNext();)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            int id = iter.nextInt();
            if (accept(id, listener))
                filteredSet.add(id);
        }

        return filteredSet.isEmpty() ? null : select(filteredSet, listener);
    }

    private Object select(IntResult objectIds, IProgressListener listener) throws SnapshotException
    {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet())
        {
            objectIds = new IntArrayResult(ctx.getSnapshot().getRetainedSet(objectIds.toArray(), new SilentProgressListener(listener)));
        }

        if (select.getSelectList().isEmpty())
        {
            return objectIds;
        }
        else if (select.isAsObjects())
        {
            ResultSet temp = new ResultSet(getSelectQuery(), objectIds.toArray());
            IntResult r = createIntResult(objectIds.size());
            convertToObjects(temp, r, listener);
            return r;
        }
        else
        {
            if (select.isDistinct())
            {
                int ids[] = objectIds.toArray();
                ResultSet r1 = new ResultSet(getSelectQuery(), ids);
                ArrayInt aa =  distinctList(r1, listener);
                // Reuse the array from remapping list to the list of object ids
                for (int i = 0; i < aa.size(); ++i)
                {
                    aa.set(i, ids[aa.get(i)]);
                }
                return new ResultSet(getSelectQuery(), aa.toArray());
            }
            else
            {
                return new ResultSet(getSelectQuery(), objectIds.toArray());
            }
        }
    }

    private Object select(List<Object> objects, IProgressListener listener) throws SnapshotException
    {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet()) { return select(convertToObjectIds(objects), listener); }

        if (select.getSelectList().isEmpty())
        {
            return objects;
        }
        else if (select.isAsObjects())
        {
            AbstractCustomTableResultSet temp = new ObjectResultSet(getSelectQuery(), objects);
            IntResult r = createIntResult(temp.getRowCount());
            convertToObjects(temp, r, listener);
            return r;
        }
        else
        {
            if (select.isDistinct())
            {
                // Prefilter the list, we also make distinct on column values
                Set<Object>so = new LinkedHashSet<Object>(objects);
                Object objs[] = so.toArray(new Object[so.size()]);
                so.clear();
                AbstractCustomTableResultSet s1 = new ObjectResultSet(getSelectQuery(), objs);
                ArrayInt aa = distinctList(s1, listener);
                Object objs2[] = new Object[aa.size()];
                for (int i = 0; i < aa.size(); ++i)
                {
                    objs2[i] = objs[aa.get(i)];
                }
                return new ObjectResultSet(getSelectQuery(), objs2);
            }
            else
            {
                return new ObjectResultSet(getSelectQuery(), objects);
            }
        }
    }

    private Object filterAndSelect(IStructuredResult result, IProgressListener listener) throws SnapshotException
    {
        List<Object> r = new ArrayList<Object>();
        IStructuredResult irt = (IStructuredResult)result;
        List<?>elements = irt instanceof IResultTree ? ((IResultTree)irt).getElements() : null;
        int count = irt instanceof IResultTable ? ((IResultTable)irt).getRowCount() : elements.size();
        listener.beginTask(Messages.OQLQueryImpl_Selecting, count);
        for (int ii = 0; ii < count; ii++)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            Object rowobj = new AbstractCustomTableResultSet.RowMap(irt, ii);
            // Don't use any context object for the select
            //IContextObject ic = irt.getContext(row);
            //IObject o = ctx.getSnapshot().getObject(ic.getObjectId());
            if (accept(rowobj, listener))
                r.add(rowobj);
            listener.worked(1);
        }

        listener.done();
        return r.isEmpty() ? null : select(r, listener);
    }

    private Object filterAndSelect(AbstractCustomTableResultSet result, IProgressListener listener) throws SnapshotException
    {
        List<Object> r = new ArrayList<Object>();
        listener.beginTask(Messages.OQLQueryImpl_Selecting, result.getRowCount() * 2);
        IProgressListener old = this.ctx.getProgressListener();
        this.ctx.setProgressListener(new SilentProgressListener(listener));
        for (AbstractCustomTableResultSet.RowMap rowobj : result)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            /*
             * Possible flatten
             */
            int maxlen = -1;
            for (Object v : rowobj.values())
            {
                if (v instanceof List)
                {
                    int ll = ((List<?>)v).size();
                    if (ll > maxlen)
                        maxlen = ll;
                }
                else if (v != null && v.getClass().isArray())
                {
                    int ll = Array.getLength(v);
                    if (ll > maxlen)
                        maxlen = ll;
                };
            }
            listener.worked(1);

            if (maxlen >= 0)
            {
                // Create a row even if the list/array is empty, will be replaced with null
                if (maxlen == 0)
                    maxlen = 1;
                for (int i = 0; i < maxlen; ++i)
                {
                    int ix = i;
                    AbstractCustomTableResultSet.RowMap rm2 = new AbstractCustomTableResultSet.RowMap(result, rowobj.index, i) {
                        public Object get(Object key)
                        {
                            Object v = rowobj.get(key);
                            if (v instanceof List)
                            {
                                if (ix >= ((List<?>)v).size())
                                    return null;
                                return ((List<?>)v).get(ix);
                            }
                            else if (v != null && v.getClass().isArray())
                            {
                                if (ix >= Array.getLength(v))
                                    return null;
                                return Array.get(v, ix);
                            }
                            else
                            {
                                return v;
                            }
                        }
                    };
                    if (accept(rm2, listener))
                        r.add(rm2);
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                }
            }
            else
            {
                if (accept(rowobj, listener))
                    r.add(rowobj);
            }
            listener.worked(1);
        }
        this.ctx.setProgressListener(old);
        listener.done();
        return r.isEmpty() ? null : select(r, listener);
    }

    private Object select(Object object, IProgressListener listener) throws SnapshotException
    {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet()) { return select(convertToObjectIds(Arrays.asList(new Object[] { object })),
                        listener); }

        if (select.getSelectList().isEmpty())
        {
            return object;
        }
        else if (select.isAsObjects())
        {
            AbstractCustomTableResultSet temp = new ObjectResultSet(getSelectQuery(), Arrays.asList(new Object[] { object }));
            IntResult r = createIntResult(temp.getRowCount());
            convertToObjects(temp, r, listener);
            return r;
        }
        else
        {
            return new ObjectResultSet(getSelectQuery(), Arrays.asList(new Object[] { object }));
        }
    }

    /**
     * Get an query without the union clause for results before applying the union clause.
     * @return A new query without the union clause.
     */
    private OQLQueryImpl getSelectQuery() {
        Query q2 = new Query();
        q2.setSelectClause(query.getSelectClause());
        q2.setFromClause(query.getFromClause());
        q2.setWhereClause(query.getWhereClause());
        OQLQueryImpl qi = new OQLQueryImpl(ctx, q2);
        return qi;
    }

    private IntArrayResult convertToObjectIds(List<?> objects) throws SnapshotException
    {
        ArrayInt a = new ArrayInt();

        for (Object object : objects)
        {
            if (object == null)
            {
                // valid value
            }
            else if (object instanceof Integer)
            {
                a.add(((Integer) object).intValue());
            }
            else if (object instanceof IObject)
            {
                a.add(((IObject) object).getObjectId());
            }
            else if (object instanceof int[])
            {
                a.addAll((int[]) object);
            }
            else
            {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_CannotCalculateRetainedSet,
                                new Object[] { object }));
            }
        }

        return new IntArrayResult(a);
    }

    private void convertToObjects(CustomTableResultSet set, IntResult resultSet, IProgressListener listener)
                    throws SnapshotException
    {
        if (set.getColumns().length != 1) { throw new SnapshotException(MessageUtil.format(
                        Messages.OQLQueryImpl_Error_QueryCannotBeConverted, new Object[] { set.getOQLQuery() })); }

        // We don't track work for converting objects
        IProgressListener old = ctx.getProgressListener();
        ctx.setProgressListener(new SilentProgressListener(listener));
        int count = set.getRowCount();
        for (int ii = 0; ii < count; ii++)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            Object rowObject = set.getColumnValue(set.getRow(ii), 0);
            /**
             * Convert arrays or collections of IObjects
             * or ints or int arrays or IObjects
             * or longs or arrays of longs into object ids.
             */
            Iterable<?> it;
            if (rowObject instanceof Iterable)
            {
                it = (Iterable<?>)rowObject;
            }
            else if (rowObject instanceof Object[])
            {
                it = Arrays.asList((Object[])rowObject);
            }
            else
            {
                it = Collections.singleton(rowObject);
            }
            for (Object object : it)
            {
                if (object == null)
                {
                    // acceptable value -> do nothing
                }
                else if (object instanceof Integer)
                {
                    resultSet.add(((Integer) object).intValue());
                }
                else if (object instanceof int[])
                {
                    resultSet.addAll((int[]) object);
                }
                else if (object instanceof IObject)
                {
                    resultSet.add(((IObject) object).getObjectId());
                }
                else if (object instanceof Long)
                {
                    long addr = ((Long) object).longValue();
                    if (addr != 0)
                    {
                        int id = ctx.getSnapshot().mapAddressToId(addr);
                        resultSet.add(id);
                    }
                }
                else if (object instanceof long[])
                {
                    for (long addr : (long[])object)
                    {
                        if (addr != 0)
                        {
                            int id = ctx.getSnapshot().mapAddressToId(addr);
                            resultSet.add(id);
                        }
                    }
                }
                else
                {
                    throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ResultMustReturnObjectList,
                                    new Object[] { set.getOQLQuery(), String.valueOf(rowObject) }));
                }
            }
        }
        ctx.setProgressListener(old);
    }

    private IntResult createIntResult(int capacity)
    {
        return query.getSelectClause().isDistinct() || query.getSelectClause().isRetainedSet() ? new IntSetResult(
                        capacity) : new IntArrayResult(capacity);
    }

    @Override
    public String toString()
    {
        return query.toString();
    }

}
