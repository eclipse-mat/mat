/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG and IBM Corporation. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 * Andrew Johnson - conversion to proper query, set operations via contexts
 ******************************************************************************/
package org.eclipse.mat.internal.snapshot.inspections;

import java.net.URISyntaxException;
import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Menu.Entry;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

@Icon("/META-INF/icons/compare.gif")
@Menu({ @Entry(options = "-setop ALL")
})
public class CompareTablesQuery implements IQuery
{
	@Argument
	public IResultTable[] tables;

    @Argument
    public IQueryContext queryContext;
	
	@Argument
	public IQueryContext[] queryContexts;

	@Argument(isMandatory = false)
	public Mode mode = Mode.ABSOLUTE;

	@Argument(isMandatory = false)
	public Operation setOp = Operation.NONE;

    private boolean[] sameSnapshot;

	public enum Mode
	{
		ABSOLUTE("ABSOLUTE"), // //$NON-NLS-1$
		DIFF_TO_FIRST("DIFF_TO_FIRST"), // //$NON-NLS-1$
		DIFF_TO_PREVIOUS("DIFF_TO_PREVIOUS"), //$NON-NLS-1$
	    DIFF_RATIO_TO_FIRST("DIFF_RATIO_TO_FIRST"), // //$NON-NLS-1$
	    DIFF_RATIO_TO_PREVIOUS("DIFF_RATIO_TO_PREVIOUS"); //$NON-NLS-1$

		String label;

		private Mode(String label)
		{
			this.label = label;
		}

		public String toString()
		{
			return label;
		}
	}

	public enum Operation
	{
	    NONE,
	    ALL,
	    INTERSECTION,
	    UNION,
	    SYMMETRIC_DIFFERENCE,
	    DIFFERENCE
	}

	public IResult execute(IProgressListener listener) throws Exception
	{
		if (tables == null) return null;

		// Length 1 table is valid, and we need to process it in case it is from a different snapshot

		IResultTable base = tables[0];
		Column[] baseColumns = base.getColumns();
		Column key = baseColumns[0];

		sameSnapshot = new boolean[tables.length];
		ISnapshot sn = (ISnapshot)queryContext.get(ISnapshot.class, null);
		for (int i = 0; i < tables.length; ++i)
		{
		    sameSnapshot[i] = (queryContexts[i] == null || sn.equals((ISnapshot)queryContexts[i].get(ISnapshot.class, null)));
		}

		List<ComparedColumn> attributes = new ArrayList<ComparedColumn>();
		for (int i = 1; i < baseColumns.length; i++)
		{
			int[] indexes = new int[tables.length];
			for (int j = 0; j < indexes.length; j++)
			{
				indexes[j] = getColumnIndex(baseColumns[i].getLabel(), tables[j]);
			}
			attributes.add(new ComparedColumn(baseColumns[i], indexes, true));
		}

		return new TableComparisonResult(mergeKeys(), key, attributes, mode, setOp);
	}

	private int getColumnIndex(String name, IResultTable table)
	{
		Column[] columns = table.getColumns();
		for (int i = 0; i < columns.length; i++)
		{
			if (columns[i].getLabel().equals(name)) return i;
		}
		return -1;
	}

	private List<ComparedRow> mergeKeys()
	{
		Map<Object, Object[]> map = new HashMap<Object, Object[]>();
		for (int i = 0; i < tables.length; i++)
		{
			for (int j = 0; j < tables[i].getRowCount(); j++)
			{
				Object row = tables[i].getRow(j);
				Object key = tables[i].getColumnValue(row, 0);
				Object[] rows = map.get(key);
				if (rows == null)
				{
					rows = new Object[tables.length];
					map.put(key, rows);
				}
				rows[i] = row;
			}
		}

		List<ComparedRow> result = new ArrayList<ComparedRow>(map.size());
		for (Map.Entry<Object, Object[]> entry : map.entrySet())
		{
			result.add(new ComparedRow(entry.getKey(), entry.getValue()));
		}

		return result;
	}

	public class ComparedColumn
	{
		Column description;
		int[] columnIndexes;
		boolean displayed;

		public ComparedColumn(Column description, int[] columnIndexes, boolean displayed)
		{
			super();
			this.displayed = displayed;
			this.description = description;
			this.columnIndexes = columnIndexes;
		}

		public Column getDescription()
		{
			return description;
		}

		public void setDescription(Column description)
		{
			this.description = description;
		}

		public int[] getColumnIndexes()
		{
			return columnIndexes;
		}

		public void setColumnIndexes(int[] columnIndexes)
		{
			this.columnIndexes = columnIndexes;
		}

		public boolean isDisplayed()
		{
			return displayed;
		}

		public void setDisplayed(boolean displayed)
		{
			this.displayed = displayed;
		}

	}

	class ComparedRow
	{
		Object key;
		Object[] rows;

		public ComparedRow(Object key, Object[] rows)
		{
			super();
			this.key = key;
			this.rows = rows;
		}

		public Object getKey()
		{
			return key;
		}

		public void setKey(Object key)
		{
			this.key = key;
		}

		public Object[] getRows()
		{
			return rows;
		}

		public void setRows(Object[] rows)
		{
			this.rows = rows;
		}
	}

	public class TableComparisonResult implements IResultTable, IIconProvider
	{
		private Column key;
		private List<ComparedRow> rows;
		/** each compared column is a column from the original table, and is displayed as several actual columns, one or more for each table */
		private List<ComparedColumn> comparedColumns;
		/** each displayed column is a column from the original table, and is displayed as several actual columns, one or more for each table */
		private List<ComparedColumn> displayedColumns;
		/** Actual columns displayed */
		private Column[] columns;
		private Mode mode;
		private Operation setOp;

		public TableComparisonResult(List<ComparedRow> rows, Column key, List<ComparedColumn> comparedColumns, Mode mode, Operation setOp)
		{
			this.key = key;
			this.mode = mode;
			this.rows = rows;
			this.comparedColumns = comparedColumns;
			updateColumns();
			setMode(mode);
			this.setOp = setOp;
		}

		public Object getRow(int rowId)
		{
			return rows.get(rowId);
		}

		public int getRowCount()
		{
			return rows.size();
		}

		public List<ComparedColumn> getComparedColumns()
		{
			return comparedColumns;
		}

		public void setComparedColumns(List<ComparedColumn> comparedColumns)
		{
			this.comparedColumns = comparedColumns;
		}

		public Object getColumnValue(Object row, int columnIndex)
		{
			ComparedRow cr = (ComparedRow) row;
			if (columnIndex == 0) return cr.getKey();

            /*
             * Each compared column has several subcolumns for data from each
             * table. The first column is the key and is common for all tables,
             * so there is only one actual column. For absolute or difference
             * modes there is one column per table. For percentage modes there
             * is one column for the first table and two for the rest.
             */
            int subCols = mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS ? tables.length
                            + tables.length - 1 : tables.length;
            int comparedColumnIdx = (columnIndex - 1) / subCols;
            int tableIdx = (columnIndex - 1) % subCols;

			if (tableIdx == 0) return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);

			switch (mode)
			{
			case ABSOLUTE:
				return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);
			case DIFF_TO_FIRST:
				return getDiffToFirst(cr, comparedColumnIdx, tableIdx, false);
			case DIFF_TO_PREVIOUS:
				return getDiffToPrevious(cr, comparedColumnIdx, tableIdx, false);
            case DIFF_RATIO_TO_FIRST:
                return getDiffToFirst(cr, comparedColumnIdx, (tableIdx + 1) / 2, tableIdx % 2 == 0);
            case DIFF_RATIO_TO_PREVIOUS:
                return getDiffToPrevious(cr, comparedColumnIdx, (tableIdx + 1) / 2, tableIdx % 2 == 0);

			default:
				break;
			}

			return null;

		}

		public Column[] getColumns()
		{
			return columns;
		}

		public IContextObject getContext(Object row)
		{
            // Find a context from one of the tables
            IContextObject ret = null;
            for (int i = 0; i < tables.length && ret == null; ++i)
            {
                ret = getContextFromTable(i, row);
            }
            return ret;
		}

		public ResultMetaData getResultMetaData()
		{
            ResultMetaData.Builder bb = new ResultMetaData.Builder();
            int previous = -1;
            for (int i = 0; i < tables.length; ++i)
            {
                if (!sameSnapshot[i])
                    continue;

                if (setOp != Operation.NONE && previous >= 0)
                {
                    for (int op = 0; op < 5; ++op)
                    {
                        if (setOp == Operation.INTERSECTION && op != 0) continue;
                        if (setOp == Operation.UNION && op != 1) continue;
                        if (setOp == Operation.SYMMETRIC_DIFFERENCE && op != 2) continue;
                        if (setOp == Operation.DIFFERENCE && op != 3) continue;
                        final int op1 = op;
                        // intersection, union, symmetric difference, difference, difference
                        String title1;
                        final LinkedList<Integer> toDo = new LinkedList<Integer>();
                        if (mode == Mode.ABSOLUTE)
                        {
                            toDo.add(previous);
                            if (op == 4)
                            {
                                for (int k = previous + 1; k <= i; ++k)
                                {
                                    toDo.addFirst(k);
                                }
                            }
                            else
                            {
                                for (int k = previous + 1; k <= i; ++k)
                                {
                                    toDo.add(k);
                                }
                            }
                        }
                        else
                        {
                            if (op == 4)
                            {
                                toDo.add(i);
                                toDo.add(previous);
                            }
                            else
                            {
                                toDo.add(previous);
                                toDo.add(i);
                            }
                        }
                        // Convert the list of tables to a readable menu item
                        if (toDo.size() == 2)
                        {
                            switch (op)
                            {
                            case 0:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_IntersectionOf2, toDo.get(0)+1, toDo.get(1)+1);
                                break;
                            case 1:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_UnionOf2, toDo.get(0)+1, toDo.get(1)+1);
                                break;
                            case 2:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceOf2, toDo.get(0)+1, toDo.get(1)+1);
                                break;                                
                            case 3:
                            case 4:
                            default:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_DifferenceOf2, toDo.get(0)+1, toDo.get(1)+1);
                                break;
                            }
                        } else {
                            String soFar;
                            switch (op)
                            {
                                case 0:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_IntersectionFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 1:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_UnionFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 2:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 3:
                                case 4:
                                default:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_DifferenceFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                            }
                            for (int t = 2; t < toDo.size() - 1; ++t)
                            {
                                switch (op)
                                {
                                case 0:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_IntersectionMiddle, soFar, toDo.get(t)+1);
                                break;
                                case 1:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_UnionMiddle, soFar, toDo.get(t)+1);
                                    break;
                                case 2:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceMiddle, soFar, toDo.get(t)+1);
                                    break;
                                case 3:
                                case 4:
                                default:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_DifferenceMiddle, soFar, toDo.get(t)+1);
                                    break;
                                }
                            }
                            int t = toDo.size() - 1;
                            switch (op)
                            {
                            case 0:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_IntersectionLast, soFar, toDo.get(t)+1);
                            break;
                            case 1:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_UnionLast, soFar, toDo.get(t)+1);
                                break;
                            case 2:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceLast, soFar, toDo.get(t)+1);
                                break;
                            case 3:
                            case 4:
                            default:
                                title1 = MessageUtil.format(Messages.CompareTablesQuery_DifferenceLast, soFar, toDo.get(t)+1);
                                break;
                            }
                        }

                        ContextProvider prov2 = new ContextProvider(title1)
                        {
                            public URL getIcon()
                            {
                                switch (op1)
                                {
                                case 0:
                                    return Icons.getURL("set_intersection.gif"); //$NON-NLS-1$
                                case 1:
                                    return Icons.getURL("set_union.gif"); //$NON-NLS-1$
                                case 2:
                                    return Icons.getURL("set_symmetric_difference.gif"); //$NON-NLS-1$
                                case 3:
                                    return Icons.getURL("set_differenceA.gif"); //$NON-NLS-1$
                                case 4:
                                    return Icons.getURL("set_differenceB.gif"); //$NON-NLS-1$
                                }
                                return null;
                            }
                            public IContextObject getContext(final Object row)
                            {
                                int nullContexts = 0;
                                for (int i = 0; i < toDo.size(); ++i)
                                {
                                    if (getContextFromTable(i, row) == null)
                                    {
                                        ++nullContexts;
                                    }
                                    else
                                    {
                                        break;
                                    }
                                }
                                // If all the contexts were null then skip this generated context too
                                if (nullContexts == toDo.size())
                                    return null;
                                // First non-null context
                                final IContextObject cb = getContextFromTable(nullContexts, row);
                                
                                return new IContextObjectSet()
                                {
                                    public int getObjectId()
                                    {
                                        return cb.getObjectId();
                                    }

                                    public String getOQL()
                                    {
                                        LinkedList<Integer> toDo2 = new LinkedList<Integer>(toDo);
                                        switch (op1)
                                        {
                                            // Intersection
                                            case 0:
                                                String resultOQL0 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL0 == null)
                                                            resultOQL0 = oql;
                                                        else
                                                        {
                                                            resultOQL0 = OQLintersection(resultOQL0, oql);
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL0;
                                            // Union
                                            case 1:
                                                StringBuilder resultOQL = new StringBuilder();
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        OQL.union(resultOQL, oql);
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                // Remove duplicates
                                                resultOQL.insert(0, "SELECT DISTINCT OBJECTS @objectId FROM OBJECTS ("); //$NON-NLS-1$
                                                resultOQL.append(")"); //$NON-NLS-1$
                                                return resultOQL.toString();
                                            // Symmetric Difference
                                            case 2:
                                                String resultOQL2 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL2 == null)
                                                            resultOQL2 = oql;
                                                        else
                                                        {
                                                            // A^B = A&~B | ~A&B
                                                            StringBuilder sb = new StringBuilder();
                                                            sb.append(OQLexcept(resultOQL2, oql));
                                                            OQL.union(sb, OQLexcept(oql, resultOQL2));
                                                            resultOQL2 = sb.toString();
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL2;
                                            // Difference
                                            case 3:
                                            case 4:
                                                String resultOQL3 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL3 == null)
                                                            resultOQL3 = oql;
                                                        else
                                                        {
                                                            resultOQL3 = OQLexcept(resultOQL3, oql);
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL3;
                                        }
                                        return null;
                                    }

                                    /**
                                     * Simulate EXCEPT
                                     * @param oql1
                                     * @param oql2
                                     * @return
                                     */
                                    private String OQLexcept(String oql1, String oql2)
                                    {
                                        //SELECT * FROM OBJECTS (a) where @objectid in (b)
                                        oql1 = "SELECT * FROM OBJECTS ("+oql1+")" +  //$NON-NLS-1$//$NON-NLS-2$
                                            " WHERE @objectId not in ("+oql2+")";  //$NON-NLS-1$//$NON-NLS-2$
                                        return oql1;
                                    }

                                    /**
                                     * Simulate INTERSECT
                                     * @param oql1
                                     * @param oql2
                                     * @return
                                     */
                                    private String OQLintersection(String oql1, String oql2)
                                    {
                                        //SELECT * FROM OBJECTS (a) where @objectid in (b)
                                        oql1 = "SELECT * FROM OBJECTS (" + oql1 + ")" + //$NON-NLS-1$//$NON-NLS-2$
                                                        " WHERE @objectId in (" + oql2 + ")"; //$NON-NLS-1$//$NON-NLS-2$
                                        return oql1;
                                    }

                                    /**
                                     * Calculate the object ids
                                     */
                                    public int[] getObjectIds()
                                    {
                                        LinkedList<Integer> toDo2 = new LinkedList<Integer>(toDo);
                                        int j = toDo2.remove();
                                        final IContextObject cb = getContextFromTable(j, row);
                                        int b[] = getObjectIdsFromContext(cb);
                                        ArrayInt bb;
                                        if (b == null)
                                        {
                                            bb = new ArrayInt();
                                        }
                                        else
                                        {
                                            bb = new ArrayInt(b);
                                            bb.sort();
                                        }

                                        while (!toDo2.isEmpty())
                                        {
                                            j = toDo2.remove();
                                            IContextObject ca = getContextFromTable(j, row);
                                            int a[] = getObjectIdsFromContext(ca);
                                            ArrayInt aa;
                                            if (a == null)
                                            {
                                                aa = new ArrayInt();
                                            }
                                            else
                                            {
                                                aa = new ArrayInt(a);
                                                aa.sort();
                                            }
                                            switch (op1)
                                            {
                                                case 0:
                                                    bb = intersectionArray(aa, bb);
                                                    break;
                                                case 1:
                                                    bb = unionArray(aa, bb);
                                                    break;
                                                case 2:
                                                    bb = symdiffArray(aa, bb);
                                                    break;
                                                case 3:
                                                case 4:
                                                    bb = diffArray(aa, bb);
                                                    break;
                                            }
                                        }

                                        final int res[] = bb.toArray();
                                        return res;
                                    }

                                    /**
                                     * Union of aa from bb
                                     * 
                                     * @param aa
                                     * @param bb
                                     * @return
                                     */
                                    private ArrayInt unionArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return bb;
                                        if (bb.size() == 0)
                                            return aa;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                            {
                                                cc.add(aa.get(j));
                                                ++j;
                                            }
                                            cc.add(bb.get(i));
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Remove aa from bb
                                     * 
                                     * @param aa
                                     * @param bb
                                     * @return
                                     */
                                    private ArrayInt diffArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (bb.size() == 0)
                                            return bb;
                                        if (aa.size() == 0)
                                            return bb;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                                ++j;
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                            else
                                            {
                                                cc.add(bb.get(i));
                                            }
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Intersection of aa and bb
                                     * 
                                     * @param aa
                                     * @param bb
                                     * @return
                                     */
                                    private ArrayInt intersectionArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return aa;
                                        if (bb.size() == 0)
                                            return bb;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                                ++j;
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                cc.add(bb.get(i));
                                                ++j;
                                            }
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Symmetric difference of aa and bb
                                     * 
                                     * @param aa
                                     * @param bb
                                     * @return
                                     */
                                    private ArrayInt symdiffArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return bb;
                                        if (bb.size() == 0)
                                            return aa;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                            {
                                                cc.add(aa.get(j));
                                                ++j;
                                            }
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                            else
                                            {
                                                cc.add(bb.get(i));
                                            }
                                        }
                                        return cc;
                                    }

                                    private int[] getObjectIdsFromContext(IContextObject b)
                                    {
                                        int bobjs[];
                                        if (b instanceof IContextObjectSet)
                                        {
                                            bobjs = ((IContextObjectSet) b).getObjectIds();
                                        }
                                        else if (b != null)
                                        {
                                            int id = b.getObjectId();
                                            if (id >= 0)
                                                bobjs = new int[] { id };
                                            else
                                                bobjs = null;
                                        }
                                        else
                                        {
                                            bobjs = null;
                                        }
                                        return bobjs;
                                    }

                                    private String getOQLfromContext(IContextObject b)
                                    {
                                        String oql;
                                        if (b instanceof IContextObjectSet)
                                        {
                                            oql = ((IContextObjectSet) b).getOQL();
                                        }
                                        else if (b != null)
                                        {
                                            int id = b.getObjectId();
                                            if (id >= 0)
                                                oql = "SELECT * FROM OBJECTS "+id; //$NON-NLS-1$
                                            else
                                                oql = null;
                                        }
                                        else
                                        {
                                            oql = null;
                                        }
                                        return oql;
                                    }
                                };
                            }
                        };
                        bb.addContext(prov2);
                    }
                }
                if (setOp == Operation.NONE || setOp == Operation.ALL)
                {
                    final int i2 = i;
                    String title = MessageUtil.format(Messages.CompareTablesQuery_Table, i + 1);
                    ContextProvider prov = new ContextProvider(title)
                    {
                        public IContextObject getContext(Object row)
                        {
                            return getContextFromTable(i2, row);
                        }
                    };
                    bb.addContext(prov);
                }
                if (mode == Mode.DIFF_TO_PREVIOUS || mode == Mode.DIFF_RATIO_TO_PREVIOUS ||previous == -1)
                    previous = i;
            }
            return bb.build();
		}

		private Object getAbsoluteValue(ComparedRow cr, int comparedColumnIdx, int tableIdx)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			return tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
		}

        private Object percentDivide(Number d1, Number d2)
        {
            if (d1.doubleValue() == 0.0
                            && d2.doubleValue() == 0.0
                            && (d2 instanceof Integer || d2 instanceof Long || d2 instanceof Byte || d2 instanceof Short))
            {
                // Helps sorting if 0 -> 0 maps to +0% rather than NaN%
                return Double.valueOf(0.0);
            }
            else
            {
                return Double.valueOf(d1.doubleValue() / d2.doubleValue());
            }
        }
		
		private Object getDiffToFirst(ComparedRow cr, int comparedColumnIdx, int tableIdx, boolean ratio)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			Object value = tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
			Object firstTableValue = getAbsoluteValue(cr, comparedColumnIdx, 0);

			if (value == null && firstTableValue == null) return null;

			if (value == null && firstTableValue instanceof Number) return null;

			if (value instanceof Number && firstTableValue == null)
			{
                return ratio ? null : value;
			}

			if (value instanceof Number && firstTableValue instanceof Number)
			{
                Object ret = computeDiff((Number) firstTableValue, (Number) value);
                if (ratio && ret instanceof Number)
                {
                    return percentDivide((Number)ret, (Number)firstTableValue); 
                }
                else
                {
                    return ret;
                }
			}
			return null;
		}

		private Object getDiffToPrevious(ComparedRow cr, int comparedColumnIdx, int tableIdx, boolean ratio)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			Object value = tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
			Object previousTableValue = getAbsoluteValue(cr, comparedColumnIdx, tableIdx - 1);

			if (value == null && previousTableValue == null) return null;

			if (value == null && previousTableValue instanceof Number) return null;

			if (value instanceof Number && previousTableValue == null)
			{
                return ratio ? null : value;
			}

			if (value instanceof Number && previousTableValue instanceof Number)
			{
                Object ret = computeDiff((Number) previousTableValue, (Number) value);
                if (ratio && ret instanceof Number)
                {
                    return percentDivide((Number)ret, (Number)previousTableValue); 
                }
                else
                {
                    return ret;
                }
			}
			return null;
		}

		private Object computeDiff(Number o1, Number o2)
		{
			if (o1 instanceof Long && o2 instanceof Long) return (o2.longValue() - o1.longValue());

			if (o1 instanceof Integer && o2 instanceof Integer) return (o2.intValue() - o1.intValue());

			if (o1 instanceof Short && o2 instanceof Short) return (o2.shortValue() - o1.shortValue());

			if (o1 instanceof Byte && o2 instanceof Byte) return (o2.byteValue() - o1.byteValue());

			if (o1 instanceof Float && o2 instanceof Float) return (o2.floatValue() - o1.floatValue());

			if (o1 instanceof Double && o2 instanceof Double) return (o2.doubleValue() - o1.doubleValue());

			return null;
		}

		/**
		 * Get the icon for the row.
		 * Chose the icon from the underlying tables if they all agree,
		 * others choose a special compare icon.
		 */
		public URL getIcon(Object row)
		{
			URL ret = null;
            final ComparedRow cr = (ComparedRow) row;
            // Find the rows from the tables
            boolean foundIcon = false;
            for (int i = 0; i < tables.length; ++i)
            {
                Object r = cr.getRows()[i];
                if (r != null)
                {
                    URL tableIcon = (tables[i] instanceof IIconProvider) ? ((IIconProvider) tables[i]).getIcon(r)
                                    : null;
                    if (foundIcon)
                    {
                        try
                        {
                            if (ret == null ? tableIcon != null : tableIcon == null
                                            || !ret.toURI().equals(tableIcon.toURI()))
                            {
                                // Mismatch, so use compare icon instead
                                ret = Icons.getURL("compare.gif"); //$NON-NLS-1$
                                break;
                            }
                        }
                        catch (URISyntaxException e)
                        {
                            // URI problem, so use compare icon instead
                            ret = Icons.getURL("compare.gif"); //$NON-NLS-1$
                            break;
                        }
                    }
                    else
                    {
                        ret = tableIcon;
                        foundIcon = true;
                    }
                }
            }
			return ret;
		}

		public Mode getMode()
		{
			return mode;
		}

		public void setMode(Mode mode)
		{
			this.mode = mode;
			updateColumns();
		}

		private void setFormatter()
		{
			int i = 1;
			Format formatter = new DecimalFormat("+#,##0;-#,##0"); //$NON-NLS-1$
            NumberFormat formatterPercent = NumberFormat.getPercentInstance();
            if (formatterPercent instanceof DecimalFormat)
            {
                ((DecimalFormat)formatterPercent).setPositivePrefix("+"); //$NON-NLS-1$
            }

			for (ComparedColumn comparedColumn : displayedColumns)
			{
				Column c = comparedColumn.description;
				for (int j = 0; j < comparedColumn.getColumnIndexes().length; j++)
				{
                    if (mode != Mode.ABSOLUTE && j > 0)
                    {
                        if (!columns[i].getCalculateTotals() && c.getCalculateTotals())
                        {
                            // Set the totals mode
                            columns[i] = new Column(columns[i].getLabel(), columns[i].getType(), columns[i].getAlign(),
                                            columns[i].getSortDirection(), columns[i].getFormatter(),
                                            columns[i].getComparator());
                        }
                        if (c.getFormatter() instanceof DecimalFormat)
                        {
                            DecimalFormat fm = ((DecimalFormat) c.getFormatter().clone());
                            fm.setPositivePrefix("+"); //$NON-NLS-1$
                            columns[i].formatting(fm);
                        }
                        else
                        {
                            columns[i].formatting(formatter);
                        }
                    }
                    else
                    {
                        if (!columns[i].getCalculateTotals() && c.getCalculateTotals())
                        {
                            // Set the totals mode
                            columns[i] = new Column(columns[i].getLabel(), columns[i].getType(), columns[i].getAlign(),
                                            columns[i].getSortDirection(), columns[i].getFormatter(),
                                            columns[i].getComparator());
                        }
                        columns[i].formatting(c.getFormatter());
                    }
					i++;
					if ((mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS) && j > 0)
                    {
                        columns[i].formatting(formatterPercent);
                        columns[i].noTotals();
                        i++;
                    }
				}
			}
		}

		public void updateColumns()
		{
			List<Column> result = new ArrayList<Column>();
			result.add(new Column(key.getLabel(), key.getType(), key.getAlign(), null, key.getFormatter(), null));

			displayedColumns = new ArrayList<ComparedColumn>();

			for (ComparedColumn comparedColumn : comparedColumns)
			{
				Column c = comparedColumn.description;
				if (comparedColumn.isDisplayed())
				{
					displayedColumns.add(comparedColumn);
					for (int j = 0; j < comparedColumn.getColumnIndexes().length; j++)
					{
                        String label;
                        final int prev = mode == Mode.DIFF_TO_PREVIOUS || mode == Mode.DIFF_RATIO_TO_PREVIOUS ? j - 1 : 0;
                        if (j == 0 || mode == Mode.ABSOLUTE)
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnAbsolute, c.getLabel(), j);
                        }
                        else
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnDifference,
                                                       c.getLabel(), j,
                                                       prev);
                        }
                        result.add(new Column(label, c.getType(), c.getAlign(), c.getSortDirection(), c.getFormatter(),
                                        null));
                        // For percentage modes also add a percent change column for subsequent tables
                        if (j > 0 && (mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS))
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnPercentDifference,
                                                       c.getLabel(), j, 
                                                       prev);
                            result.add(new Column(label, c.getType(), c.getAlign(), c.getSortDirection(), c
                                            .getFormatter(), null));
                        }
					}
				}
			}

			columns = result.toArray(new Column[result.size()]);
			setFormatter();
		}

        IContextObject getContextFromTable(int i, Object row)
        {
            if (!sameSnapshot[i])
                return null;
            final ComparedRow cr = (ComparedRow) row;
            IContextObject ret = null;
            Object r = cr.getRows()[i];
            if (r != null)
            {
                ret = tables[i].getContext(r);
            }
            return ret;
        }
    }
}
