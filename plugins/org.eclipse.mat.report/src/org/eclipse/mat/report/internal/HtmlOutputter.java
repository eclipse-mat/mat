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
import java.io.FileFilter;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.DetailResultProvider;
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
        boolean hasDetailsLink = "true".equals(context.param(Params.Html.RENDER_DETAILS, "true")) // 
                        && result != null //
                        && result.getResultMetaData() != null //
                        && !result.getResultMetaData().getDetailResultProviders().isEmpty();

        if (result instanceof RefinedTable)
        {
            renderTable(context, (RefinedTable) result, writer, hasDetailsLink);
        }
        else if (result instanceof RefinedTree)
        {
            renderTree(context, (RefinedTree) result, writer, hasDetailsLink);
        }
        else if (result instanceof TextResult)
        {
            renderText(context, (TextResult) result, writer, hasDetailsLink);
        }
        else if (result instanceof DisplayFileResult)
        {
            File src = ((DisplayFileResult) result).getFile();
            File dest = new File(context.getOutputDirectory(), src.getName());

            if (!src.renameTo(dest))
                throw new IOException(MessageFormat.format("Error moving file {0} to {1}", //
                                src.getAbsolutePath(), dest.getAbsolutePath()));
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

    private void renderTable(Context context, RefinedTable table, Writer artefact, boolean hasDetailsLink)
                    throws IOException
    {
        Column[] columns = table.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(context, artefact, columns, hasDetailsLink);

        artefact.append("<tbody>");

        // render filter row
        renderFilterRow(context, artefact, table, hasDetailsLink);

        int numberOfRowsToDisplay = (context.hasLimit() && context.getLimit() < table.getRowCount()) ? context
                        .getLimit() : table.getRowCount();
        for (int rowIndex = 0; rowIndex < numberOfRowsToDisplay; rowIndex++)
        {
            Object row = table.getRow(rowIndex);

            artefact.append("<tr");

            if (table.isSelected(row))
                artefact.append(" class=\"selected\"");

            artefact.append(">");

            if (context.isColumnVisible(0))
            {
                artefact.append("<td>");

                String iconUrl = context.addIcon(table.getIcon(row));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\">");
                renderColumnValue(context, artefact, table, columns, row, 0);

                artefact.append("</td>");
            }
            renderDataColumns(context, artefact, table, columns, row, hasDetailsLink);
            artefact.append("</tr>");
        }
        // append totals row
        final TotalsRow totalsRow = table.buildTotalsRow(table.getRows());
        totalsRow.setVisibleItems(+numberOfRowsToDisplay);
        if (totalsRow.isVisible())
            renderTotalsRow(context, artefact, table, table.getRows(), totalsRow, columns, new int[0], hasDetailsLink);
        artefact.append("</tbody></table>");
    }

    private void renderFilterRow(Context context, Writer artefact, RefinedStructuredResult result,
                    boolean hasDetailsLink) throws IOException
    {
        if (!result.hasActiveFilter())
            return;

        artefact.append("<tr class=\"filter\">");
        Filter[] filter = result.getFilter();
        for (int i = 0; i < filter.length; i++)
        {
            if (context.isColumnVisible(i))
            {
                if (filter[i].isActive())
                    artefact.append("<td>").append(HTMLUtils.escapeText(filter[i].getCriteria())).append("</td>");
                else
                    artefact.append("<td/>");
            }
        }

        if (hasDetailsLink)
            artefact.append("<td/>");

        artefact.append("</tr>");
    }

    private void renderTableHeader(Context context, Writer artefact, Column[] columns, boolean hasDetailsLink)
                    throws IOException
    {
        boolean showTableHeader = "true".equals(context.param(Params.Html.SHOW_TABLE_HEADER, "true"));
        if (showTableHeader)
        {
            artefact.append("<thead><tr>");
            for (int ii = 0; ii < columns.length; ii++)
            {
                if (context.isColumnVisible(ii))
                    artefact.append("<th>").append(columns[ii].getLabel()).append("</th>");
            }

            if (hasDetailsLink)
                artefact.append("<th>Details</th>");

            artefact.append("</tr></thead>");
        }
    }

    private void renderTotalsRow(Context context, Writer artefact, RefinedStructuredResult result, List<?> elements,
                    TotalsRow totalsRow, Column[] columns, int[] branches, boolean hasDetailsLink) throws IOException
    {
        if (context.isTotalsRowVisible())
        {
            result.calculateTotals(elements, totalsRow, new VoidProgressListener());
            String iconUrl = context.addIcon(totalsRow.getIcon());
            artefact.append("<tr class=\"totals\">");
            for (int i = 0; i < columns.length; i++)
            {
                if (context.isColumnVisible(i))
                {
                    if (i == 0)// append icon
                    {
                        artefact.append("<td>");

                        if (branches.length > 0)
                        {
                            branches[branches.length - 1] = 3;
                        }
                        renderTreeIndentation(context, artefact, branches);

                        artefact.append("<img src=\"").append(iconUrl).append("\"/>");
                        artefact.append("<ul><li>").append(totalsRow.getLabel(i)).append("</li></ul>").append("</td>");
                    }
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

            if (hasDetailsLink)
                artefact.append("<td/>");

            artefact.append("</tr>");
        }
    }

    private void renderTree(Context context, RefinedTree tree, Writer artefact, boolean hasDetailsLink)
                    throws IOException
    {
        Column[] columns = tree.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(context, artefact, columns, hasDetailsLink);

        artefact.append("<tbody>");

        renderFilterRow(context, artefact, tree, hasDetailsLink);

        renderChildren(context, artefact, tree, columns, tree.getElements(), 0, new int[0], hasDetailsLink);

        artefact.append("</tbody></table>");
    }

    private void renderChildren(Context context, Writer artefact, RefinedTree tree, Column[] columns, List<?> elements,
                    int level, int[] branches, boolean hasDetailsLink) throws IOException
    {
        int numberOfRowsToDisplay = (context.hasLimit() && context.getLimit() < elements.size()) ? context.getLimit()
                        : elements.size();

        TotalsRow totalsRow = tree.buildTotalsRow(elements);
        totalsRow.setVisibleItems(numberOfRowsToDisplay);

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

            if (context.isColumnVisible(0))
            {
                artefact.append("<td>");
                if (i == numberOfRowsToDisplay - 1 && (!totalsRow.isVisible() || !context.isTotalsRowVisible()))
                {
                    if (branches.length > 0)
                    {
                        branches[branches.length - 1] = 3;
                    }
                }
                renderTreeIndentation(context, artefact, branches);

                String iconUrl = context.addIcon(tree.getIcon(element));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\"/>");

                artefact.append("<ul><li>");
                renderColumnValue(context, artefact, tree, columns, element, 0);

                if (!isExpanded && hasChildren)
                    artefact.append(" &raquo;");

                artefact.append("</li></ul>");
                artefact.append("</td>");
            }
            renderDataColumns(context, artefact, tree, columns, element, hasDetailsLink);
            artefact.append("</tr>");

            if (isExpanded && hasChildren && level < 100)
            {
                int[] newBranches = new int[branches.length + 1];
                System.arraycopy(branches, 0, newBranches, 0, branches.length);
                newBranches[newBranches.length - 1] = 2;
                if (newBranches.length > 1)
                {
                    int last = newBranches[newBranches.length - 2];
                    newBranches[newBranches.length - 2] = (last == 2) ? 1 : 0;
                }
                renderChildren(context, artefact, tree, columns, children, level + 1, newBranches, hasDetailsLink);
            }
            else if (level == 100)
            {
                renderDepthRow(artefact);
            }

        }
        if (totalsRow.isVisible())
            renderTotalsRow(context, artefact, tree, elements, totalsRow, columns, branches, hasDetailsLink);
    }

    private void renderTreeIndentation(Context context, Writer artefact, int[] branches) throws IOException
    {
        File[] files = context.getOutputDirectory().listFiles(new FileFilter()
        {

            public boolean accept(File file)
            {
                if (file.isDirectory() && file.getName().equals("img"))
                    return true;
                return false;
            }
        });
        String prefix = (files.length > 0) ? "" : "../";
        for (int bc : branches)
        {
            switch (bc)
            {
                case 0:
                    artefact.append("<img src=\"").append(prefix).append("img/empty.gif\"/>");
                    break;
                case 1:
                    artefact.append("<img src=\"").append(prefix).append("img/line.gif\"/>");
                    break;
                case 2:
                    artefact.append("<img src=\"").append(prefix).append("img/fork.gif\"/>");
                    break;
                case 3:
                    artefact.append("<img src=\"").append(prefix).append("img/corner.gif\"/>");
                    break;

                default:
                    break;
            }
        }
    }

    private void renderDepthRow(Writer artefact) throws IOException
    {
        artefact.append("<tr class=\"totals\">");
        artefact.append("<td style=\"padding-left:1000px\">").append("&raquo; Depth of the tree is limited to 100")
                        .append("</td>");
        artefact.append("</tr>");
    }

    private void renderDataColumns(Context context, Writer artefact, RefinedStructuredResult structured,
                    Column[] columns, Object row, boolean hasDetailsLink) throws IOException
    {
        for (int columnIndex = 1; columnIndex < columns.length; columnIndex++)
        {
            if (context.isColumnVisible(columnIndex))
            {
                if (columns[columnIndex].isNumeric())
                    artefact.append("<td align=\"right\">");
                else
                    artefact.append("<td>");

                renderColumnValue(context, artefact, structured, columns, row, columnIndex);
                artefact.append("</td>");
            }
        }

        if (hasDetailsLink)
        {
            artefact.append("<td>");
            for (DetailResultProvider p : structured.getResultMetaData().getDetailResultProviders())
            {
                if (p.hasResult(row))
                {
                    try
                    {
                        String link = context.addContextResult(p.getLabel(), p.getResult(row,
                                        new VoidProgressListener()));
                        if (link != null)
                        {
                            artefact.append("<a href=\"").append(link).append("\">");
                            artefact.append(p.getLabel());
                            artefact.append("</a>");
                        }
                    }
                    catch (SnapshotException e)
                    {
                        IOException ioe = new IOException(e.getMessage());
                        ioe.initCause(e);
                        throw ioe;
                    }
                }
            }
            artefact.append("</td>");
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
        if (ctx != null)
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

    private void renderText(Context context, TextResult textResult, Writer writer, boolean hasDetailsLink)
                    throws IOException
    {
        writer.append("<p>");//$NON-NLS-1$

        if (textResult.isHtml())
        {
            if (!hasDetailsLink)
                writer.append(textResult.getText());
            else
                resolveDetailLinks(context, textResult, writer);
        }
        else
        {
            writer.append("<pre>"); //$NON-NLS-1$
            writer.append(HTMLUtils.escapeText(textResult.getText()));
            writer.append("</pre>"); //$NON-NLS-1$
        }

        writer.append("</p>");//$NON-NLS-1$
    }

    private void resolveDetailLinks(Context context, TextResult textResult, Writer writer) throws IOException
    {
        List<DetailResultProvider> detailProvider = textResult.getResultMetaData().getDetailResultProviders();

        // very simple parsing, as we are only interested in links we construct
        // ourselves
        String text = textResult.getText();

        int start = 0;
        int length = text.length();

        int protocolIndex = text.indexOf(QueryObjectLink.PROTOCOL);
        while (protocolIndex >= 0)
        {
            int endIndex = text.indexOf('"', protocolIndex);
            if (endIndex < 0)
                break;

            String url = text.substring(protocolIndex, endIndex);
            QueryObjectLink link = QueryObjectLink.parse(url);
            if (link == null || link.getType() != QueryObjectLink.Type.DETAIL_RESULT)
            {
                writer.append(text.substring(start, endIndex));
            }
            else
            {
                int targetIndex = link.getTarget().indexOf('/');
                String name = link.getTarget().substring(0, targetIndex);
                String identifier = link.getTarget().substring(targetIndex + 1);

                writer.append(text.subSequence(start, protocolIndex));

                boolean done = false;
                for (DetailResultProvider provider : detailProvider)
                {
                    if (name.equals(provider.getLabel()))
                    {
                        if (provider.hasResult(identifier))
                        {
                            try
                            {
                                String l = context.addContextResult(provider.getLabel(), provider.getResult(identifier,
                                                new VoidProgressListener()));
                                if (l != null)
                                {
                                    writer.append(l);
                                    done = true;
                                }
                            }
                            catch (SnapshotException e)
                            {
                                IOException ioe = new IOException(e.getMessage());
                                ioe.initCause(e);
                                throw ioe;
                            }
                        }
                        break;
                    }
                }

                if (!done)
                    writer.append(url);
            }

            start = endIndex;
            protocolIndex = text.indexOf(QueryObjectLink.PROTOCOL, start);
        }

        if (start < length)
            writer.append(text.substring(start));

    }
}
