/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot;

import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

public class HeapObjectContextArgument extends HeapObjectArgumentFactory
{
    List<IContextObject> context;
    String label;

    public HeapObjectContextArgument(ISnapshot snapshot, List<IContextObject> context, String label)
    {
        super(snapshot);
        this.context = context;
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label != null ? "[" + label + "]" : Messages.HeapObjectContextArgument_Label_Context; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void appendUsage(StringBuilder buf)
    {
        buf.append(toString());
    }

    @Override
    protected int[] asIntegerArray() throws SnapshotException
    {
        return asObjectArgument().getIds(new VoidProgressListener());
    }

    @Override
    protected IHeapObjectArgument asObjectArgument()
    {
        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                return HeapObjectContextArgument.this.getIds(listener);
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
                return label;
            }
        };
    }

    private int[] getIds(IProgressListener listener)
    {
        ArrayIntBig objIdxs = new ArrayIntBig();
        for (IContextObject ctx : context)
        {
            if (ctx instanceof IContextObjectSet)
                objIdxs.addAll(((IContextObjectSet) ctx).getObjectIds());
            else
                objIdxs.add(ctx.getObjectId());

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        return objIdxs.toArray();
    }

}
