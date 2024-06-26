/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - Bytes values
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.report.Renderer;

import com.ibm.icu.text.DecimalFormatSymbols;

@Renderer(target = "csv", result = { IResultTree.class, IResultTable.class })
public class CSVOutputter extends OutputterBase
{
    private static final char SEPARATOR = new DecimalFormatSymbols().getDecimalSeparator() == ',' ? ';' : ',';

    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        embedd(context, result, writer);
    }

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        // add column names to first row
        Column[] columns = (result instanceof IResultTable) ? ((IResultTable) result).getColumns()
                        : ((IResultTree) result).getColumns();
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
        if (result instanceof IResultTable)
        {
            IResultTable table = ((IResultTable) result);
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
        else if (result instanceof IResultTree)
        {
            // export only first level of the RefinedTree
            IResultTree tree = (IResultTree) result;
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
     * https://en.wikipedia.org/wiki/Comma-separated_values
     */
    private static void escape(Writer writer, String data) throws IOException
    {
        if (data == null)
            return;

        boolean hasSeparator = data.indexOf(SEPARATOR) >= 0;
        boolean hasQuote = data.indexOf('"') >= 0;
        boolean hasNewLine = data.indexOf('\n') >= 0 || data.indexOf('\r') >= 0 && data.indexOf('\f') >= 0;

        if (hasSeparator || hasQuote || hasNewLine)
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
}
