/*******************************************************************************
 * Copyright (c) 2008, 2010 Chris Grindstaff.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Chris Grindstaff - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("primitive_arrays_with_a_constant_value")
public class PrimitiveArraysWithAConstantValueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    @Help("The array objects. Only primitive arrays will be examined.")
    public IHeapObjectArgument objects;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        listener.subTask(Messages.PrimitiveArraysWithAConstantValueQuery_SearchingArrayValues);

        ArrayInt result = new ArrayInt();

        ObjectLoop: for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    break ObjectLoop;

                if (!snapshot.isArray(objectId))
                    continue;

                IObject object = snapshot.getObject(objectId);
                if (object instanceof IObjectArray)
                    continue;

                IPrimitiveArray array = (IPrimitiveArray) object;

                int length = array.getLength();
                if (length > 1)
                {
                    boolean allSame = true;
                    Object value0 = array.getValueAt(0);
                    for (int i = 1; i < length; i++)
                    {
                        Object valueAt = array.getValueAt(i);
                        if (valueAt.equals(value0))
                            continue;
                        else
                        {
                            allSame = false;
                            break;
                        }
                    }
                    if (allSame)
                        result.add(objectId);
                }
            }
        }

        return new ObjectListResult.Inbound(snapshot, result.toArray());
    }

}
