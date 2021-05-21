/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG.
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
package org.eclipse.mat.inspections;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("oql")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/oql.gif")
@Usage("oql \"select * from ...\"")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/queryingheapobjects.html")
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
            else if (result instanceof List<?>)
            {
                return new ObjectTableResultImpl(snapshot, queryString, (List<?>) result);
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

    static class ObjectListResultImpl extends ObjectListResult.Outbound implements IOQLQuery.Result,
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

    static class OQLTextResult extends TextResult implements IOQLQuery.Result
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


    static class ObjectTableResultImpl implements IOQLQuery.Result,
                    IResultTable, IDecorator
    {
        String queryString;
        List<?>objs;
        boolean hasIObjects;

        public ObjectTableResultImpl(ISnapshot snapshot, String queryString, List<?>objs)
        {
            this.queryString = queryString;
            this.objs = objs;
            for (Object o : objs)
            {
                if (o instanceof IObject)
                {
                    hasIObjects = true;
                    break;
                }
            }
        }

        public String getOQLQuery()
        {
            return queryString;
        }

        @Override
        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        @Override
        public Column[] getColumns()
        {
            if (hasIObjects)
                return new Column[] { new Column(Messages.Column_ClassName).decorator(this), //
                                new Column(Messages.Column_ShallowHeap, Bytes.class).noTotals(), //
                                new Column(Messages.Column_RetainedHeap, Bytes.class).noTotals() };
            else
                return new Column[] { new Column(Messages.Column_ClassName) };
        }

        @Override
        public Object getColumnValue(Object row, int columnIndex)
        {
            int rw = (Integer)row;
            //Object o;
            switch (columnIndex)
            {
                case 0:
                    Object o = objs.get(rw);
                    if (o instanceof IObject)
                        return ((IObject)o).getDisplayName();
                    else if (o != null)
                        return o.toString();
                    else
                        return null;
                case 1:
                    if (!hasIObjects)
                        throw new IllegalArgumentException();
                    o = objs.get(rw);
                    if (o instanceof IObject)
                        return ((IObject)o).getUsedHeapSize();
                    else
                        return null;
                case 2:
                    if (!hasIObjects)
                        throw new IllegalArgumentException();
                    o = objs.get(rw);
                    if (o instanceof IObject)
                        return ((IObject)o).getRetainedHeapSize();
                    else
                        return null;
                default:
                    throw new IllegalArgumentException(row.toString());
            }
        }

        @Override
        public IContextObject getContext(Object row)
        {
            if (!hasIObjects)
                return null;
            int rw = (Integer)row;
            return new IContextObjectSet() {

                @Override
                public int getObjectId()
                {
                    Object o = objs.get(rw);
                    if (o instanceof IObject)
                    try
                    {
                        return ((IObject)o).getObjectId();
                    }
                    catch (RuntimeException e)
                    {
                        if (e.getCause() instanceof SnapshotException)
                            return -1;
                        throw e;
                    }
                    return -1;
                }

                @Override
                public int[] getObjectIds()
                {
                    return new int[0];
                }

                @Override
                public String getOQL()
                {
                    Object o = objs.get(rw);
                    if (o instanceof IObject)
                        return OQL.forAddress(((IObject)o).getObjectAddress());
                    else
                        return null;
                }
            };
        }

        @Override
        public int getRowCount()
        {
            return objs.size();
        }

        @Override
        public Object getRow(int rowId)
        {
            return Integer.valueOf(rowId);
        }

        @Override
        public String prefix(Object row)
        {
            return null;
        }

        @Override
        public String suffix(Object row)
        {
            return null;
        }
    }
}
