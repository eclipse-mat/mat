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
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
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

@Renderer(target = "csv", result = { IResultTree.class, IResultTable.class })
public class CSVOutputter implements IOutputter
{
    private static final String SEPARATOR = ";";

    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        embedd(context, result, writer);
    }

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        // add Column titles
        Column[] columns = (result instanceof RefinedTable) ? ((RefinedTable) result).getColumns()
                        : ((RefinedTree) result).getColumns();
        Filter.ValueConverter[] filter = new Filter.ValueConverter[columns.length];

        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
        {
            filter[columnIndex] = (Filter.ValueConverter) columns[columnIndex].getData(Filter.ValueConverter.class);

            if (context.isColumnVisible(columnIndex))
            {
                if (columns[columnIndex].getLabel() != null)
                    writer.append(columns[columnIndex].getLabel());
                writer.append(SEPARATOR);
            }

        }
        writer.append("\n");
        // add data rows
        if (result instanceof RefinedTable)
        {
            RefinedTable table = ((RefinedTable) result);
            int limit = context.hasLimit() ? Math.min(table.getRowCount(), context.getLimit()) : table.getRowCount();

            for (int i = 0; i < limit; i++)
            {
                for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
                {
                    if (context.isColumnVisible(columnIndex))
                    {
                        Object columnValue = table.getColumnValue(table.getRow(i), columnIndex);
                        if (columnValue != null)
                        {
                            writer.append(getStringValue(columnValue, filter[columnIndex]));
                        }
                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n");
            }
        }
        else if (result instanceof RefinedTree)
        {
            RefinedTree tree = (RefinedTree) result;
            // export only first level of the RefinedTree
            List<?> elements = tree.getElements();
            int limit = context.hasLimit() ? Math.min(elements.size(), context.getLimit()) : elements.size();

            for (int i = 0; i < limit; i++)
            {
                for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
                {
                    if (context.isColumnVisible(columnIndex))
                    {
                        Object columnValue = tree.getColumnValue(elements.get(i), columnIndex);
                        if (columnValue != null)
                        {
                            writer.append(getStringValue(columnValue, filter[columnIndex]));
                        }
                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n");
            }
        }
    }

    private String getStringValue(Object columnValue, Filter.ValueConverter format)
    {
        if (format != null)
        {
            Format fmt = null;
            if (columnValue instanceof Long || columnValue instanceof Integer)
                fmt = new DecimalFormat("0");
            else
                fmt = new DecimalFormat("0.#");

            return fmt.format(format.convert(((Number) columnValue).doubleValue()));
        }
        else
        {
            return columnValue.toString();
        }
    }
}
