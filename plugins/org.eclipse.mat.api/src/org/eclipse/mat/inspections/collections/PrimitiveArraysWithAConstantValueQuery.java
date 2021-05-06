/*******************************************************************************
 * Copyright (c) 2008, 2021 Chris Grindstaff, James Livingston and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Chris Grindstaff - initial API and implementation
 *    James Livingston - expose collection utils as API
 *    Andrew Johnson/IBM Corporation - add icon
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.Arrays;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("primitive_arrays_with_a_constant_value")
@Icon("/META-INF/icons/constant_value.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"byte[]", "boolean[]", "short[]", "char[]", "int[]", "float[]", "long[]", "double[]"})
public class PrimitiveArraysWithAConstantValueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    @Help("The array objects. Only primitive arrays will be examined.")
    public IHeapObjectArgument objects;

    private static class Result
    {
        final int len;
        final Object value;
        final long used;
        public Result(int len, Object value, long used)
        {
            this.len = len;
            this.value = value;
            this.used = used;
        }
    }
    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        listener.subTask(Messages.PrimitiveArraysWithAConstantValueQuery_SearchingArrayValues);

        // group by size attribute
        Quantize.Builder builder = Quantize.valueDistribution( //
                        new Column(Messages.PrimitiveArraysWithAConstantValueQuery_Column_Length, int.class).noTotals(),
                        new Column(Messages.PrimitiveArraysWithAConstantValueQuery_Column_Value, Comparable.class).noTotals());
        builder.column(Messages.PrimitiveArraysWithAConstantValueQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_BYTES, SortDirection.DESC);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        int counter = 0;
        IClass type = null;
        for (int[] objectIds : objects)
        {
            HashMapIntObject<Result> resultMap = null;
            int sortedObjs[] = objectIds;
            int prev = Integer.MIN_VALUE;
            for (int objectId : objectIds)
            {
                if (objectId < prev)
                {
                    sortedObjs = objectIds.clone();
                    Arrays.sort(sortedObjs);
                    resultMap = new HashMapIntObject<Result>();
                    break;
                }
                objectId = prev;
            }
            for (int objectId : sortedObjs)
            {
                if (listener.isCanceled())
                    break;

                if (!snapshot.isArray(objectId))
                    continue;

                IObject obj = snapshot.getObject(objectId);
                if (obj instanceof IObjectArray)
                    continue;
                if (counter++ % 1000 == 0 && !obj.getClazz().equals(type))
                {
                    type = obj.getClazz();
                    listener.subTask(Messages.PrimitiveArraysWithAConstantValueQuery_SearchingArrayValues + "\n" + type.getName()); //$NON-NLS-1$
                }

                IPrimitiveArray array = (IPrimitiveArray) obj;

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
                    {
                        long size = snapshot.getHeapSize(objectId);
                        // Key by length and value
                        if (resultMap != null)
                            resultMap.put(objectId, new Result(length, value0, size));
                        else
                            quantize.addValue(objectId, length, value0, null, size);
                    }
                }
            }
            if (resultMap != null)
            {
                for (int objectId : objectIds)
                {
                    if (resultMap.containsKey(objectId))
                    {
                        Result r = resultMap.get(objectId);
                        quantize.addValue(objectId, r.len, r.value, null, r.used);
                    }
                }
            }
            if (listener.isCanceled())
                break;
        }
        return quantize.getResult();
    }
}
