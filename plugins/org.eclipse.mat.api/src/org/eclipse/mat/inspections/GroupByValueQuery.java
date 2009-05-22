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
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@Name("Group By Value")
@Category("Java Basics")
@Help("Group objects by their string representation.\n\n"
                + "By default, the objects are grouped by the the class specific name resolver. "
                + "Alternatively, one can specify a field using the dot notation, whose name "
                + "resolver is then used.\n\n" //
                + "Examples:\n" //
                + "To find duplicate strings, run:\n\tgroup_by_value java.lang.String\n"
                + "To group array lists by their size, run:\n\tgroup_by_value java.util.ArrayList -field size\n")
public class GroupByValueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    @Help("An optional dot notation to specify a field which is used to group the objects, "
                    + "e.g. modCount to group HashMaps by their modifications.")
    public String field;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        listener.subTask("Grouping objects ...");

        Quantize quantize = Quantize.valueDistribution("String Value") //
                        .column("Objects", Quantize.COUNT) //
                        .column("Shallow Heap", Quantize.SUM_LONG, SortDirection.DESC) //
                        .column("Avg. Retained Size", Quantize.AVERAGE_LONG) //
                        .addDerivedData(RetainedSizeDerivedData.APPROXIMATE) //
                        .build();

        boolean canceled = false;
        for (int[] objectIds : objects)
        {
            for (int ii = 0; ii < objectIds.length; ii++)
            {
                if (listener.isCanceled())
                {
                	canceled = true;
                	break;
                }

                int objectId = objectIds[ii];
                IObject object = snapshot.getObject(objectId);

                Object subject = object;
                if (field != null)
                    subject = object.resolveValue(field);

                if (subject instanceof IObject)
                    subject = ((IObject) subject).getClassSpecificName();

                quantize.addValue(objectId, subject, null, object.getUsedHeapSize(), object.getRetainedHeapSize());
            }
            if (canceled)
            	break;
        }

        return quantize.getResult();
    }
}
