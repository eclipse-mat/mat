/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

class ArrayIndexExpression extends Expression
{
    List<Expression> parameters;

    public ArrayIndexExpression(List<Expression> parameters)
    {
        this.parameters = parameters;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
    {
        Object subject = ctx.getSubject();
        if (subject == null)
            return null;

        // compute arguments
        Object[] arguments = new Object[parameters.size()];
        for (int ii = 0; ii < arguments.length; ii++)
            arguments[ii] = parameters.get(ii).compute(ctx);

        Object indexObj = arguments[0];
        int index;
        if (indexObj instanceof Integer || indexObj instanceof Short || indexObj instanceof Byte)
        {
            index = ((Number) arguments[0]).intValue();
        }
        else
        {
            // Don't show the array to show that just the index is wrong
            throw new IllegalArgumentException(toString()+": "+(indexObj != null ? indexObj.getClass() : null)); //$NON-NLS-1$
        }

        if (subject.getClass().isArray())
        {
            int len = Array.getLength(subject);
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            return Array.get(subject, index);
        }
        else if (subject instanceof List)
        {
            List<?> l = (List<?>) subject;
            int len = l.size();
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            return ((List<?>) subject).get(index);
        }
        else if (subject instanceof IPrimitiveArray)
        {
            IPrimitiveArray array = (IPrimitiveArray) subject;
            int len = array.getLength();
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            if (index >= array.getLength())
                return null;
            return array.getValueAt(index);
        }
        else if (subject instanceof IObjectArray)
        {
            IObjectArray array = (IObjectArray) subject;
            int len = array.getLength();
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            if (index >= array.getLength())
                return null;
            long addr = array.getReferenceArray(index, 1)[0];
            if (addr == 0)
                return null;
            ISnapshot snapshot = array.getSnapshot();
            int objectId = snapshot.mapAddressToId(addr);
            return snapshot.getObject(objectId);
        }
        else
        {
            // Show the array, which is wrong, and the index for context
            throw new IllegalArgumentException(subject + toString()+": "+subject.getClass()); //$NON-NLS-1$
        }

    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        for (Expression element : this.parameters)
        {
            boolean isContextDependent = element.isContextDependent(ctx);

            if (isContextDependent)
                return true;
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        buf.append("[");//$NON-NLS-1$

        for (Iterator<Expression> iter = this.parameters.iterator(); iter.hasNext();)
        {
            Expression element = iter.next();
            buf.append(element);

            if (iter.hasNext())
                buf.append(",");//$NON-NLS-1$
        }

        buf.append("]");//$NON-NLS-1$

        return buf.toString();
    }

}
