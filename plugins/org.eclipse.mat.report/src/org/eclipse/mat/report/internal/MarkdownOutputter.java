/*******************************************************************************
 * Copyright (c) 2026 SAP AG and IBM Corporation
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

import org.eclipse.mat.query.AIDetailsProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Renderer;

@Renderer(target = "md", result = { IResultTree.class, IResultTable.class, TextResult.class, IResultPie.class })
public class MarkdownOutputter extends TextOutputter
{
    @Override
    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        ResultMetaData rmd = result.getResultMetaData();
        if (rmd != null)
        {
            AIDetailsProvider aiDetailsProvider = rmd.getAIDetailsProvider();
            if (aiDetailsProvider != null)
            {
                String prefix = aiDetailsProvider.getOutputPrefix();
                if (prefix != null)
                {
                    writer.append(prefix);
                    writer.append(LINE_SEPARATOR);
                    writer.append(LINE_SEPARATOR);
                }
            }
        }

        if (result instanceof IResultTree)
        {
            writer.append(Messages.MarkdownOutputter_PrefixTree);
        }
        else if (result instanceof IResultTable)
        {
            writer.append(Messages.MarkdownOutputter_PrefixTable);
        }
        else if (result instanceof TextResult)
        {
            writer.append(Messages.MarkdownOutputter_PrefixText);
        }
        else if (result instanceof TextResult)
        {
            writer.append(Messages.MarkdownOutputter_PieChart);
        }

        writer.append(LINE_SEPARATOR);
        writer.append(LINE_SEPARATOR);

        super.embedd(context, result, writer);
    }

    @Override
    protected void embeddTree(Context context, IResult result, Writer writer)
    {
        new TreeTextEmitter(context, result, writer).doCopy();
    }

    @Override
    protected void embeddTable(Context context, IResult result, Writer writer)
    {
        new TableTextEmitter(context, result, writer).doCopy();
    }

    private static void appendItem(TextEmitter emitter, Object[] columns, Object item, int depth)
    {
        boolean firstOutput = true;
        String prefix = depth > 0 ? new String(" ").repeat(4 * depth) : ""; //$NON-NLS-1$ //$NON-NLS-2$
        for (int i = 0; i < emitter.order.length; i++)
        {
            int columnIndex = emitter.order[i];
            String columnName = emitter.getColumnName(columns[emitter.order[i]]);
            String value = emitter.getItemValue(item, columnIndex);
            for (String filterName : Filter.FILTER_TYPES)
            {
                if (value.equals(filterName))
                    value = ""; //$NON-NLS-1$
            }

            if (value.length() > 0)
            {
                if (firstOutput)
                {
                    firstOutput = false;
                    emitter.append(prefix);
                    emitter.append("* "); //$NON-NLS-1$
                }
                else
                {
                    emitter.append(", "); //$NON-NLS-1$
                }
                emitter.append(columnName);
                emitter.append(": "); //$NON-NLS-1$
                emitter.append(value);
            }
        }
        if (!firstOutput)
        {
            emitter.append(LINE_SEPARATOR);
        }
    }

    private static class TreeTextEmitter extends RefinedTreeTextEmitter
    {
        public TreeTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void doCopyTable(Object[] items, Object[] columns, int numberOfColumns)
        {
            for (Object item : items)
            {
                appendItem(this, columns, item, 0);
                if (shouldAddNextLine(item))
                {
                    addNextLine(item, columns, 1);
                }
            }
        }

        private void addNextLine(Object item, Object[] columns, int depth)
        {
            Object[] children = getChildren(item);
            if (children != null)
            {
                for (int j = 0; j < children.length; j++)
                {
                    if (shouldProcessChild(children[j]))
                    {
                        appendItem(this, columns, children[j], depth);

                        if (isExpanded(children[j]))
                        {
                            addNextLine(children[j], columns, depth + 1);
                        }
                    }
                }
            }
        }
    }

    private static class TableTextEmitter extends RefinedTableTextEmitter
    {
        public TableTextEmitter(Context context, IResult result, Writer writer)
        {
            super(context, result, writer);
        }

        @Override
        protected void doCopyTable(Object[] items, Object[] columns, int numberOfColumns)
        {
            for (Object item : items)
            {
                appendItem(this, columns, item, 0);
                if (shouldAddNextLine(item))
                {
                    addNextLine(item, columns, 1);
                }
            }
        }

        private void addNextLine(Object item, Object[] columns, int depth)
        {
            Object[] children = getChildren(item);
            if (children != null)
            {
                for (int j = 0; j < children.length; j++)
                {
                    if (shouldProcessChild(children[j]))
                    {
                        appendItem(this, columns, children[j], depth);

                        if (isExpanded(children[j]))
                        {
                            addNextLine(children[j], columns, depth + 1);
                        }
                    }
                }
            }
        }
    }
}
