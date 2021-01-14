/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - accessibility improvements
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.LinkedList;
import java.util.Locale;

import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.internal.ResultRenderer.HtmlArtefact;
import org.eclipse.mat.report.internal.ResultRenderer.Key;
import org.eclipse.mat.util.HTMLUtils;

@SuppressWarnings("nls")
/* package */class PageSnippets
{

    public static void beginPage(final AbstractPart part, HtmlArtefact artefact, String title, String encoding)
    {
        artefact.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"" +
                        " \"http://www.w3.org/TR/html4/loose.dtd\">");
        artefact.append("<html><head>");
        try
        {
            // Convert to canonical form
            encoding = Charset.forName(encoding).name();
        }
        catch (IllegalCharsetNameException e)
        {
            // Ignore
        }
        artefact.append("<meta http-equiv=\"Content-type\" content=\"text/html;charset="+encoding+"\">");
        artefact.append("<title>").append(HTMLUtils.escapeText(title)).append("</title>");
        artefact.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(artefact.getPathToRoot()).append(
                        "styles.css\">");
        artefact.append("<link rel=\"contents\" href=\"").append(artefact.getPathToRoot()).append(
                        "toc.html\">");
        artefact.append("<link rel=\"start\" href=\"").append(artefact.getPathToRoot()).append(
                        "index.html\">");

        artefact.append("<script src=\"").append(artefact.getPathToRoot()).append(
                        "code.js\" type=\"text/javascript\"></script>");
        artefact.append("</head><body onload=\"preparepage();\">");

        // Pass the text used for hide / unhide 
        artefact.append("<input type=\"hidden\" id=\"imageBase\" value=\"").append(artefact.getPathToRoot()).append(
                        "img/\" title=\"").append(Messages.PageSnippets_Label_HideUnhide).append("\">");

        artefact.append("<div id=\"header\"><ul>");

        if (part == null)
        {
            artefact.append("<li><a href=\"").append(artefact.getPathToRoot()).append(
                            "index.html\">" + Messages.PageSnippets_Label_StartPage + "</a></li>");
        }
        else
        {
            LinkedList<AbstractPart> path = new LinkedList<AbstractPart>();
            AbstractPart tmp = part;
            while (tmp.getParent() != null)
            {
                AbstractPart parent = tmp.getParent();

                boolean showHeading = parent.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);
                if (showHeading)
                    path.addFirst(parent);

                tmp = parent;
            }

            boolean isFirst = true;

            for (AbstractPart p : path)
            {
                HtmlArtefact page = (HtmlArtefact) p.getObject(Key.ARTEFACT);
                tmp = p;
                while (page == null)
                    page = (HtmlArtefact) (tmp = tmp.getParent()).getObject(Key.ARTEFACT);

                artefact.append("<li>");
                if (!isFirst)
                    artefact.append("&raquo; ");
                PageSnippets.beginLink(artefact, page.getRelativePathName() + "#" + part.getId());
                artefact.append(HTMLUtils.escapeText(p.spec().getName()));
                PageSnippets.endLink(artefact);
                artefact.append("</li>");

                isFirst = false;
            }

            artefact.append("<li>");
            if (!isFirst)
                artefact.append("&raquo; ");
            artefact.append("<a href=\"#\">").append(HTMLUtils.escapeText(part.spec().getName())).append("</a></li>");
        }

        artefact.append("</ul></div>\n");
    }

    private static void addCommandLink(AbstractPart part, HtmlArtefact artefact)
    {
        if (part.getCommand() != null) {
            String cmdString = null;

            try
            {
                cmdString = URLEncoder.encode(part.getCommand(), "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                // $JL-EXC$
                // should never happen as UTF-8 is always supported
                cmdString = part.getCommand();
            }

            artefact.append(" <a href=\"").append(QueryObjectLink.forQuery(part.getCommand())) //
                            .append("\" title=\"").append(Messages.PageSnippets_Label_OpenInMemoryAnalyzer).append(" ") //
                            .append(cmdString).append("\"><img src=\"") //
                            .append(artefact.getPathToRoot()).append("img/open.gif\" alt=\"")
                            //.append(Messages.PageSnippets_Label_OpenInMemoryAnalyzer).append(" ") //
                            //.append(cmdString)
                            .append("\"></a>");
        }
    }

    public static void endPage(HtmlArtefact artefact)
    {
        artefact.append("<br>");
        artefact.append("<div id=\"footer\">");

        artefact.append("<div class=\"toc\"><a href=\"").append(artefact.getPathToRoot()).append(
                        "toc.html\">" + Messages.PageSnippets_Label_TableOfContents + "</a></div>");

        artefact.append("<div class=\"mat\">");
        artefact.append(Messages.PageSnippets_Label_CreatedBy);
        artefact.append("</div>");

        artefact.append("</div>\n");

        artefact.append("</body></html>");
    }

    private static final String OPENED = "img/opened.gif";
    private static final String CLOSED = "img/closed.gif";
    private static boolean linkToHeading = true;

    public static void heading(HtmlArtefact artefact, AbstractPart part, int order, boolean isExpandable,
                    boolean forceExpansion)
    {
        boolean showHeading = part.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);
        if (!showHeading)
        {
            artefact.append("<a name=\"").append(part.getId()).append("\"></a>");
        }
        else
        {
            String v = String.valueOf(Math.min(order, 5));
            if (linkToHeading)
                artefact.append("<h").append(v).append(" id=\"").append(part.getId()).append("\">");
            else
                artefact.append("<h").append(v).append(">");

            if (isExpandable)
            {
                boolean isExpanded = forceExpansion || !part.params().getBoolean(Params.Html.COLLAPSED, false);

                artefact.append("<a href=\"#\" onclick=\"hide(this, 'exp").append(part.getId()) //
                                .append("'); return false;\" title=\"") //
                                .append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                                .append("\"><img src=\"") //
                                .append(artefact.getPathToRoot()).append(isExpanded ? OPENED : CLOSED) //
                                .append("\" alt=\"")
                                //.append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                                .append("\"></a> ");
            }

            if (part.getStatus() != null)
                artefact.append("<img src=\"").append(artefact.getPathToRoot()) //
                                .append("img/").append(part.getStatus().name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(part.getStatus().toString()).append("\"> ");

            if (!linkToHeading)
                artefact.append("<a name=\"").append(part.getId()).append("\">");
            artefact.append(HTMLUtils.escapeText(part.spec().getName()));
            if (!linkToHeading)
                artefact.append("</a>");
            addCommandLink(part, artefact);
            artefact.append("</h").append(v).append(">");
        }
    }

    public static void linkedHeading(HtmlArtefact artefact, AbstractPart part, int order, String filename)
    {
        String v = String.valueOf(order);
        artefact.append("<h").append(v).append(">");

        if (part instanceof QueryPart && part.getStatus() != null)
            artefact.append("<img src=\"").append(artefact.getPathToRoot()).append("img/").append(
                            part.getStatus().name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(part.getStatus().toString()).append("\"> ");

        artefact.append("<a href=\"").append(artefact.getPathToRoot()).append(filename).append("\">");
        artefact.append(HTMLUtils.escapeText(part.spec().getName()));
        artefact.append("</a>");
        addCommandLink(part, artefact);
        artefact.append("</h").append(v).append(">");
    }

    public static void queryHeading(HtmlArtefact artefact, QueryPart query, boolean forceExpansion)
    {
        boolean showHeading = query.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);

        if (!showHeading)
        {
            artefact.append("<a name=\"").append(query.getId()).append("\"></a>");
        }
        else
        {
            if (linkToHeading)
                artefact.append("<h5 id=\"").append(query.getId()).append("\">");
            else
                artefact.append("<h5>");

            boolean isExpanded = forceExpansion || !query.params().getBoolean(Params.Html.COLLAPSED, false);

            artefact.append("<a href=\"#\" onclick=\"hide(this, 'exp").append(query.getId()) //
                            .append("'); return false;\" title=\"") //
                            .append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                            .append("\"><img src=\"") //
                            .append(artefact.getPathToRoot()).append(isExpanded ? OPENED : CLOSED)
                            .append("\" alt=\"")
                            //.append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                            .append("\"></a> ");

            if (query.getStatus() != null)
                artefact.append("<img src=\"").append(artefact.getPathToRoot()).append("img/").append(
                                query.getStatus().name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(query.getStatus().toString()).append("\"> ");

            if (!linkToHeading)
                artefact.append("<a name=\"").append(query.getId()).append("\">");
            artefact.append(HTMLUtils.escapeText(query.spec().getName()));
            if (!linkToHeading)
                artefact.append("</a>");
            addCommandLink(query, artefact);
            artefact.append("</h5>");
        }
    }

    public static void link(HtmlArtefact artefact, String target, String label)
    {
        artefact.append("<a href=\"").append(artefact.getPathToRoot()).append(target).append("\">");
        artefact.append(HTMLUtils.escapeText(label));
        artefact.append("</a>");
    }

    public static void beginLink(HtmlArtefact artefact, String target)
    {
        artefact.append("<a href=\"").append(artefact.getPathToRoot()).append(target).append("\">");
    }

    public static void endLink(HtmlArtefact artefact)
    {
        artefact.append("</a>");
    }

    public static void beginExpandableDiv(HtmlArtefact artefact, AbstractPart part, boolean forceExpanded)
    {
        artefact.append("<div id=\"exp").append(part.getId()).append("\"");
        if (!forceExpanded && part.params().getBoolean(Params.Html.COLLAPSED, false))
            artefact.append(" style=\"display:none\"");
        artefact.append(">");
    }

    public static void endDiv(HtmlArtefact artefact)
    {
        artefact.append("</div>");
    }

}
