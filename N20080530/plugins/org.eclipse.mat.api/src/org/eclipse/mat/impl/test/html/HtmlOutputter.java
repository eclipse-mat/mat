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
package org.eclipse.mat.impl.test.html;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.impl.query.IndividualObjectUrl;
import org.eclipse.mat.impl.result.Filter;
import org.eclipse.mat.impl.result.RefinedStructuredResult;
import org.eclipse.mat.impl.result.RefinedTable;
import org.eclipse.mat.impl.result.RefinedTree;
import org.eclipse.mat.impl.result.TotalsRow;
import org.eclipse.mat.impl.test.IOutputter;
import org.eclipse.mat.impl.test.QueryPart;
import org.eclipse.mat.impl.test.ResultRenderer.RenderingInfo;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.test.Params;
import org.eclipse.mat.util.VoidProgressListener;


@Subject("html")
public class HtmlOutputter implements IOutputter
{
    public String getExtension()
    {
        return "html";
    }

    public void embedd(Context context, QueryPart part, IResult result, RenderingInfo rInfo, Writer writer)
                    throws IOException
    {
        // TODO: remove multiple instantiations
        Map<String, IOutputter> config = new HashMap<String, IOutputter>();

        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint("org.eclipse.mat.api" + ".renderer"); //$NON-NLS-1$
        if (point != null)
        {
            IExtension[] extensions = point.getExtensions();
            for (int i = 0; i < extensions.length; i++)
            {
                IConfigurationElement confElements[] = extensions[i].getConfigurationElements();
                for (int jj = 0; jj < confElements.length; jj++)
                {
                    try
                    {
                        IOutputter pane = (IOutputter) confElements[jj].createExecutableExtension("class");
                        String r = confElements[jj].getAttribute("result");
                        config.put(r, pane);
                    }
                    catch (InvalidRegistryObjectException e)
                    {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "", e);
                    }
                    catch (CoreException e)
                    {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "", e);
                    }
                }
            }
        }

        if (result != null)
        {
            List<String> types = new ArrayList<String>();
            types.add(result.getClass().getName());
            for (Class<?> clazz : result.getClass().getInterfaces())
                types.add(clazz.getName());

            for (String type : types)
            {
                IOutputter o = config.get(type);
                if (o != null)
                {
                    o.embedd(context, part, result, rInfo, writer);
                    return;
                }
            }
        }

        if (result instanceof RefinedTable)
        {
            renderTable(context, part, (RefinedTable) result, rInfo, writer);
        }
        else if (result instanceof RefinedTree)
        {
            renderTree(context, part, (RefinedTree) result, rInfo, writer);
        }
        else if (result instanceof TextResult)
        {
            writer.append("<p>");
            writer.append(((TextResult) result).getHtml());
            writer.append("</p>");
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

    public void process(Context context, QueryPart part, IResult result, RenderingInfo renderingInfo, Writer writer)
                    throws IOException
    {
        throw new UnsupportedOperationException();
    }

    // //////////////////////////////////////////////////////////////
    // 
    // //////////////////////////////////////////////////////////////

    private void renderTable(Context context, QueryPart test, RefinedTable table, RenderingInfo rInfo, Writer artefact)
                    throws IOException
    {
        Column[] columns = table.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(test, artefact, columns, rInfo);

        artefact.append("<tbody>");

        // render filter row
        renderFilterRow(artefact, table, rInfo);

        int numberOfRowsToDisplay = (rInfo.hasLimit() && rInfo.limit < table.getRowCount()) ? rInfo.limit : table
                        .getRowCount();
        for (int rowIndex = 0; rowIndex < numberOfRowsToDisplay; rowIndex++)
        {
            Object row = table.getRow(rowIndex);

            artefact.append("<tr");

            if (table.isSelected(row))
                artefact.append(" class=\"selected\"");

            artefact.append(">");

            if (rInfo.isVisible(0))
            {
                artefact.append("<td>");

                String iconUrl = context.getRelativeIconLink(table.getIcon(row));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\">");
                renderColumnValue(context, test, artefact, table, columns, row, 0);

                artefact.append("</td>");
            }
            renderDataColumns(context, test, artefact, table, columns, row, rInfo);
            artefact.append("</tr>");
        }
        // append totals row
        renderTotalsRow(context, artefact, (RefinedStructuredResult) table, ((RefinedTable) table).getRows(),
                        numberOfRowsToDisplay, columns, rInfo, 0);

        artefact.append("</tbody></table>");
    }

    private void renderFilterRow(Writer artefact, RefinedStructuredResult result, RenderingInfo rInfo)
                    throws IOException
    {
        if (!result.hasActiveFilter())
            return;

        artefact.append("<tr class=\"filter\">");
        Filter[] filter = result.getFilter();
        for (int i = 0; i < filter.length; i++)
        {
            if (rInfo.isVisible(i))
            {
                if (filter[i].isActive())
                    artefact.append("<td>").append(HTMLUtils.escapeText(filter[i].getCriteria())).append("</td>");
                else
                    artefact.append("<td></td>");
            }
        }
        artefact.append("</tr>");
    }

    private void renderTableHeader(QueryPart test, Writer artefact, Column[] columns, RenderingInfo rInfo)
                    throws IOException
    {
        boolean showTableHeader = test.params().getBoolean(Params.Html.SHOW_TABLE_HEADER, true);
        if (showTableHeader)
        {
            artefact.append("<thead><tr>");
            for (int i = 0; i < columns.length; i++)
            {
                if (rInfo.isVisible(i))
                    artefact.append("<th>").append(columns[i].getLabel()).append("</th>");
            }
            artefact.append("</tr></thead>");
        }
    }

    private void renderTotalsRow(Context context, Writer artefact, RefinedStructuredResult result, List<?> elements,
                    int numberOfRowsToDisplay, Column[] columns, RenderingInfo rInfo, int level) throws IOException
    {
        if (rInfo.showTotals)
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
                if (rInfo.isVisible(i))
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

    private void renderTree(Context context, QueryPart test, RefinedTree tree, RenderingInfo rInfo, Writer artefact)
                    throws IOException
    {
        Column[] columns = tree.getColumns();

        artefact.append("<table class=\"result\">");

        renderTableHeader(test, artefact, columns, rInfo);

        artefact.append("<tbody>");

        renderFilterRow(artefact, tree, rInfo);

        renderChildren(context, test, artefact, tree, columns, tree.getElements(), 0, rInfo);

        artefact.append("</tbody></table>");
    }

    private void renderChildren(Context context, QueryPart test, Writer artefact, RefinedTree tree, Column[] columns,
                    List<?> elements, int level, RenderingInfo rInfo) throws IOException
    {
        int numberOfRowsToDisplay = (rInfo.hasLimit() && rInfo.limit < elements.size()) ? rInfo.limit : elements.size();
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

            if (rInfo.isVisible(0))
            {

                artefact.append("<td style=\"padding-left:").append(String.valueOf(level * 10)).append("px\">");

                String iconUrl = context.getRelativeIconLink(tree.getIcon(element));
                if (iconUrl != null)
                    artefact.append("<img src=\"").append(iconUrl).append("\">");

                renderColumnValue(context, test, artefact, tree, columns, element, 0);

                if (!isExpanded && hasChildren)
                    artefact.append(" &raquo;");

                artefact.append("</td>");
            }
            renderDataColumns(context, test, artefact, tree, columns, element, rInfo);
            artefact.append("</tr>");

            if (isExpanded && hasChildren && level < 100)
            {
                renderChildren(context, test, artefact, tree, columns, children, level + 1, rInfo);
            }
            else if (level == 100)
            {
                renderDepthRow(artefact);
            }

        }
        renderTotalsRow(context, artefact, (RefinedStructuredResult) tree, elements, numberOfRowsToDisplay, columns,
                        rInfo, level);
    }

    private void renderDepthRow(Writer artefact) throws IOException
    {
        artefact.append("<tr class=\"totals\">");
        artefact.append("<td style=\"padding-left:1000px\">").append("&raquo; Depth of the tree is limited to 100")
                        .append("</td>");
        artefact.append("</tr>");
    }

    private void renderDataColumns(Context context, QueryPart test, Writer artefact,
                    RefinedStructuredResult structured, Column[] columns, Object row, RenderingInfo rInfo)
                    throws IOException
    {
        for (int columnIndex = 1; columnIndex < columns.length; columnIndex++)
        {
            if (rInfo.isVisible(columnIndex))
            {
                if (columns[columnIndex].isNumeric())
                    artefact.append("<td align=\"right\">");
                else
                    artefact.append("<td>");

                renderColumnValue(context, test, artefact, structured, columns, row, columnIndex);
                artefact.append("</td>");
            }
        }
    }

    private void renderColumnValue(Context context, QueryPart test, Writer artefact,
                    RefinedStructuredResult structured, Column[] columns, Object row, int columnIndex)
                    throws IOException
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
                renderLink(context, test, artefact, structured, row, label);
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

    private void renderLink(Context context, QueryPart test, Writer artefact, IStructuredResult thing, Object row,
                    String label) throws IOException
    {
        int objectId = -1;

        IContextObject ctx = thing.getContext(row);
        if (ctx instanceof IContextObject)
            objectId = ctx.getObjectId();

        if (objectId >= 0)
        {
            try
            {
                long objectAddress = context.getSnapshot().mapIdToAddress(objectId);
                artefact.append(new IndividualObjectUrl(objectAddress, label).toHtml());
            }
            catch (SnapshotException ignore)
            {
                // $JL-EXC$ simply do not write the link
                artefact.append(label);
            }
        }
        else
        {
            artefact.append(label);
        }
    }

}
