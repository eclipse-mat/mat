/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.io.Writer;
import java.text.Format;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DecimalFormatSymbols;

@Renderer(target = "csv", result = { IResultTree.class, IResultTable.class })
public class CSVOutputter implements IOutputter
{
    private static final char SEPARATOR = new DecimalFormatSymbols().getDecimalSeparator() == ',' ? ';' : ',';

    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        embedd(context, result, writer);
    }

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        // add column names to first row
        Column[] columns = (result instanceof RefinedTable) ? ((RefinedTable) result).getColumns()
                        : ((RefinedTree) result).getColumns();
        Filter.ValueConverter[] filter = new Filter.ValueConverter[columns.length];

        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
        {
            filter[columnIndex] = (Filter.ValueConverter) columns[columnIndex].getData(Filter.ValueConverter.class);

            if (context.isColumnVisible(columnIndex))
            {
                if (columns[columnIndex].getLabel() != null)
                    escape(writer, columns[columnIndex].getLabel());
                writer.append(SEPARATOR);
            }

        }
        writer.append("\n"); //$NON-NLS-1$

        // add data records
        if (result instanceof RefinedTable)
        {
            RefinedTable table = ((RefinedTable) result);
            int limit = context.hasLimit() ? Math.min(table.getRowCount(), context.getLimit()) : table.getRowCount();

            for (int row = 0; row < limit; row++)
            {
                for (int column = 0; column < columns.length; column++)
                {
                    if (context.isColumnVisible(column))
                    {
                        Object columnValue = table.getColumnValue(table.getRow(row), column);
                        if (columnValue != null)
                            escape(writer, getStringValue(columnValue, filter[column]));

                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n"); //$NON-NLS-1$
            }
        }
        else if (result instanceof RefinedTree)
        {
            // export only first level of the RefinedTree
            RefinedTree tree = (RefinedTree) result;
            List<?> elements = tree.getElements();
            int limit = context.hasLimit() ? Math.min(elements.size(), context.getLimit()) : elements.size();

            for (int row = 0; row < limit; row++)
            {
                for (int column = 0; column < columns.length; column++)
                {
                    if (context.isColumnVisible(column))
                    {
                        Object columnValue = tree.getColumnValue(elements.get(row), column);
                        if (columnValue != null)
                            escape(writer, getStringValue(columnValue, filter[column]));

                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n"); //$NON-NLS-1$
            }
        }
    }

    /**
     * Escape data for use in csv files.
     * http://en.wikipedia.org/wiki/Comma-separated_values
     */
    private static void escape(Writer writer, String data) throws IOException
    {
        if (data == null)
            return;

        boolean hasSeparator = data.indexOf(SEPARATOR) >= 0;
        boolean hasQuote = data.indexOf('"') >= 0;

        if (hasSeparator || hasQuote)
        {
            writer.append('"');

            if (hasQuote)
            {
                int len = data.length();
                for (int ii = 0; ii < len; ii++)
                {
                    char c = data.charAt(ii);
                    if (c == '"')
                        writer.append('"');
                    writer.append(c);
                }
            }
            else
            {
                writer.append(data);
            }

            writer.append('"');
        }
        else
        {
            writer.append(data);
        }
    }

    private String getStringValue(Object columnValue, Filter.ValueConverter converter)
    {
        if (columnValue == null)
            return ""; //$NON-NLS-1$

        // check first the format: the converter can change the type to double!
        Format fmt = null;
        if (columnValue instanceof Long || columnValue instanceof Integer)
            fmt = new DecimalFormat("0"); //$NON-NLS-1$
        else if (columnValue instanceof Double || columnValue instanceof Float)
            fmt = new DecimalFormat("0.#####"); //$NON-NLS-1$

        if (converter != null)
            columnValue = converter.convert(((Number) columnValue).doubleValue());

        if (fmt != null)
            return fmt.format(columnValue);
        else
            return columnValue.toString();
    }
}
