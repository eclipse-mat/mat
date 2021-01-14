/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
        AbstractPart child = null;

        if (result instanceof Spec)
            child = part.factory.create(this.part, (Spec) result);
        else
            child = part.factory.create(this.part, new QuerySpec(name, result));

        child.params().put("$embedded", "true"); //$NON-NLS-1$ //$NON-NLS-2$

        DataFile dataFile = child.getDataFile();
        String filename = dataFile.getUrl();
        if (filename == null)
            filename = dataFile.getSuggestedFile();

        if (filename == null)
        {
            filename = ResultRenderer.DIR_PAGES + '/' + child.getId() + ".html"; //$NON-NLS-1$
            dataFile.setSuggestedFile(filename);
        }

        if (!(child instanceof LinkedPart))
            part.children.add(child);

        return resultRenderer.getPathToRoot(part) + filename;
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
