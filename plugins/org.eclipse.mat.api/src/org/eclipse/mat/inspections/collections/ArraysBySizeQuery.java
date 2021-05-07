/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.Arrays;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("arrays_grouped_by_size")
@Icon("/META-INF/icons/array_size.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"byte[]", "boolean[]", "short[]", "char[]", "int[]", "float[]", "long[]", "double[]", "java.lang.Object[]"})
public class ArraysBySizeQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    private static class Result
    {
        final int len;
        final long size;
        public Result(int len, long size)
        {
            this.len = len;
            this.size = size;
        }
    }

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.ArraysBySizeQuery_ExtractingArraySizes);

        // group by length and size attribute
        Quantize.Builder builder = Quantize.valueDistribution( //
                        new Column(Messages.ArraysBySizeQuery_ColumnLength, int.class).noTotals(),
                        new Column(Messages.ArraysBySizeQuery_SingleInstanceSize, Bytes.class).noTotals());
        builder.column(Messages.ArraysBySizeQuery_ColumnNumObjects, Quantize.COUNT);
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
                prev = objectId;
            }
            for (int objectId : sortedObjs)
            {
                if (listener.isCanceled())
                    break;

                if (!snapshot.isArray(objectId))
                    continue;
                
                if (counter++ % 1000 == 0 && snapshot.getClassOf(objectId).equals(type))
                {
                    type = snapshot.getClassOf(objectId);
                    listener.subTask(Messages.ArraysBySizeQuery_ExtractingArraySizes + "\n" + type.getName()); //$NON-NLS-1$
                }

                IObject obj = snapshot.getObject(objectId);
                int len = (obj instanceof IArray) ? ((IArray)obj).getLength() : 0;
                long size = snapshot.getHeapSize(objectId);
                if (resultMap != null)
                    resultMap.put(objectId, new Result(len, size));
                else
                    quantize.addValue(objectId, len, size, null, size);
            }
            if (resultMap != null)
            {
                for (int objectId : objectIds)
                {
                    if (resultMap.containsKey(objectId))
                    {
                        Result r = resultMap.get(objectId);
                        quantize.addValue(objectId, r.len, r.size, null, r.size);
                    }
                }
            }
            if (listener.isCanceled())
                break;
        }
        return quantize.getResult();
    }
}
