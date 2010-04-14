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
package org.eclipse.mat.snapshot.extension;

import java.util.Collection;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * Holds detailed information about a thread 
 */
public interface IThreadInfo
{
    /**
     * The thread id
     * @return
     */
    int getThreadId();

    /**
     * The actual thread object
     * @return
     */
    IObject getThreadObject();

    /**
     * To add particular information
     * @param column
     * @param value
     */
    void setValue(Column column, Object value);

    /**
     * To add a keyword used for error report summaries
     * @param keyword
     */
    void addKeyword(String keyword);

    /**
     * Add details - doesn't appear to be used.
     * @param name subtitle for the report
     * @param details the result containing the details
     */
    void addDetails(String name, IResult details);

    /**
     * Add requests such as the URL from a web server
     * @param summary a title
     * @param details the result containing the request
     * @return
     */
    void addRequest(String summary, IResult details);

    /**
     * Get requests such as the URL from a web server
     * @return a combination result of all the requests
     */
    CompositeResult getRequests();

    /**
     * Get a set of keywords reflecting a high level description of the situation.
     * @return
     */
    Collection<String> getKeywords();

    /**
     * The context class loader for the thread
     * @return the id of the class loader
     */
    int getContextClassLoaderId();
}
