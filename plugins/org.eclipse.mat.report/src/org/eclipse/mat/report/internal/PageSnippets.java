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
 *    Andrew Johnson (IBM Corporation) - accessibility improvements
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.LinkedList;
import java.util.Locale;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.report.ITestResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.internal.ResultRenderer.HtmlArtefact;
import org.eclipse.mat.report.internal.ResultRenderer.Key;
import org.eclipse.mat.util.HTMLUtils;

@SuppressWarnings("nls")
/* package */class PageSnippets
{

    public static void beginPage(final AbstractPart part, HtmlArtefact artefact, String title, String encoding)
    {
        String lang = getLang(part);
        artefact.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"" +
                        " \"http://www.w3.org/TR/html4/loose.dtd\">");
        artefact.append("<html").append(lang).append("><head>");
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
        artefact.append("<div><input type=\"hidden\" id=\"imageBase\" value=\"").append(artefact.getPathToRoot()).append(
                        "img/\" title=\"").append(Messages.PageSnippets_Label_HideUnhide).append("\"></div>");

        // role="navigation" doesn't pass HTML4 validation
        artefact.append("<div id=\"header\">");
        artefact.append("<a class=\"sr-only\" href=\"#content\">").append(Messages.PageSnippets_Label_SkipToMainContent).append("</a>");
        artefact.append("<ul>");

        if (part == null)
        {
            artefact.append("<li>");
            beginLink(artefact, "index.html");
            artefact.append(Messages.PageSnippets_Label_StartPage);
            endLink(artefact);
            artefact.append("</li>");
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
                String targetlang = getLang(p);
                link(artefact, page.getRelativePathName() + "#" + id(p), p.spec().getName(), targetlang, targetlang);
                artefact.append("</li>");

                isFirst = false;
            }

            artefact.append("<li>");
            if (!isFirst)
                artefact.append("&raquo; ");

            /* Can't link directly to the part id as the details part is on another page */
            String id = "content";
            artefact.append("<a href=\"#" + id + "\">").append(HTMLUtils.escapeText(part.spec().getName())).append("</a></li>");
        }

        artefact.append("</ul></div>\n");
        // role="main" doesn't pass HTML4 validation
        artefact.append("<div id=\"content\">");
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
        artefact.append("</div>");
        // role="contentinfo" doesn't pass HTML4 validation
        artefact.append("<div id=\"footer\" class=\"toc\">");
        beginLink(artefact, "toc.html");
        artefact.append(Messages.PageSnippets_Label_TableOfContents);
        endLink(artefact);

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
        String lang = getLang(part);
        boolean showHeading = part.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);
        if (!showHeading)
        {
            artefact.append("<a").append(lang).append(" id=\"").append(id(part)).append("\"></a>");
        }
        else
        {
            String v = String.valueOf(Math.min(order, 5));
            if (linkToHeading)
                artefact.append("<h").append(v).append(lang).append(" id=\"").append(id(part)).append("\">");
            else
                artefact.append("<h").append(v).append(lang).append(">");

            if (isExpandable)
            {
                boolean isExpanded = forceExpansion || !part.params().getBoolean(Params.Html.COLLAPSED, false);

                artefact.append("<a href=\"#\" onclick=\"hide(this, '").append(id("exp", part)) //
                                .append("'); return false;\" title=\"") //
                                .append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                                .append("\"><img src=\"") //
                                .append(artefact.getPathToRoot()).append(isExpanded ? OPENED : CLOSED) //
                                .append("\" alt=\"")
                                //.append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                                .append("\"></a> ");
            }

            ITestResult.Status status = getStatus(part);
            if (status != null)
                artefact.append("<img src=\"").append(artefact.getPathToRoot()) //
                                .append("img/").append(status.name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(status.toString()).append("\"> ");

            if (!linkToHeading)
                artefact.append("<a id=\"").append(id(part)).append("\">");
            artefact.append(HTMLUtils.escapeText(part.spec().getName()));
            if (!linkToHeading)
                artefact.append("</a>");
            addCommandLink(part, artefact);
            artefact.append("</h").append(v).append(">");
        }
    }

    public static void linkedHeading(HtmlArtefact artefact, AbstractPart part, int order, String filename)
    {
        String lang = getLang(part);
        String v = String.valueOf(order);
        artefact.append("<h").append(v).append(lang).append(" id=\"").append(id(part)).append("\">");

        ITestResult.Status status = getStatus(part);
        if (status != null)
            artefact.append("<img src=\"").append(artefact.getPathToRoot()).append("img/").append(
                            status.name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(status.toString()).append("\"> ");

        link(artefact, filename, part.spec().getName());
        addCommandLink(part, artefact);
        artefact.append("</h").append(v).append(">");
    }

    public static void queryHeading(HtmlArtefact artefact, QueryPart query, boolean forceExpansion)
    {
        String lang = getLang(query);
        boolean showHeading = query.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);

        if (!showHeading)
        {
            artefact.append("<a").append(lang).append(" id=\"").append(id(query)).append("\"></a>");
        }
        else
        {
            if (linkToHeading)
                artefact.append("<h5").append(lang).append(" id=\"").append(id(query)).append("\"");
            else
                artefact.append("<h5").append(lang);
            boolean isImportant = query.params().shallow().getBoolean(Params.Html.IS_IMPORTANT, false);
            if (isImportant)
            {
                artefact.append(" class=\"important\"");
            }
            artefact.append(">");

            boolean isExpanded = forceExpansion || !query.params().getBoolean(Params.Html.COLLAPSED, false);

            artefact.append("<a href=\"#\" onclick=\"hide(this, '").append(id("exp", query)) //
                            .append("'); return false;\" title=\"") //
                            .append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                            .append("\"><img src=\"") //
                            .append(artefact.getPathToRoot()).append(isExpanded ? OPENED : CLOSED)
                            .append("\" alt=\"")
                            //.append(isExpanded ? Messages.PageSnippets_Label_HideUnhide : Messages.PageSnippets_Label_UnhideHide) //
                            .append("\"></a> ");

            if (query.getStatus() != null)
                artefact.append("<img src=\"").append(artefact.getPathToRoot()).append("img/").append(
                                query.getStatus().name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"").append(query.getStatus().toString()).append("\">");

            if (!linkToHeading)
                artefact.append("<a id=\"").append(id(query)).append("\">");
            artefact.append(HTMLUtils.escapeText(query.spec().getName()));
            if (!linkToHeading)
                artefact.append("</a>");
            addCommandLink(query, artefact);
            artefact.append("</h5>");
        }
    }

    public static void link(HtmlArtefact artefact, String target, String label)
    {
        link(artefact, target, label, "", "");
    }

    static void link(HtmlArtefact artefact, String target, String label, String linklang, String targetlang)
    {
        beginLink(artefact, target, linklang, targetlang);
        artefact.append(HTMLUtils.escapeText(label));
        endLink(artefact);
    }

    public static void beginLink(HtmlArtefact artefact, String target)
    {
        beginLink(artefact, target, "", "");
    }

    public static void beginLink(HtmlArtefact artefact, String target, AbstractPart part)
    {
        String lang = getLang(part);
        beginLink(artefact, target + "#" + id(part), lang, lang);
    }

    static void beginLink(HtmlArtefact artefact, String target, String linklang, String targetlang)
    {
        targetlang = targetlang.replaceFirst("^ lang=", " hreflang=");
        artefact.append("<a").append(linklang).append(targetlang).append(" href=\"").append(artefact.getPathToRoot()).append(target).append("\">");
    }

    public static void endLink(HtmlArtefact artefact)
    {
        artefact.append("</a>");
    }

    public static void beginExpandableDiv(HtmlArtefact artefact, AbstractPart part, boolean forceExpanded)
    {
        String lang = getLang(part, true);
        artefact.append("<div").append(lang).append(" id=\"").append(id("exp", part)).append("\"");
        if (!forceExpanded && part.params().getBoolean(Params.Html.COLLAPSED, false))
            artefact.append(" style=\"display:none\"");
        artefact.append(">");
    }

    public static void endDiv(HtmlArtefact artefact)
    {
        artefact.append("</div>");
    }

    /**
     * Retrieve the current language of the spec of the part
     * with a " lang=" prefix
     * or nothing if it is the same as the parent part.
     * @param part
     * @return ' lang="en-US"' etc. or "" 
     */
    static String getLang(AbstractPart part)
    {
        return getLang(part, false);
    }

    /**
     * Retrieve the current language of the part or spec
     * with a " lang=" prefix
     * or nothing if it is the same as the parent part.
     * @param part Query 
     * @return ' lang="en-US"' etc. or "" 
     */
    static String getLang(AbstractPart part, boolean usePart)
    {
        if (part == null)
            return "";
        String lang = usePart ? part.params().get("lang") : part.spec().getParams().get("lang");
        if (lang == null)
            return "";
        AbstractPart parent = part.getParent();
        if (parent != null && part.getDataFile().getUrl().equals(parent.getDataFile().getUrl()))
        {
            String langp = parent.params().get("lang");
            if (lang.equals(langp))
                return "";
            // Nothing defined further up which will be mapped to empty 
            if (langp == null && lang.isEmpty())
                return "";
        }
        return " lang=\"" + lang + "\"";
    }

    private static String id(String prefix, AbstractPart part)
    {
        return prefix + part.getId();
    }

    private static String id(AbstractPart part)
    {
        return id("i", part);
    }

    /**
     * The test result might not have been cascaded up yet, so evaluate now.
     */
    private static ITestResult.Status getStatus(AbstractPart part)
    {
        ITestResult.Status status = part.getStatus();
        Spec s = part.spec();
        if (s instanceof SectionSpec)
            status = ITestResult.Status.max(status, ((SectionSpec)s).getStatus());
        else if (s instanceof QuerySpec)
        {
            IResult result = ((QuerySpec)s).getResult();
            if (result instanceof ITestResult)
                status = ITestResult.Status.max(status, ((ITestResult)result).getStatus());
            if (result instanceof CompositeResult)
                status = ITestResult.Status.max(status, ((CompositeResult)result).getStatus());
        }
        for (AbstractPart child : part.getChildren())
        {
            ITestResult.Status status2 = getStatus(child);
            status = ITestResult.Status.max(status, status2);
        }
        return status;
    }
}
