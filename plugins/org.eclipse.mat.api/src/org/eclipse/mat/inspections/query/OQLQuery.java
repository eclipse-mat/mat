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
package org.eclipse.mat.inspections.query;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.IProgressListener;


@Name("OQL")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/oql.gif")
@Usage("oql \"select * from ...\"")
@Help("Execute an OQL Statement.")
public class OQLQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public String queryString;

    public IOQLQuery.Result execute(IProgressListener listener) throws Exception
    {

        try
        {
            IOQLQuery query = SnapshotFactory.createQuery(queryString);
            Object result = query.execute(snapshot, listener);

            if (result == null)
            {
                return new OQLTextResult("Your Query did not yield any result.\n\n" + query, queryString);
            }
            else if (result instanceof IOQLQuery.Result)
            {
                return (IOQLQuery.Result) result;
            }
            else if (result instanceof int[])
            {
                return new ObjectListResult(snapshot, queryString, (int[]) result);
            }
            else
            {
                return new OQLTextResult(String.valueOf(result), queryString);
            }
        }
        catch (IProgressListener.OperationCanceledException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder buf = new StringBuilder(256);
            buf.append("Executed Query:\n");
            buf.append(queryString);

            Throwable t = null;
            if (e instanceof SnapshotException)
            {
                buf.append("\n\nProblem reported:\n");
                buf.append(e.getMessage());
                t = e.getCause();
            }
            else
            {
                t = e;
            }

            if (t != null)
            {
                buf.append("\n\n");
                StringWriter w = new StringWriter();
                PrintWriter o = new PrintWriter(w);
                t.printStackTrace(o);
                o.flush();

                buf.append(w.toString());
            }

            return new OQLTextResult(buf.toString(), queryString);
        }
    }

    private static class ObjectListResult extends ObjectListQuery.OutboundObjects implements IOQLQuery.Result, IResultTree
    {
        String queryString;

        public ObjectListResult(ISnapshot snapshot, String queryString, int[] objectIds)
        {
            super(snapshot, objectIds);
            this.queryString = queryString;
        }

        public String getOQLQuery()
        {
            return queryString;
        }
    }

    private static class OQLTextResult extends TextResult implements IOQLQuery.Result
    {
        String queryString;

        public OQLTextResult(String text, String queryString)
        {
            super(text);
            this.queryString = queryString;
        }

        public String getOQLQuery()
        {
            return queryString;
        }
    }

}
