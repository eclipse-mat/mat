/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
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

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
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

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.ArraysBySizeQuery_ExtractingArraySizes);

        // group by size attribute
        Quantize.Builder builder = Quantize.valueDistribution( //
                        new Column(Messages.ArraysBySizeQuery_ColumnLength, int.class));
        builder.column(Messages.ArraysBySizeQuery_ColumnNumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG, SortDirection.DESC);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

                if (!snapshot.isArray(objectId))
                    continue;

                long size = snapshot.getHeapSize(objectId);
                quantize.addValue(objectId, size, null, size);
            }
        }
        return quantize.getResult();
    }
}
