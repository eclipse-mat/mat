/*******************************************************************************
 * Copyright (c) 2025 IBM.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.NumberFormat;

@CommandName("select_objects_by_field")
@Icon("/META-INF/icons/select_objects_by_field.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/select_objects_by_field.html")
public class SelectObjectsByFieldQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = true)
    public String field;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] objectIds = objects.getIds(listener);

        SetInt finalObjects = new SetInt();
        listener.beginTask(MessageUtil.format(Messages.SelectObjectsByFieldQuery_Evaluating,
                        NumberFormat.getNumberInstance().format(objectIds.length)), objectIds.length);
        for (int objectId : objectIds)
        {
            if (listener.isCanceled())
            {
                break;
            }
            IObject object = snapshot.getObject(objectId);
            Object resolved = object.resolveValue(field);
            if (resolved != null && resolved instanceof IObject)
            {
                finalObjects.add(((IObject) resolved).getObjectId());
            }
            listener.worked(1);
        }
        listener.done();

        return new ObjectListResult.Outbound(snapshot, finalObjects.toArray());
    }
}
