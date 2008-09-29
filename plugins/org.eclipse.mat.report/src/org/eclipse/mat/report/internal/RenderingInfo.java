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
import java.net.URL;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.Spec;

/* package */class RenderingInfo implements IOutputter.Context
{
    private ResultRenderer resultRenderer;
    private QueryPart part;

    private boolean[] visibleColumns;
    private int limit;
    private boolean showTotals = true;

    public RenderingInfo(QueryPart part, ResultRenderer resultRenderer)
    {
        this.part = part;
        this.resultRenderer = resultRenderer;
        this.limit = 25;
    }

    public RenderingInfo(QueryPart part, ResultRenderer resultRenderer, int columnCount)
    {
        this(part, resultRenderer);
        visibleColumns = new boolean[columnCount];
        for (int ii = 0; ii < visibleColumns.length; ii++)
            visibleColumns[ii] = true;
    }

    public String getId()
    {
        return part.getId();
    }

    public File getOutputDirectory()
    {
        return resultRenderer.getOutputDirectory(part);
    }

    public IQueryContext getQueryContext()
    {
        return resultRenderer.getQueryContext();
    }

    public String addIcon(URL icon)
    {
        return resultRenderer.addIcon(icon, part);
    }

    public String getPathToRoot()
    {
        return resultRenderer.getPathToRoot(part);
    }

    public String addContextResult(String name, IResult result)
    {
        if (result instanceof Spec)
            name = ((Spec) result).getName();
        
        QuerySpec spec = new QuerySpec(name, result);
        spec.set("$embedded", "true");

        QueryPart child = new QueryPart(this.part, spec);
        String filename = ResultRenderer.DIR_PAGES + File.separator + child.getId() + ".html";
        child.setFilename(filename);
        part.children.add(child);
        return resultRenderer.getPathToRoot(part) + filename.replace(File.separatorChar, '/');
    }

    public boolean hasLimit()
    {
        return limit >= 0;
    }

    public int getLimit()
    {
        return limit;
    }

    public boolean isColumnVisible(int columnIndex)
    {
        return visibleColumns[columnIndex];
    }

    public boolean isTotalsRowVisible()
    {
        return showTotals;
    }

    public String param(String key)
    {
        return part.params().get(key);
    }

    public String param(String key, String defaultValue)
    {
        return part.params().get(key, defaultValue);
    }

    public void setShowTotals(boolean showTotals)
    {
        this.showTotals = showTotals;
    }

    public void setColumnVisible(int columnIndex, boolean isVisible)
    {
        this.visibleColumns[columnIndex] = isVisible;
    }

    public void setLimit(int limit)
    {
        this.limit = limit;
    }
}
