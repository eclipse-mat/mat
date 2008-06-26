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
import org.eclipse.mat.report.IOutputter;

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

    public boolean hasLimit()
    {
        return limit >= 0;
    }

    public boolean isVisible(int columnIndex)
    {
        return visibleColumns[columnIndex];
    }

    public boolean showTotals()
    {
        return showTotals;
    }

    public String getId()
    {
        return part.getId();
    }

    public int getLimit()
    {
        return limit;
    }

    public File getOutputDirectory()
    {
        return resultRenderer.getOutputDirectory();
    }

    public IQueryContext getQueryContext()
    {
        return resultRenderer.getQueryContext();
    }

    public String getRelativeIconLink(URL icon)
    {
        return resultRenderer.getRelativeIconLink(icon);
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
