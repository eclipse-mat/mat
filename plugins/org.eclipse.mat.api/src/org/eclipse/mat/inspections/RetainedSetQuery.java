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
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("show_retained_set")
@Icon("/META-INF/icons/show_retained_set.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/retained_set.html")
public class RetainedSetQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false, flag = "f")
    public String[] fieldNames;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] retainedSet;

        if (fieldNames == null)
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), listener);
        else
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), fieldNames, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = snapshot.getHistogram(retainedSet, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(MessageUtil.format(Messages.RetainedSetQuery_RetainedBy, objects.getLabel()));
        return histogram;
    }

}
