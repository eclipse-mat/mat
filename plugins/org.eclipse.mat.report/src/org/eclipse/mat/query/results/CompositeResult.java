/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
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
 * If it is returned from an IQuery without {@link #setAsHtml(boolean)} being set to true
 * then the Memory Analyzer graphical user interface displays each result as a separate tab.
 * If {@link #setAsHtml(boolean)} has been set true or 
 * if the CompositeResult is incorporated into an HTML report then each result appears 
 * as a separate HTML section.
 */
public final class CompositeResult implements IResult
{
    /**
     * An individual sub-result
     */
    public static class Entry
    {
        String name;
        IResult result;

        private Entry(String name, IResult result)
        {
            this.name = name;
            this.result = result;
        }

        /**
         * Get the name of the sub-result
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * Get the sub-result
         * @return the sub-result
         */
        public IResult getResult()
        {
            return result;
        }

    }

    private List<Entry> entries;

    private boolean asHtml = false;
    private ITestResult.Status status;
    private String name;

    /**
     * Build a result out of several others
     * @param results a list of results
     */
    public CompositeResult(IResult... results)
    {
        this.entries = new ArrayList<Entry>();

        for (IResult result : results)
            this.entries.add(new Entry(null, result));
    }

    /**
     * Get the metadata (none).
     * @return null
     */
    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    /**
     * @deprecated Use {@link #getResultEntries()} instead
     * @return the multiple results from a {@link CompositeResult}
     */
    @Deprecated
    public List<IResult> getResults()
    {
        List<IResult> answer = new ArrayList<IResult>(entries.size());
        for (Entry entry : entries)
            answer.add(entry.getResult());
        return answer;
    }

    /**
     * Get a list of the sub-results
     * @return an unmodifiable list
     */
    public List<Entry> getResultEntries()
    {
        return Collections.unmodifiableList(entries);
    }

    /**
     * See if there are sub-results
     * @return if no sub-results
     */
    public boolean isEmpty()
    {
        return entries.isEmpty();
    }

    /**
     * Add one more result
     * @param result the sub-result
     */
    public void addResult(IResult result)
    {
        this.entries.add(new Entry(null, result));
    }

    /**
     * Add one more result with the given name
     * @param name the name
     * @param result the sub-result
     */
    public void addResult(String name, IResult result)
    {
        this.entries.add(new Entry(name, result));
    }

    /**
     * A combined status
     * @return the status
     */
    public ITestResult.Status getStatus()
    {
        return status;
    }

    /**
     * Set the combined status
     * @param status the new status
     */
    public void setStatus(ITestResult.Status status)
    {
        this.status = status;
    }

    /**
     * Whether to display the results as HTML.
     * @return true if to be HTML.
     */
    public boolean asHtml()
    {
        return asHtml;
    }

    /**
     * Change the HTML setting.
     * @param asHtml true if HTML required
     */
    public void setAsHtml(boolean asHtml)
    {
        this.asHtml = asHtml;
    }

    /**
     * Get the name of this whole report.
     * @return the name of the report
     */
    public String getName()
    {
        return name;
    }

    /**
     * Set the name of this whole report.
     * @param name the name of the report
     */
    public void setName(String name)
    {
        this.name = name;
    }

}
