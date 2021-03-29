/*******************************************************************************
 * Copyright (c) 2021 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.Alignment;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;

@Renderer(target = "txt", result = { IResultTree.class, IResultTable.class, TextResult.class })
public class TextOutputter extends OutputterBase implements IOutputter
{
    @Override
    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        embedd(context, result, writer);
    }

    @Override
    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        if (result instanceof TextResult)
        {
            writer.append(((TextResult) result).getText());
            writer.append(LINE_SEPARATOR);
        }
        else if (result instanceof RefinedTable)
        {
            new RefinedTableTextEmitter(context, result, writer).doCopy();
        }
        else if (result instanceof RefinedTree)
        {
            new RefinedTreeTextEmitter(context, result, writer).doCopy();
        }
    }

    private static abstract class StructuredResultTextEmitter extends TextEmitter
    {
        protected static final int ALIGN_LEFT = 0;
        protected static final int ALIGN_CENTER = 1;
        protected static final int ALIGN_RIGHT = 2;
        protected Context context;
        protected Writer writer;
        protected Filter.ValueConverter[] filter;

        public StructuredResultTextEmitter(Context context, IResult result, Writer writer)
        {
            this.context = context;
            this.writer = writer;
            initialize(result);
        }

        protected abstract void initialize(IResult result);

        @Override
        protected void append(String string)
        {
            try
            {
                writer.append(string);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void done()
        {}

        @Override
        protected boolean shouldAddNextLine(Object item)
        {
            return false;
        }

        @Override
        protected boolean shouldSuppressLineBreak(Object item)
        {
            return false;
        }

        @Override
        protected void findColumnOrderAndAlignment(Object[] genericColumns)
        {
            Column[] columns = (Column[]) genericColumns;

            align = new int[columns.length];
            order = new int[columns.length];
            filter = new Filter.ValueConverter[columns.length];

            for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
            {
                filter[columnIndex] = (Filter.ValueConverter) columns[columnIndex].getData(Filter.ValueConverter.class);
                order[columnIndex] = columnIndex;
                Alignment alignment = columns[columnIndex].getAlign();
                switch (alignment)
                {
                    case CENTER:
                        align[columnIndex] = ALIGN_CENTER;
                        break;
                    case RIGHT:
                        align[columnIndex] = ALIGN_RIGHT;
                        break;
                    default:
                        align[columnIndex] = ALIGN_LEFT;
                        break;
                }

                // TODO it's a bit complicated to handle invisible columns
                // because the columnIndex may be used to lookup values
                // in the underlying IResult, so we would need to add a mapping
                //
                // if (context.isColumnVisible(columnIndex))
            }
        }

        @Override
        protected String getColumnName(Object column)
        {
            return ((Column) column).getLabel();
        }

        @Override
        protected int getColumnLength(Object[] items, Object[] objColumns, int columnNumber)
        {
            int result = getColumnName(objColumns[columnNumber]).length();
            for (int i = 0; i < items.length; i++)
            {
                String rowValue = getItemValue(items[i], columnNumber);
                if (rowValue.length() > result)
                {
                    result = rowValue.length();
                }
            }
            return result;
        }

        @Override
        protected boolean shouldProcessChild(Object child)
        {
            return false;
        }

        @Override
        protected String getDisplayableRowValue(Object item)
        {
            return null;
        }

        @Override
        protected Object[] getChildren(Object item)
        {
            return null;
        }

        @Override
        protected String getDisplayableColumnValueInSimpleStructure(Object item)
        {
            return getDisplayableRowValue(item);
        }

        @Override
        protected boolean isAlignmentRight(int alignment)
        {
            return alignment == ALIGN_RIGHT;
        }

        @Override
        protected boolean isAlignmentCenter(int alignment)
        {
            return alignment == ALIGN_CENTER;
        }
    }

    private static class RefinedTableTextEmitter extends StructuredResultTextEmitter
    {
        private RefinedTable table;

        public RefinedTableTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void initialize(IResult result)
        {
            this.table = (RefinedTable) result;
        }

        @Override
        protected Object[] getItems()
        {
            int rows = context.hasLimit() ? Math.min(table.getRowCount(), context.getLimit()) : table.getRowCount();
            Object[] result = new Object[rows];
            for (int i = 0; i < rows; i++)
            {
                result[i] = table.getRow(i);
            }
            return result;
        }

        @Override
        protected Object[] getColumns()
        {
            return table.getColumns();
        }

        @Override
        protected String getItemValue(Object item, int columnIndex)
        {
            return getStringValue(table.getColumnValue(item, columnIndex), filter[columnIndex]);
        }

        @Override
        protected String getDisplayableColumnValue(Object item, int index)
        {
            return getStringValue(table.getColumnValue(item, index), filter[index]);
        }

        @Override
        protected boolean isExpanded(Object item)
        {
            return table.isExpanded(item);
        }
    }

    private static class RefinedTreeTextEmitter extends StructuredResultTextEmitter
    {
        private RefinedTree tree;

        public RefinedTreeTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void initialize(IResult result)
        {
            tree = (RefinedTree) result;
        }

        @Override
        protected Object[] getItems()
        {
            List<?> elements = tree.getElements();
            int rows = context.hasLimit() ? Math.min(elements.size(), context.getLimit()) : elements.size();
            Object[] result = new Object[rows];
            for (int i = 0; i < rows; i++)
            {
                result[i] = elements.get(i);
            }
            return result;
        }

        @Override
        protected Object[] getColumns()
        {
            return tree.getColumns();
        }

        @Override
        protected String getItemValue(Object item, int columnIndex)
        {
            return getStringValue(tree.getColumnValue(item, columnIndex), filter[columnIndex]);
        }

        @Override
        protected String getDisplayableColumnValue(Object item, int index)
        {
            return getStringValue(tree.getColumnValue(item, index), filter[index]);
        }

        @Override
        protected boolean isExpanded(Object item)
        {
            return tree.isExpanded(item);
        }
    }
}
