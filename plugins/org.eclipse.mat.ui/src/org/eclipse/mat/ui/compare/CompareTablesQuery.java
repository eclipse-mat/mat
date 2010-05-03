/*******************************************************************************
 * Copyright (c) 2010 SAP AG. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.mat.ui.compare;

import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.util.IProgressListener;

import com.ibm.icu.text.DecimalFormat;

public class CompareTablesQuery implements IQuery
{
	@Argument
	public IResultTable[] tables;

	@Argument(isMandatory = false)
	public Mode mode = Mode.ABSOLUTE;

	public enum Mode
	{
		ABSOLUTE("ABSOLUTE"), // //$NON-NLS-1$
		DIFF_TO_FIRST("DIFF_TO_FIRST"), // //$NON-NLS-1$
		DIFF_TO_PREVIOUS("DIFF_TO_PREVIOUS"); //$NON-NLS-1$

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

	public IResult execute(IProgressListener listener) throws Exception
	{
		if (tables == null) return null;

		if (tables.length == 1) return tables[0];

		IResultTable base = tables[0];
		Column[] baseColumns = base.getColumns();
		Column key = baseColumns[0];

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

		return new TableComparisonResult(mergeKeys(), key, attributes, mode);
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
		private List<ComparedColumn> comparedColumns;
		private List<ComparedColumn> displayedColumns;
		private Column[] columns;
		private Mode mode;

		public TableComparisonResult(List<ComparedRow> rows, Column key, List<ComparedColumn> comparedColumns, Mode mode)
		{
			this.key = key;
			this.mode = mode;
			this.rows = rows;
			this.comparedColumns = comparedColumns;
			updateColumns();
			setMode(mode);
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

			int comparedColumnIdx = (columnIndex - 1) / tables.length;
			int tableIdx = (columnIndex - 1) % tables.length;

			if (tableIdx == 0) return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);

			switch (mode)
			{
			case ABSOLUTE:
				return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);
			case DIFF_TO_FIRST:
				return getDiffToFirst(cr, comparedColumnIdx, tableIdx);
			case DIFF_TO_PREVIOUS:
				return getDiffToPrevious(cr, comparedColumnIdx, tableIdx);

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
			// TODO Auto-generated method stub
			return null;
		}

		public ResultMetaData getResultMetaData()
		{
			// TODO Auto-generated method stub
			return null;
		}

		private Object getAbsoluteValue(ComparedRow cr, int comparedColumnIdx, int tableIdx)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			return tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
		}

		private Object getDiffToFirst(ComparedRow cr, int comparedColumnIdx, int tableIdx)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			Object value = tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
			Object firstTableValue = getAbsoluteValue(cr, comparedColumnIdx, 0);

			if (value == null && firstTableValue == null) return null;

			if (value == null && firstTableValue instanceof Number) return null;

			if (value instanceof Number && firstTableValue == null) return value;

			if (value instanceof Number && firstTableValue instanceof Number)
			{
				return computeDiff((Number) firstTableValue, (Number) value);
			}
			return null;
		}

		private Object getDiffToPrevious(ComparedRow cr, int comparedColumnIdx, int tableIdx)
		{
			Object tableRow = cr.getRows()[tableIdx];
			if (tableRow == null) return null;

			int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
			if (tableColumnIdx == -1) return null;

			Object value = tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
			Object previousTableValue = getAbsoluteValue(cr, comparedColumnIdx, tableIdx - 1);

			if (value == null && previousTableValue == null) return null;

			if (value == null && previousTableValue instanceof Number) return null;

			if (value instanceof Number && previousTableValue == null) return value;

			if (value instanceof Number && previousTableValue instanceof Number)
			{
				return computeDiff((Number) previousTableValue, (Number) value);
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

		public URL getIcon(Object row)
		{
			return ISharedImages.class.getResource(ISharedImages.COMPARE);
		}

		public Mode getMode()
		{
			return mode;
		}

		public void setMode(Mode mode)
		{
			this.mode = mode;
			setFormatter();
		}

		private void setFormatter()
		{
			int i = 1;
			Format formatter = new DecimalFormat("+#,##0;-#,##0"); //$NON-NLS-1$

			for (ComparedColumn comparedColumn : displayedColumns)
			{
				Column c = comparedColumn.description;
				for (int j = 0; j < comparedColumn.getColumnIndexes().length; j++)
				{
					if (mode != Mode.ABSOLUTE && j > 0)
					{
						columns[i].formatting(formatter);
					}
					else
					{
						columns[i].formatting(c.getFormatter());
					}
					i++;
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
						result.add(new Column(c.getLabel() + " #" + (j + 1), c.getType(), c.getAlign(), c.getSortDirection(), c.getFormatter(), null)); //$NON-NLS-1$
					}
				}
			}

			columns = result.toArray(new Column[result.size()]);
			setFormatter();
		}
	}

}
