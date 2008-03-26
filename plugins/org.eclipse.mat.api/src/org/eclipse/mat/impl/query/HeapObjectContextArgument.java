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
package org.eclipse.mat.impl.query;

import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;


public class HeapObjectContextArgument implements IHeapObjectFactory
{
    List<IContextObject> context;
    String label;

    public HeapObjectContextArgument(List<IContextObject> context, String label)
    {
        this.context = context;
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label != null ? "<" + label + ">" : "<context>";
    }

    public IHeapObjectArgument create(final ISnapshot snapshot)
    {
        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                return HeapObjectContextArgument.this.getIds(snapshot, listener);
            }

            public Iterator<int[]> iterator()
            {
                return new Iterator<int[]>()
                {
                    Iterator<IContextObject> iter = context.iterator();

                    public boolean hasNext()
                    {
                        return iter.hasNext();
                    }

                    public int[] next()
                    {
                        IContextObject ctx = iter.next();

                        if (ctx instanceof IContextObjectSet)
                            return ((IContextObjectSet) ctx).getObjectIds();
                        else
                            return new int[] { ctx.getObjectId() };
                    }

                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            public String getLabel()
            {
                return "<context>";
            }
        };
    }

    private int[] getIds(ISnapshot snapshot, IProgressListener listener)
    {
        ArrayIntBig objIdxs = new ArrayIntBig();
        for (IContextObject ctx : context)
        {
            if (ctx instanceof IContextObjectSet)
                objIdxs.addAll(((IContextObjectSet) ctx).getObjectIds());
            else
                objIdxs.add(ctx.getObjectId());
        }

        return objIdxs.toArray();
    }

}
