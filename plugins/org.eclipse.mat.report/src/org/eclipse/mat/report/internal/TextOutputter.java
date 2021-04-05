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
import java.util.Arrays;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.Alignment;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

@Renderer(target = "txt", result = { IResultTree.class, IResultTable.class, TextResult.class, IResultPie.class })
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
            TextResult tr = (TextResult) result;
            String s = tr.getText();
            if (tr.isHtml())
                s = toTextFromHTML(s);
            writer.append(s);
            writer.append(LINE_SEPARATOR);
        }
        else if (result instanceof IResultTable)
        {
            new RefinedTableTextEmitter(context, result, writer).doCopy();
        }
        else if (result instanceof IResultTree)
        {
            new RefinedTreeTextEmitter(context, result, writer).doCopy();
        }
        else if (result instanceof IResultPie)
        {
            IResultPie pie = (IResultPie)result;
            writer.append(MessageUtil.format(Messages.TextOutputter_PieChart, pie.getSlices().size()));
            writer.append(LINE_SEPARATOR);
            writer.append(LINE_SEPARATOR);
            int sl = 1;
            for (Slice s : pie.getSlices())
            {
                writer.append(MessageUtil.format(Messages.TextOutputter_Slice, sl, s.getValue(), toTextFromHTML(s.getDescription())));
                writer.append(LINE_SEPARATOR);
                writer.append(LINE_SEPARATOR);
                ++sl;
            }
        }
    }

    private String toTextFromHTML(String s)
    {
        {
            // quick and dirty replacement, enough for leak suspects
            s = s.replaceAll("<b>", "").replace("</b>", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            s = s.replaceAll("<br>", LINE_SEPARATOR); //$NON-NLS-1$
            s = s.replaceAll("<br/>", LINE_SEPARATOR); //$NON-NLS-1$
            s = s.replaceAll("<p>", LINE_SEPARATOR).replaceAll("</p>", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            s = s.replaceAll("<a [^>]+>", "").replaceAll("</a>", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            s = s.replaceAll("&quot;", "\""); //$NON-NLS-1$ //$NON-NLS-2$
            s = s.replaceAll("&apos;", "\""); //$NON-NLS-1$ //$NON-NLS-2$
            s = s.replaceAll("&lt;", "<"); //$NON-NLS-1$ //$NON-NLS-2$
            s = s.replaceAll("&gt;", ">"); //$NON-NLS-1$ //$NON-NLS-2$
            s = s.replaceAll("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return s;
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

            int orderIndex = 0;
            for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
            {
                filter[columnIndex] = (Filter.ValueConverter) columns[columnIndex].getData(Filter.ValueConverter.class);
                /*
                 * It's a bit complicated to handle invisible columns
                 * because the columnIndex may be used to lookup values.
                 * Omit the column from the ordering.
                 */
                if (context.isColumnVisible(columnIndex))
                    order[orderIndex++] = columnIndex;
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
            }
            if (orderIndex < order.length)
                order = Arrays.copyOf(order, orderIndex);
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
        private IResultTable table;

        public RefinedTableTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void initialize(IResult result)
        {
            this.table = (IResultTable) result;
        }

        @Override
        protected Object[] getItems()
        {
            int rows = context.hasLimit() ? Math.min(table.getRowCount(), context.getLimit()) : table.getRowCount();
            int rows1 = context.isTotalsRowVisible() && table instanceof RefinedStructuredResult ? rows + 1 : rows;
            int rows0 = 0;
            if (table instanceof RefinedStructuredResult && ((RefinedStructuredResult) table).hasActiveFilter())
            {
                ++rows1;
                rows0 = 1;
            }
            Object[] result = new Object[rows1];
            for (int i = 0; i < rows; i++)
            {
                result[rows0 + i] = table.getRow(i);
            }
            if (table instanceof RefinedStructuredResult)
            {
                RefinedStructuredResult rsr = (RefinedStructuredResult)table;
                if (rows0 == 1)
                    result[0] = rsr.getFilter();
                if (rows1 > rows0 + rows)
                {
                    List<Object> subList = Arrays.asList(result).subList(rows0, rows0 + rows);
                    final TotalsRow totalsRow = rsr.buildTotalsRow(subList);
                    totalsRow.setVisibleItems(rows);
                    rsr.calculateTotals(subList, totalsRow, new VoidProgressListener());
                    result[rows1 - 1] = totalsRow;
                }
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
            if (item instanceof Filter[])
            {
                Filter f[] = (Filter[])item;
                String fv = f[columnIndex].getCriteria();
                if (fv == null)
                    fv = ""; //$NON-NLS-1$
                return fv;
            }
            if (item instanceof TotalsRow)
            {
                TotalsRow tr = (TotalsRow)item;
                return tr.getLabel(columnIndex);
            }
            if (table instanceof RefinedStructuredResult)
                return ((RefinedStructuredResult)table).getFormattedColumnValue(item, columnIndex);
            return getStringValue(table.getColumnValue(item, columnIndex), filter[columnIndex]);
        }

        @Override
        protected String getDisplayableColumnValue(Object item, int index)
        {
            if (item instanceof Filter[])
            {
                Filter f[] = (Filter[])item;
                String fv = f[index].getCriteria();
                if (fv == null)
                    fv = ""; //$NON-NLS-1$
                return fv;
            }
            if (item instanceof TotalsRow)
            {
                TotalsRow tr = (TotalsRow)item;
                return tr.getLabel(index);
            }
            if (table instanceof RefinedStructuredResult)
            {
                String v = ((RefinedStructuredResult)table).getFormattedColumnValue(item, index);
                IDecorator dec = table.getColumns()[index].getDecorator();
                if (dec != null)
                {
                    String prefix = dec.prefix(item);
                    String suffix = dec.suffix(item);
                    if (prefix != null)
                        if (suffix != null)
                            v = prefix + " " + v + " " + suffix; //$NON-NLS-1$ //$NON-NLS-2$
                        else
                            v = prefix + " " + v; //$NON-NLS-1$
                    else
                        if (suffix != null)
                            v = v + " " + suffix; //$NON-NLS-1$
                }
                return v;
            }
            return getStringValue(table.getColumnValue(item, index), filter[index]);
        }

        @Override
        protected boolean isExpanded(Object item)
        {
            return false;
        }
    }

    private static class RefinedTreeTextEmitter extends StructuredResultTextEmitter
    {
        private IResultTree tree;
        private ISelectionProvider sel;

        public RefinedTreeTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void initialize(IResult result)
        {
            tree = (IResultTree) result;
            if (result instanceof ISelectionProvider)
                sel  = (ISelectionProvider)result;
            else
                sel = ISelectionProvider.EMPTY;
        }

        @Override
        protected Object[] getItems()
        {
            List<?> elements = tree.getElements();
            return getItems(elements);
        }

        protected Object[] getItems(List<?> elements)
        {
            int rows = context.hasLimit() ? Math.min(elements.size(), context.getLimit()) : elements.size();
            int rows1 = context.isTotalsRowVisible() && tree instanceof RefinedStructuredResult ? rows + 1 : rows;
            int rows0 = 0;
            if (tree instanceof RefinedStructuredResult && ((RefinedStructuredResult) tree).hasActiveFilter())
            {
                ++rows1;
                rows0 = 1;
            }
            Object[] result = new Object[rows1];
            for (int i = 0; i < rows; i++)
            {
                result[rows0 + i] = elements.get(i);
            }
            if (tree instanceof RefinedStructuredResult)
            {
                RefinedStructuredResult rsr = (RefinedStructuredResult)tree;
                if (rows0 == 1)
                    result[0] = rsr.getFilter();
                if (rows1 > rows0 + rows)
                {
                    List<Object> subList = Arrays.asList(result).subList(rows0, rows0 + rows);
                    final TotalsRow totalsRow = rsr.buildTotalsRow(subList);
                    totalsRow.setVisibleItems(rows);
                    rsr.calculateTotals(subList, totalsRow, new VoidProgressListener());
                    result[rows1 - 1] = totalsRow;
                }
            }
            return result;
        }

        @Override
        protected Object[] getColumns()
        {
            return tree.getColumns();
        }

        @Override
        protected String getDisplayableRowValue(Object item)
        {
            // Special work-around case for trees with no columns, e.g. navigation history returned by Copy
            return tree.getColumnValue(item, -1).toString();
        }

        @Override
        protected String getItemValue(Object item, int columnIndex)
        {
            if (item instanceof Filter[])
            {
                Filter f[] = (Filter[])item;
                String fv = f[columnIndex].getCriteria();
                if (fv == null)
                    fv = ""; //$NON-NLS-1$
                return fv;
            }
            if (item instanceof TotalsRow)
            {
                TotalsRow tr = (TotalsRow)item;
                return tr.getLabel(columnIndex);
            }
            if (tree instanceof RefinedStructuredResult)
                return ((RefinedStructuredResult)tree).getFormattedColumnValue(item, columnIndex);
            return getStringValue(tree.getColumnValue(item, columnIndex), filter[columnIndex]);
        }

        @Override
        protected String getDisplayableColumnValue(Object item, int index)
        {
            if (item instanceof Filter[])
            {
                Filter f[] = (Filter[])item;
                String fv = f[index].getCriteria();
                if (fv == null)
                    fv = ""; //$NON-NLS-1$
                return fv;
            }
            if (item instanceof TotalsRow)
            {
                TotalsRow tr = (TotalsRow)item;
                return tr.getLabel(index);
            }
            if (tree instanceof RefinedStructuredResult)
            {
                String v = ((RefinedStructuredResult)tree).getFormattedColumnValue(item, index);
                IDecorator dec = tree.getColumns()[index].getDecorator();
                if (dec != null)
                {
                    String prefix = dec.prefix(item);
                    String suffix = dec.suffix(item);
                    if (prefix != null)
                        if (suffix != null)
                            v = prefix + " " + v + " " + suffix; //$NON-NLS-1$ //$NON-NLS-2$
                        else
                            v = prefix + " " + v; //$NON-NLS-1$
                    else
                        if (suffix != null)
                            v = v + " " + suffix; //$NON-NLS-1$
                }
                return v;
            }
            return getStringValue(tree.getColumnValue(item, index), filter[index]);
        }

        @Override
        protected boolean isExpanded(Object item)
        {
            if (item instanceof TotalsRow)
                return false;
            return sel.isExpanded(item);
        }

        @Override
        protected boolean shouldAddNextLine(Object item)
        {
            return isExpanded(item) && toPrint(item);
        }

        private boolean toPrint(Object item)
        {
            return true;
        }

        @Override
        protected boolean shouldProcessChild(Object child)
        {
            return true;
        }

        @Override
        protected Object[] getChildren(Object item)
        {
            if (item instanceof TotalsRow)
                return null;
            if (tree.hasChildren(item))
            {
                List<?> children = tree.getChildren(item);
                if (children != null)
                    return getItems(children);
            }
            return null;
        }
        
        protected int getColumnLength(Object[] items, Object[] objColumns, int columnNumber)
        {
            int lengthToCompare = 0;
            String header = getColumnName(objColumns[columnNumber]);
            int length = header != null ? header.length() : 0;

            for (int i = 0; i < items.length; i++)
            {
                lengthToCompare = getDisplayableColumnValue(items[i], columnNumber).length();

                if (lengthToCompare > length)
                    length = lengthToCompare;

                if (isExpanded(items[i]))
                {
                    if (columnNumber == order[0])
                        lengthToCompare = compare(items[i], length, columnNumber, new StringBuilder());
                    else
                        lengthToCompare = getOtherColumnLength(items[i], length, columnNumber);

                }
                if (lengthToCompare > length)
                    length = lengthToCompare;
            }

            return length;
        }

        private int getOtherColumnLength(Object item, int length, int columnNumber)
        {
            int lengthToCompare = 0;
            Object[] children = getChildren(item);
            if (children != null)
            {
                for (int i = 0; i < children.length; i++)
                {
                    //if (selection != null && skip(children[i]))
                    //    continue;
                    String columnText = getDisplayableColumnValue(children[i], columnNumber);
                    if (columnText != null)
                        lengthToCompare = columnText.length();
                    if (lengthToCompare > length)
                        length = lengthToCompare;

                    if (isExpanded(children[i]))
                    {
                        lengthToCompare = getOtherColumnLength(children[i], length, columnNumber);
                        if (lengthToCompare > length)
                            length = lengthToCompare;
                    }

                }
            }
            return length;
        }

        private int compare(Object item, int length, int columnNumber, StringBuilder level)
        {
            int lengthToCompare = 0;
            Object[] children = getChildren(item);
            if (children != null)
            {
                for (int i = 0; i < children.length; i++)
                {
                    //if (selection != null && skip(children[i]))
                    //    continue;
                    level = getLevel(level, children.length, i);
                    lengthToCompare = getDisplayableColumnValue(children[i], columnNumber).length() + level.length();
                    if (lengthToCompare > length)
                        length = lengthToCompare;

                    if (isExpanded(children[i]))
                    {
                        lengthToCompare = compare(children[i], length, columnNumber, level);
                        if (lengthToCompare > length)
                            length = lengthToCompare;
                    }
                    if (level.length() >= 3)
                        level.delete(level.length() - 3, level.length());

                }
            }
            return length;
        }
    
    }
}
