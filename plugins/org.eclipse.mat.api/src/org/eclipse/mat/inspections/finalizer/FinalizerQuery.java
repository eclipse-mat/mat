/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
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
package org.eclipse.mat.inspections.finalizer;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.SimpleMonitor;

@CommandName("finalizer_overview")
@Icon("/META-INF/icons/finalizer.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingfinalizer.html")
public class FinalizerQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SimpleMonitor monitor = new SimpleMonitor(Messages.FinalizerQuery_Finalizers, listener, new int[] { 100, 100, 100, 100 });
        SectionSpec spec = new SectionSpec(Messages.FinalizerQuery_Finalizers);

        IResult result = SnapshotQuery.lookup("finalizer_in_processing", snapshot).execute(monitor.nextMonitor()); //$NON-NLS-1$
        QuerySpec inProcessing = new QuerySpec(Messages.FinalizerQuery_InProcessing, result);
        inProcessing.setCommand("finalizer_in_processing"); //$NON-NLS-1$
        spec.add(inProcessing);

        result = SnapshotQuery.lookup("finalizer_queue", snapshot).execute(monitor.nextMonitor()); //$NON-NLS-1$
        QuerySpec finalizerQueue = new QuerySpec(Messages.FinalizerQuery_ReadyForFinalizer, result);
        finalizerQueue.setCommand("finalizer_queue"); //$NON-NLS-1$
        spec.add(finalizerQueue);
        finalizerQueue.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());

        result = SnapshotQuery.lookup("finalizer_thread", snapshot).execute(monitor.nextMonitor()); //$NON-NLS-1$
        QuerySpec finalizerThread = new QuerySpec(Messages.FinalizerQuery_FinalizerThread, result);
        finalizerThread.setCommand("finalizer_thread"); //$NON-NLS-1$
        spec.add(finalizerThread);

        result = SnapshotQuery.lookup("finalizer_thread_locals", snapshot).execute(monitor.nextMonitor()); //$NON-NLS-1$
        QuerySpec finalizerLocals = new QuerySpec(Messages.FinalizerQuery_FinalizerThreadLocals, result);
        finalizerLocals.setCommand("finalizer_thread_locals"); //$NON-NLS-1$
        spec.add(finalizerLocals);
        listener.done();
        return spec;
    }
}
