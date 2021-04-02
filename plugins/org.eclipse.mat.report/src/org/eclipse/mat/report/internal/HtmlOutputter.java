/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - add extra links for multiple objects
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.registry.Converters;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.Renderer;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

@Renderer(target = "html")
public class HtmlOutputter implements IOutputter
{
    private int maxLinkObjects = 10;
    private static final boolean useList = true;

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        boolean hasDetailsLink = "true".equals(context.param(Params.Html.RENDER_DETAILS, "true")) //$NON-NLS-1$ //$NON-NLS-2$
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
                throw new IOException(MessageUtil.format(Messages.HtmlOutputter_Error_MovingFile, //
                                src.getAbsolutePath(), dest.getAbsolutePath()));
        }
        else if (result == null)
        {
            writer.append(Messages.HtmlOutputter_Label_NotApplicable);
        }
        else
        {
            writer.append(result.toString());
        }
    }

    public void process(Context context, IResult result, Writer writer) throws IOException
    {
        throw new UnsupportedOperationException(result.toString());
    }

    // //////////////////////////////////////////////////////////////
    // 
    // //////////////////////////////////////////////////////////////
    /**
     * Extract alternate text for image URL.
     * Use result within double quotes for attributes.
     * @param url
     * @return the text or an empty string
     */
    @SuppressWarnings("nls")
    private String altText(URL url)
    {
        String alt = "";
        return HTMLUtils.escapeText(alt).replace("\"","&quot;");
    }

    @SuppressWarnings("nls")
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

                URL url = table.getIcon(row);
                String iconUrl = context.addIcon(url);
                if (iconUrl != null)
                {
                    String alt = altText(url);
                    artefact.append("<img src=\"").append(iconUrl).append("\" alt=\""+alt+"\">");
                }
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

    @SuppressWarnings("nls")
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
                    artefact.append("<td></td>");
            }
        }

        if (hasDetailsLink)
            artefact.append("<td></td>");

        artefact.append("</tr>");
    }

    @SuppressWarnings("nls")
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
                artefact.append("<th>" + Messages.HtmlOutputter_Label_Details + "</th>");

            artefact.append("</tr></thead>");
        }
    }

    @SuppressWarnings("nls")
    private void renderTotalsRow(Context context, Writer artefact, RefinedStructuredResult result, List<?> elements,
                    TotalsRow totalsRow, Column[] columns, int[] branches, boolean hasDetailsLink) throws IOException
    {
        if (context.isTotalsRowVisible())
        {
            result.calculateTotals(elements, totalsRow, new VoidProgressListener());
            URL url = totalsRow.getIcon();
            String iconUrl = context.addIcon(url);
            artefact.append("<tr class=\"totals\">");
            for (int i = 0; i < columns.length; i++)
            {
                if (context.isColumnVisible(i))
                {
                    if (i == 0)// append icon
                    {
                        artefact.append("<td>");

                        if (branches.length > 0)
                            branches[branches.length - 1] = 3;

                        renderTreeIndentation(artefact, branches);

                        String alt = altText(url);
                        artefact.append("<img src=\"").append(iconUrl).append("\" alt=\""+alt+"\">");
                        if (useList) artefact.append("<ul><li>");
                        artefact.append(totalsRow.getLabel(i));
                        if (useList) artefact.append("</li></ul>");
                        artefact.append("</td>");
                    }
                    else
                    {
                        switch (columns[i].getAlign())
                        {
                            case RIGHT:
                                artefact.append("<td align=\"right\">");
                                break;
                            case CENTER:
                                artefact.append("<td align=\"center\">");
                                break;
                            case LEFT:
                            default:
                                artefact.append("<td>");
                                break;
                        }
                        artefact.append(totalsRow.getLabel(i)).append("</td>");
                    }
                }
            }

            if (hasDetailsLink)
                artefact.append("<td></td>");

            artefact.append("</tr>");
        }
    }

    @SuppressWarnings("nls")
    private void renderTree(Context context, RefinedTree tree, Writer artefact, boolean hasDetailsLink)
                    throws IOException
    {
        Column[] columns = tree.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(context, artefact, columns, hasDetailsLink);

        artefact.append("<tbody class=\"tree\">");

        renderFilterRow(context, artefact, tree, hasDetailsLink);

        renderChildren(context, artefact, tree, columns, tree.getElements(), 0, new int[0], hasDetailsLink);

        artefact.append("</tbody></table>");
    }

    @SuppressWarnings("nls")
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
                        branches[branches.length - 1] = 3;
                }
                renderTreeIndentation(artefact, branches);

                URL url = tree.getIcon(element);
                String iconUrl = context.addIcon(url);
                if (iconUrl != null)
                {
                    String alt = altText(url);
                    artefact.append("<img src=\"").append(iconUrl).append("\" alt=\""+alt+"\">");
                }

                if (useList) artefact.append("<ul><li>");
                renderColumnValue(context, artefact, tree, columns, element, 0);

                if (!isExpanded && hasChildren)
                    artefact.append(" &raquo;");

                if (useList) artefact.append("</li></ul>");
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

    @SuppressWarnings("nls")
    private void renderTreeIndentation(Writer artefact, int[] branches) throws IOException
    {
        for (int branch : branches)
        {
            switch (branch)
            {
                case 0:
                    artefact.append(".");
                    break;
                case 1:
                    artefact.append("|");
                    break;
                case 2:
                    artefact.append("+");
                    break;
                case 3:
                    artefact.append("\\");
                    break;
            }
        }
    }

    @SuppressWarnings("nls")
    private void renderDepthRow(Writer artefact) throws IOException
    {
        artefact.append("<tr class=\"totals\">");
        artefact.append("<td style=\"padding-left:1000px\">") //
                        .append("&raquo; " + Messages.HtmlOutputter_Msg_TreeIsLimited).append("</td>");
        artefact.append("</tr>");
    }

    @SuppressWarnings("nls")
    private void renderDataColumns(Context context, Writer artefact, RefinedStructuredResult structured,
                    Column[] columns, Object row, boolean hasDetailsLink) throws IOException
    {
        for (int columnIndex = 1; columnIndex < columns.length; columnIndex++)
        {
            if (context.isColumnVisible(columnIndex))
            {
                switch (columns[columnIndex].getAlign())
                {
                    case RIGHT:
                        artefact.append("<td align=\"center\">");
                        break;
                    case CENTER:
                        artefact.append("<td align=\"center\">");
                        break;
                    case LEFT:
                    default:
                        artefact.append("<td>");
                        break;
                }

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
                            URL url = p.getIcon();
                            String iconUrl = context.addIcon(url);
                            if (iconUrl != null)
                            {
                                String alt = altText(url);
                                artefact.append("<img src=\"").append(iconUrl).append("\" alt=\""+alt+"\">");
                            }
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

    @SuppressWarnings("nls")
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
                renderLink(context, artefact, structured, row, label, columns, columnIndex);
            else
            {
                // Is there a context corresponding to this column
                if (!renderLink(context, artefact, structured, row, label,columns, columnIndex))
                    artefact.append(label);
            }
        }

        if (decorator != null)
        {
            String suffix = decorator.suffix(row);
            if (suffix != null)
                artefact.append(" <strong>").append(HTMLUtils.escapeText(suffix)).append("</strong>");
        }
    }

    /**
     * Is this new context significantly different from the base context and worth displaying.
     * If they both are simple contexts and the new id is different and not -1, then display it.
     * If either are IContextObjectSets then they probably are different.
     * @param c1
     * @param c2
     * @return
     */
    private boolean moreContextObjects(IContextObject c1, IContextObject c2) {
        if (c2 == null) return false;
        if (c1 == null) return true;
        if (c1.equals(c2)) return false;
        if (c1.getObjectId() != c2.getObjectId() && c2.getObjectId() != -1) return true;
        if (!(c1 instanceof IContextObjectSet) && !(c2 instanceof IContextObjectSet)) return false;
        return true;
    }

    @SuppressWarnings("nls")
    private boolean renderLink(Context context, Writer artefact, IStructuredResult thing, Object row, String label, Column columns[], int columnIndex)
                    throws IOException
    {
        IContextObject ctx = thing.getContext(row);
        if (columnIndex == 0)
            renderContext(context, label, null, ctx, artefact);
        boolean first = true;
        boolean done = false;
        for (ContextProvider prov : thing.getResultMetaData().getContextProviders())
        {
            IContextObject ctx1 = prov.getContext(row);
            if (moreContextObjects(ctx, ctx1))
            {
                if (columnIndex == 0)
                {
                    int c;
                    for (c = 1; c < columns.length; ++c)
                        if (columns[c].getLabel().equals(prov.getLabel()))
                            break;
                    if (c < columns.length)
                        continue;
                }
                else if (!columns[columnIndex].getLabel().equals(prov.getLabel()))
                    continue;
                if (first)
                {
                    first = false;
                    // unordered lists in tables don't work with the internal browser
                    //artefact.append("<ul>");
                    done = true;
                }
                if (columnIndex == 0)
                {
                    artefact.append("<br>");
                    //artefact.append("<li>");
                    String contextLabel = HTMLUtils.escapeText(prov.getLabel());
                    renderContext(context, contextLabel, prov.getIcon(), ctx1, artefact);
                    //artefact.append("</li>");
                }
                else
                {
                    // Other column, so render label with the link
                    renderContext(context, label, prov.getIcon(), ctx1, artefact);
                }
            }
        }
        if (done)
        {
            //artefact.append("</ul>");
        }
        return done;
    }

    @SuppressWarnings("nls")
    private void renderContext(Context context, String label, URL url, IContextObject ctx, Writer artefact)
                    throws IOException
    {
        String iconURL = context.addIcon(url);
        int objectId = -1;

        if (ctx != null)
            objectId = ctx.getObjectId();

        if (objectId >= 0)
        {
            try
            {
                String externalIdentifier = context.getQueryContext().mapToExternalIdentifier(objectId);
                artefact.append("<a href=\"").append(QueryObjectLink.forObject(externalIdentifier)).append("\">");
                if (iconURL != null)
                {
                    String alt = altText(url);
                    artefact.append("<img src=\"").append(iconURL).append("\" alt=\""+alt+"\">");
                }
                artefact.append(label).append("</a>");
            }
            catch (SnapshotException exception)
            {
                if (iconURL != null)
                {
                    String alt = altText(url);
                    artefact.append("<img src=\"").append(iconURL).append("\" alt=\""+alt+"\">");
                }
                artefact.append(label);
            }
        }
        else
        {
            if (iconURL != null)
            {
                String alt = altText(url);
                artefact.append("<img src=\"").append(iconURL).append("\" alt=\""+alt+"\">");
            }
            artefact.append(label);
        }
        if (ctx instanceof IContextObjectSet)
        {
            IContextObjectSet set = (IContextObjectSet)ctx;
            String oqlCommand = set.getOQL();
            boolean complexoql = oqlCommand != null && !oqlCommand.matches("SELECT . FROM");
            int objs[] = set.getObjectIds();
            // At least one object, and not a single object the same as the base context
            if (objs.length > 0 && (complexoql || !(objs.length == 1 && objs[0] == objectId)))
            {
                int n = Math.min(maxLinkObjects, objs.length);
                if (objs.length > n && oqlCommand != null || complexoql)
                {
                    artefact.append("<br><a href=\"");
                    StringBuilder sb = new StringBuilder("oql");
                    sb.append(" ");
                    String escapedCommand = Converters.convertAndEscape(String.class, oqlCommand);
                    sb.append(escapedCommand);
                    artefact.append(QueryObjectLink.forQuery(sb.toString())).append("\">");
                    artefact.append(Messages.HtmlOutputter_Label_AllObjects);
                    artefact.append("</a>");
                }
                else
                {
                    artefact.append("<br><a href=\"");
                    StringBuilder sb = new StringBuilder("list_objects");
                    for (int i = 0; i < n; ++i)
                    {
                        try
                        {
                            String externalIdentifier = context.getQueryContext().mapToExternalIdentifier(objs[i]);
                            sb.append(" ");
                            sb.append(externalIdentifier);
                        }
                        catch (SnapshotException e)
                        {}
                    }
                    artefact.append(QueryObjectLink.forQuery(sb.toString())).append("\">");
                    if (n < objs.length)
                    {
                        artefact.append(MessageUtil.format(Messages.HtmlOutputter_Label_FirstObjects, n, objs.length));
                    }
                    else
                    {
                        artefact.append(MessageUtil.format(Messages.HtmlOutputter_Label_AllNObjects, objs.length));
                    }
                    artefact.append("</a>");
                }
            }
        }
    }

    private void renderText(Context context, TextResult textResult, Writer writer, boolean hasDetailsLink)
                    throws IOException
    {
        if (textResult.isHtml())
        {
            writer.append("<div>");//$NON-NLS-1$
            String html = textResult.getText();
            if (!html.startsWith("<p") && !html.startsWith("<h")) //$NON-NLS-1$ //$NON-NLS-2$
                writer.append("<p>"); //$NON-NLS-1$
            if (!hasDetailsLink)
                writer.append(html);
            else
                resolveDetailLinks(context, textResult, writer);
            // Hard to work out whether the <p> tag has been closed by another tag
            writer.append("</div>");//$NON-NLS-1$
        }
        else
        {
            // <pre> is a block level tag so cannot be surrounded by <p>
            writer.append("<pre>"); //$NON-NLS-1$
            writer.append(HTMLUtils.escapeText(textResult.getText()));
            writer.append("</pre>"); //$NON-NLS-1$
        }
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
