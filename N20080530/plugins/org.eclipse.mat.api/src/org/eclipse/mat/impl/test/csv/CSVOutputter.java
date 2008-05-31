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
package org.eclipse.mat.impl.test.csv;

import java.io.IOException;
import java.io.Writer;
import java.text.Format;
import java.util.List;

import org.eclipse.mat.impl.result.RefinedTable;
import org.eclipse.mat.impl.result.RefinedTree;
import org.eclipse.mat.impl.result.RefinedStructuredResult.RetainedSizeFormat;
import org.eclipse.mat.impl.test.IOutputter;
import org.eclipse.mat.impl.test.QueryPart;
import org.eclipse.mat.impl.test.ResultRenderer.RenderingInfo;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.snapshot.extension.Subject;


@Subject("csv")
public class CSVOutputter implements IOutputter
{
    private static final String SEPARATOR = ";";

    public String getExtension()
    {
        return "csv";
    }

    public void process(Context context, QueryPart part, IResult result, RenderingInfo renderingInfo, Writer writer)
                    throws IOException
    {
        embedd(context, part, result, renderingInfo, writer);
    }

    public void embedd(Context context, QueryPart part, IResult result, RenderingInfo renderingInfo, Writer writer)
                    throws IOException
    {
        // add Column titles
        Column[] columns;
        if (result instanceof RefinedTable)
        {

            columns = ((RefinedTable) result).getColumns();
        }
        else
        {
            columns = ((RefinedTree) result).getColumns();
        }
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
        {
            if (renderingInfo.isVisible(columnIndex))
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
            for (int i = 0; i < ((RefinedTable) result).getRowCount(); i++)
            {
                for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
                {
                    if (renderingInfo.isVisible(columnIndex))
                    {
                        Object columnValue = ((RefinedTable) result).getColumnValue(((RefinedTable) result).getRow(i),
                                        columnIndex);
                        if (columnValue != null)
                        {
                            writer.append(getStringValue(columnValue, columns[columnIndex].getFormatter()));
                        }
                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n");
            }
        }
        else if (result instanceof RefinedTree)
        {
            // export only first level of the RafinedTree
            List<?> elements = ((RefinedTree) result).getElements();
            for (int i = 0; i < elements.size(); i++)
            {
                for (int columnIndex = 0; columnIndex < columns.length; columnIndex++)
                {
                    if (renderingInfo.isVisible(columnIndex))
                    {
                        Object columnValue = ((RefinedTree) result).getColumnValue(elements.get(i), columnIndex);
                        if (columnValue != null)
                        {
                            writer.append(getStringValue(columnValue, columns[columnIndex].getFormatter()));
                        }
                        writer.append(SEPARATOR);
                    }
                }
                writer.append("\n");
            }
        }
    }

    private String getStringValue(Object columnValue, Format format)
    {// convert minus value(in case of min retained size) to plus value
        String value = columnValue.toString();

        if (value.startsWith("-") && format instanceof RetainedSizeFormat)
            value = value.substring(1);
        return value;
    }
}
