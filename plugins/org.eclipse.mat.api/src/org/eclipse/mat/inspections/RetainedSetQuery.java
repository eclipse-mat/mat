/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.mat.util.SimpleMonitor;

@CommandName("show_retained_set")
@Icon("/META-INF/icons/show_retained_set.gif")
@HelpUrl("/org.eclipse.mat.ui.help/concepts/shallowretainedheap.html")
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

        SimpleMonitor monitor = new SimpleMonitor(
                        MessageUtil.format(Messages.RetainedSetQuery_ProgressName, objects.getLabel()), listener,
                        new int[] { 10, 100, 10 });
        if (fieldNames == null)
            retainedSet = snapshot.getRetainedSet(objects.getIds(monitor.nextMonitor()), monitor.nextMonitor());
        else
            retainedSet = snapshot.getRetainedSet(objects.getIds(monitor.nextMonitor()), fieldNames, monitor.nextMonitor());

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = snapshot.getHistogram(retainedSet, monitor.nextMonitor());

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(MessageUtil.format(Messages.RetainedSetQuery_RetainedBy, objects.getLabel()));
        listener.done();
        return histogram;
    }

}
