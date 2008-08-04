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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.Renderer;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.VoidProgressListener;

@Renderer(target = "html")
public class HtmlOutputter implements IOutputter
{

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        if (result instanceof RefinedTable)
        {
            renderTable(context, (RefinedTable) result, writer);
        }
        else if (result instanceof RefinedTree)
        {
            renderTree(context, (RefinedTree) result, writer);
        }
        else if (result instanceof TextResult)
        {
            renderText(context, (TextResult) result, writer);
        }
        else if (result instanceof DisplayFileResult)
        {       
            File target = new File( context.getOutputDirectory(), ((DisplayFileResult) result).getFile().getName());
            //TODO(en) check whether successful:
            ((DisplayFileResult) result).getFile().renameTo(target);           
        }
        else if (result == null)
        {
            writer.append("n/a");
        }            
        else
        {
            writer.append(result.toString());
        }
    }

    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    // //////////////////////////////////////////////////////////////
    // 
    // //////////////////////////////////////////////////////////////

    private void renderTable(Context context, RefinedTable table, Writer artefact) throws IOException
    {
        Column[] columns = table.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(context, artefact, columns);

        artefact.append("<tbody>");

        // render filter row
        renderFilterRow(context, artefact, table);

        int numberOfRowsToDisplay = (context.hasLimit() && context.getLimit() < table.getRowCount()) ? context
                        .getLimit() : table.getRowCount();
        for (int rowIndex = 0; rowIndex < numberOfRowsToDisplay; rowIndex++)
        {
            Object row = table.getRow(rowIndex);

            artefact.append("<tr");

            if (table.isSelected(row))
                artefact.append(" class=\"selected\"");

            artefact.append(">");

            if (context.isVisible(0))
            {
                artefact.append("<td>");

                String iconUrl = context.getRelativeIconLink(table.getIcon(row));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\">");
                renderColumnValue(context, artefact, table, columns, row, 0);

                artefact.append("</td>");
            }
            renderDataColumns(context, artefact, table, columns, row);
            artefact.append("</tr>");
        }
        // append totals row
        renderTotalsRow(context, artefact, (RefinedStructuredResult) table, ((RefinedTable) table).getRows(),
                        numberOfRowsToDisplay, columns, 0);

        artefact.append("</tbody></table>");
    }

    private void renderFilterRow(Context context, Writer artefact, RefinedStructuredResult result) throws IOException
    {
        if (!result.hasActiveFilter())
            return;

        artefact.append("<tr class=\"filter\">");
        Filter[] filter = result.getFilter();
        for (int i = 0; i < filter.length; i++)
        {
            if (context.isVisible(i))
            {
                if (filter[i].isActive())
                    artefact.append("<td>").append(HTMLUtils.escapeText(filter[i].getCriteria())).append("</td>");
                else
                    artefact.append("<td></td>");
            }
        }
        artefact.append("</tr>");
    }

    private void renderTableHeader(Context context, Writer artefact, Column[] columns) throws IOException
    {
        boolean showTableHeader = "true".equals(context.param(Params.Html.SHOW_TABLE_HEADER, "true"));
        if (showTableHeader)
        {
            artefact.append("<thead><tr>");
            for (int ii = 0; ii < columns.length; ii++)
            {
                if (context.isVisible(ii))
                    artefact.append("<th>").append(columns[ii].getLabel()).append("</th>");
            }
            artefact.append("</tr></thead>");
        }
    }

    private void renderTotalsRow(Context context, Writer artefact, RefinedStructuredResult result, List<?> elements,
                    int numberOfRowsToDisplay, Column[] columns, int level) throws IOException
    {
        if (context.showTotals())
        {
            final TotalsRow totalsRow = result.buildTotalsRow(elements);
            totalsRow.setVisibleItems(numberOfRowsToDisplay);
            if (!totalsRow.isVisible())
                return;
            result.calculateTotals(elements, totalsRow, new VoidProgressListener());
            String iconUrl = context.getRelativeIconLink(totalsRow.getIcon());
            artefact.append("<tr class=\"totals\">");
            for (int i = 0; i < columns.length; i++)
            {
                if (context.isVisible(i))
                {
                    if (i == 0)// append icon
                        artefact.append("<td style=\"padding-left:").append(String.valueOf(level * 10)).append("px\">")
                                        .append("<img src=\"").append(iconUrl).append("\">").append(
                                                        totalsRow.getLabel(i)).append("</td>");
                    else
                    {
                        if (columns[i].isNumeric())
                            artefact.append("<td align=\"right\">");
                        else
                            artefact.append("<td>");
                        artefact.append(totalsRow.getLabel(i)).append("</td>");
                    }
                }
            }
            artefact.append("</tr>");
        }
    }

    private void renderTree(Context context, RefinedTree tree, Writer artefact) throws IOException
    {
        Column[] columns = tree.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(context, artefact, columns);

        artefact.append("<tbody>");

        renderFilterRow(context, artefact, tree);

        renderChildren(context, artefact, tree, columns, tree.getElements(), 0);

        artefact.append("</tbody></table>");
    }

    private void renderChildren(Context context, Writer artefact, RefinedTree tree, Column[] columns, List<?> elements,
                    int level) throws IOException
    {
        int numberOfRowsToDisplay = (context.hasLimit() && context.getLimit() < elements.size()) ? context.getLimit()
                        : elements.size();
        for (int i = 0; i < numberOfRowsToDisplay; i++)
        {
            Object element = elements.get(i);

            boolean isExpanded = tree.isExpanded(element);

            List<?> children = tree.hasChildren(element) ? tree.getChildren(element) : null;
            boolean hasChildren = children != null && !children.isEmpty();

            artefact.append("<tr");

            if (tree.isSelected(element))
                artefact.append(" class=\"selected\"");

            artefact.append(">");

            if (context.isVisible(0))
            {

                artefact.append("<td style=\"padding-left:").append(String.valueOf(level * 10)).append("px\">");

                String iconUrl = context.getRelativeIconLink(tree.getIcon(element));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\">");

                renderColumnValue(context, artefact, tree, columns, element, 0);

                if (!isExpanded && hasChildren)
                    artefact.append(" &raquo;");

                artefact.append("</td>");
            }
            renderDataColumns(context, artefact, tree, columns, element);
            artefact.append("</tr>");

            if (isExpanded && hasChildren && level < 100)
            {
                renderChildren(context, artefact, tree, columns, children, level + 1);
            }
            else if (level == 100)
            {
                renderDepthRow(artefact);
            }

        }
        renderTotalsRow(context, artefact, (RefinedStructuredResult) tree, elements, numberOfRowsToDisplay, columns,
                        level);
    }

    private void renderDepthRow(Writer artefact) throws IOException
    {
        artefact.append("<tr class=\"totals\">");
        artefact.append("<td style=\"padding-left:1000px\">").append("&raquo; Depth of the tree is limited to 100")
                        .append("</td>");
        artefact.append("</tr>");
    }

    private void renderDataColumns(Context context, Writer artefact, RefinedStructuredResult structured,
                    Column[] columns, Object row) throws IOException
    {
        for (int columnIndex = 1; columnIndex < columns.length; columnIndex++)
        {
            if (context.isVisible(columnIndex))
            {
                if (columns[columnIndex].isNumeric())
                    artefact.append("<td align=\"right\">");
                else
                    artefact.append("<td>");

                renderColumnValue(context, artefact, structured, columns, row, columnIndex);
                artefact.append("</td>");
            }
        }
    }

    private void renderColumnValue(Context context, Writer artefact, RefinedStructuredResult structured,
                    Column[] columns, Object row, int columnIndex) throws IOException
    {
        IDecorator decorator = columns[columnIndex].getDecorator();

        if (decorator != null)
        {
            String prefix = decorator.prefix(row);
            if (prefix != null)
                artefact.append("<strong>").append(HTMLUtils.escapeText(prefix)).append("</strong> ");
        }

        String label = structured.getFormattedColumnValue(row, columnIndex);
        if (label.length() > 0)
        {
            label = HTMLUtils.escapeText(label);

            if (columnIndex == 0)
                renderLink(context, artefact, structured, row, label);
            else
                artefact.append(label);
        }

        if (decorator != null)
        {
            String suffix = decorator.suffix(row);
            if (suffix != null)
                artefact.append(" <strong>").append(HTMLUtils.escapeText(suffix)).append("</strong>");
        }
    }

    private void renderLink(Context context, Writer artefact, IStructuredResult thing, Object row, String label)
                    throws IOException
    {
        int objectId = -1;

        IContextObject ctx = thing.getContext(row);
        if (ctx instanceof IContextObject)
            objectId = ctx.getObjectId();

        if (objectId >= 0)
        {
            try
            {
                String externalIdentifier = context.getQueryContext().mapToExternalIdentifier(objectId);
                artefact.append("<a href=\"").append(QueryObjectLink.forObject(externalIdentifier)).append("\">")
                                .append(label).append("</a>");
            }
            catch (SnapshotException exception)
            {
                artefact.append(label);
            }
        }
        else
        {
            artefact.append(label);
        }
    }

    private void renderText(Context context, TextResult textResult, Writer writer) throws IOException
    {
        writer.append("<p>");//$NON-NLS-1$

        if (textResult.isHtml())
        {
            writer.append(textResult.getText());
        }
        else
        {
            writer.append("<pre>"); //$NON-NLS-1$
            writer.append(HTMLUtils.escapeText(textResult.getText()));
            writer.append("</pre>"); //$NON-NLS-1$
        }

        writer.append("</p>");//$NON-NLS-1$
    }
}
