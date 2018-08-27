/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("group_by_value")
@Icon("/META-INF/icons/group_by_value.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/group_by_value.html")
public class GroupByValueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public String field;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.GroupByValueQuery_GroupingObjects);

        Quantize quantize = Quantize.valueDistribution(Messages.GroupByValueQuery_Column_StringValue) //
                        .column(Messages.GroupByValueQuery_Column_Objects, Quantize.COUNT) //
                        .column(Messages.Column_ShallowHeap, Quantize.SUM_LONG, SortDirection.DESC) //
                        .column(Messages.GroupByValueQuery_Column_AvgRetainedSize, Quantize.AVERAGE_LONG) //
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
