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
package org.eclipse.mat.query;

import java.text.MessageFormat;
import java.util.Iterator;

import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


public class QueryUtil
{

    private QueryUtil()
    {}

    public static IResult execute(IQuery query, IProgressListener listener) throws Exception
    {
        check(query);
        return query.execute(listener);
    }

    public static void check(IQuery query) throws SnapshotException
    {
        try
        {
            QueryDescriptor descriptor = QueryRegistry.instance().getQuery(query.getClass());

            for (ArgumentDescriptor argument : descriptor.getArguments())
            {
                if (argument.isMandatory())
                {
                    Object value = argument.getField().get(query);
                    if (value == null)
                        throw new SnapshotException(MessageFormat.format(
                                        "Field ''{0}'' of ''{1}'' is mandatory, but not set.", argument.getName(),
                                        query.getClass()));
                }
            }
        }
        catch (IllegalAccessException e)
        {
            throw new SnapshotException(e);
        }

    }

    public static IHeapObjectArgument asArgument(final IObject object)
    {
        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                return new int[] { object.getObjectId() };
            }

            public String getLabel()
            {
                return object.getDisplayName();
            }

            public Iterator<int[]> iterator()
            {
                return new Iterator<int[]>()
                {
                    boolean done = false;

                    public boolean hasNext()
                    {
                        return !done;
                    }

                    public int[] next()
                    {
                        done = true;
                        return new int[] { object.getObjectId() };
                    }

                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public static IHeapObjectArgument asArgument(final int objectId)
    {
        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                return new int[] { objectId };
            }

            public String getLabel()
            {
                return "Object " + objectId;
            }

            public Iterator<int[]> iterator()
            {
                return new Iterator<int[]>()
                {
                    boolean done = false;

                    public boolean hasNext()
                    {
                        return !done;
                    }

                    public int[] next()
                    {
                        done = true;
                        return new int[] { objectId };
                    }

                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public static IHeapObjectArgument asArgument(final int[] objects)
    {
        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                return objects;
            }

            public String getLabel()
            {
                return "Objects";
            }

            public Iterator<int[]> iterator()
            {
                return new Iterator<int[]>()
                {
                    boolean done = false;

                    public boolean hasNext()
                    {
                        return !done;
                    }

                    public int[] next()
                    {
                        done = true;
                        return objects;
                    }

                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
