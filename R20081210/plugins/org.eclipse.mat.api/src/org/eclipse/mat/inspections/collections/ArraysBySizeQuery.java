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

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

@Name("Arrays Grouped By Size")
@Category("Java Collections")
@Help("Distribution histogram of given arrays by their size.")
public class ArraysBySizeQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    @Help("The array objects. Non-array objects will be ignored.")
    public IHeapObjectArgument objects;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask("Extracting array sizes...");
        
        // group by size attribute
        Quantize.Builder builder = Quantize.valueDistribution(new Column("Length", int.class));
        builder.column("# Objects", Quantize.COUNT);
        builder.column("Shallow Heap", Quantize.SUM_LONG, SortDirection.DESC);
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

                int size = snapshot.getHeapSize(objectId);
                quantize.addValue(objectId, size, null, size);
            }
        }
        return quantize.getResult();
    }
}
