/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - remove newlines in titles
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;

public class QueryResult
{
    private QueryResult parent;
    private QueryDescriptor query;
    private String command;
    private IResult subject;

    /** caching */
    private ResultMetaData resultMetaData;

    /** caching */
    private ContextProvider defaultContextProvider;

    public QueryResult(QueryDescriptor query, String command, IResult subject)
    {
        this(null, query, command, subject);
    }

    public QueryResult(QueryResult parent, QueryDescriptor query, String command, IResult subject)
    {
        this.parent = parent;
        this.query = query;
        this.command = command;
        this.subject = subject;

        resultMetaData = subject != null ? subject.getResultMetaData() : null;
        if (resultMetaData == null)
            resultMetaData = new ResultMetaData.Builder().build();

        if (subject instanceof IStructuredResult)
        {
            defaultContextProvider = new ContextProvider((String) null)
            {
                @Override
                public IContextObject getContext(Object row)
                {
                    return ((IStructuredResult) QueryResult.this.subject).getContext(row);
                }
            };
        }
    }

    public QueryResult getParent()
    {
        return parent;
    }

    public String getCommand()
    {
        return command;
    }

    public QueryDescriptor getQuery()
    {
        return query;
    }

    public IResult getSubject()
    {
        return subject;
    }

    public ResultMetaData getResultMetaData()
    {
        return resultMetaData;
    }

    public String getTitle()
    {
        return command.replaceAll("[\\r\\n]", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public String getTitleToolTip()
    {
        return command.replaceAll("[\\r\\n]", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public ContextProvider getDefaultContextProvider()
    {
        return defaultContextProvider;
    }

}
