/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("oql")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/oql.gif")
@Usage("oql \"select * from ...\"")
public class OQLQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public String queryString;

    public IOQLQuery.Result execute(IProgressListener listener) throws Exception
    {

        try
        {
            IOQLQuery query = SnapshotFactory.createQuery(queryString);
            Object result = query.execute(snapshot, listener);

            if (result == null)
            {
                return new OQLTextResult(Messages.OQLQuery_NoResult + "\n\n" + query, queryString); //$NON-NLS-1$
            }
            else if (result instanceof IOQLQuery.Result)
            {
                return (IOQLQuery.Result) result;
            }
            else if (result instanceof int[])
            {
                return new ObjectListResultImpl(snapshot, queryString, (int[]) result);
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
            buf.append(Messages.OQLQuery_ExecutedQuery + "\n"); //$NON-NLS-1$
            buf.append(queryString);

            Throwable t = null;
            if (e instanceof SnapshotException)
            {
                buf.append("\n\n" + Messages.OQLQuery_ProblemReported + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
                buf.append(e.getMessage());
                t = e.getCause();
            }
            else
            {
                t = e;
            }

            if (t != null)
            {
                buf.append("\n\n"); //$NON-NLS-1$
                StringWriter w = new StringWriter();
                PrintWriter o = new PrintWriter(w);
                t.printStackTrace(o);
                o.flush();

                buf.append(w.toString());
            }

            return new OQLTextResult(buf.toString(), queryString);
        }
    }

    private static class ObjectListResultImpl extends ObjectListResult.Outbound implements IOQLQuery.Result,
                    IResultTree
    {
        String queryString;

        public ObjectListResultImpl(ISnapshot snapshot, String queryString, int[] objectIds)
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
