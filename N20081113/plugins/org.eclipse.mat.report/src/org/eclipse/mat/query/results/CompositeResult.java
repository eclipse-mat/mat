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
package org.eclipse.mat.query.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.report.ITestResult;

/**
 * Return multiple result types.
 */
public final class CompositeResult implements IResult
{
    public static class Entry
    {
        String name;
        IResult result;

        private Entry(String name, IResult result)
        {
            this.name = name;
            this.result = result;
        }

        public String getName()
        {
            return name;
        }

        public IResult getResult()
        {
            return result;
        }

    }

    private List<Entry> entries;

    private boolean asHtml = false;
    private ITestResult.Status status;
    private String name;

    public CompositeResult(IResult... results)
    {
        this.entries = new ArrayList<Entry>();

        for (IResult result : results)
            this.entries.add(new Entry(null, result));
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    /**
     * @deprecated Use {@link #getResultEntries()} instead
     */
    @Deprecated
    public List<IResult> getResults()
    {
        List<IResult> answer = new ArrayList<IResult>(entries.size());
        for (Entry entry : entries)
            answer.add(entry.getResult());
        return answer;
    }

    public List<Entry> getResultEntries()
    {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty()
    {
        return entries.isEmpty();
    }

    public void addResult(IResult result)
    {
        this.entries.add(new Entry(null, result));
    }

    public void addResult(String name, IResult result)
    {
        this.entries.add(new Entry(name, result));
    }

    public ITestResult.Status getStatus()
    {
        return status;
    }

    public void setStatus(ITestResult.Status status)
    {
        this.status = status;
    }

    public boolean asHtml()
    {
        return asHtml;
    }

    public void setAsHtml(boolean asHtml)
    {
        this.asHtml = asHtml;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

}
