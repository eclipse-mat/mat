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
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("array_fill_ratio")
public class ArrayFillRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public int segments = 5;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.ArrayFillRatioQuery_ExtractingFillRatios);

        // create frequency distribution
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(Messages.ArrayFillRatioQuery_ColumnFillRatio,
                        0, 1, (double) 1 / (double) segments);
        builder.column(Messages.ArrayFillRatioQuery_ColumnNumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        ObjectLoop: for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    break ObjectLoop;

                if (!snapshot.isArray(objectId))
                    continue;

                IObject object = snapshot.getObject(objectId);
                if (!(object instanceof IObjectArray))
                    continue;

                IObjectArray array = (IObjectArray) object;

                // 100% if the array has length 0 --> the good ones
                double fillRatio = 0;
                if (array.getLength() > 0)
                {
                    fillRatio = (double) (CollectionUtil.getNumberOfNoNullArrayElements(array))
                                    / (double) array.getLength();
                }
                quantize.addValue(objectId, fillRatio, 1, array.getUsedHeapSize());
            }
        }

        return quantize.getResult();

    }
}
