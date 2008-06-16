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
package org.eclipse.mat.inspections.tests;

import java.text.MessageFormat;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.impl.query.CommandLine;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.test.ITestResult;
import org.eclipse.mat.util.IProgressListener;


@Name("Test Above Threshold")
@Category(Category.HIDDEN)
public class TableThresholdTest implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public String command;

    @Argument
    public long threshold;

    @Argument
    public String columnName;

    public IResult execute(IProgressListener listener) throws Exception
    {
        IResult result = CommandLine.execute(snapshot, command, listener);
        if (!(result instanceof IResultTable))
            throw new SnapshotException(MessageFormat.format("Does not return a result table: {0}", command));

        IResultTable table = (IResultTable) result;
        Column[] columns = table.getColumns();

        int columnIndex = -1;
        for (int ii = 0; ii < columns.length; ii++)
        {
            if (columnName.equals(columns[ii].getLabel()))
            {
                columnIndex = ii;
                break;
            }
        }

        if (columnIndex < 0)
            throw new SnapshotException(MessageFormat.format("Column {0} not found in result of {1}", columnName,
                            command));

        ArrayInt aboveThreshold = new ArrayInt();

        for (int index = 0; index < table.getRowCount(); index++)
        {
            Object row = table.getRow(index);
            Object value = table.getColumnValue(row, columnIndex);

            if (toLong(value) > threshold)
                aboveThreshold.add(index);
        }

        return new FilteredTable(table, columns, aboveThreshold.toArray());
    }

    private long toLong(Object value)
    {
        // TODO: try to parse String as long?
        return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
    }

    class FilteredTable implements IResultTable, ITestResult
    {
        IResultTable source;
        Column[] columns;
        int[] aboveThreshold;

        FilteredTable(IResultTable source, Column[] columns, int[] aboveThreshold)
        {
            this.source = source;
            this.columns = columns;
            this.aboveThreshold = aboveThreshold;
        }

        public int getRowCount()
        {
            return aboveThreshold.length;
        }

        public Object getRow(int rowId)
        {
            return source.getRow(aboveThreshold[rowId]);
        }

        public Column[] getColumns()
        {
            return columns;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            return source.getColumnValue(row, columnIndex);
        }

        public IContextObject getContext(Object row)
        {
            return source.getContext(row);
        }

        public ResultMetaData getResultMetaData()
        {
            return source.getResultMetaData();
        }

        public Status getStatus()
        {
            return aboveThreshold.length > 0 ? Status.WARNING : Status.SUCCESS;
        }

    }
}
