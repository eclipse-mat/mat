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
package org.eclipse.mat.inspections.finalizer;

import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_overview")
@Icon("/META-INF/icons/finalizer.gif")
public class FinalizerQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd"); //$NON-NLS-1$

        SectionSpec spec = new SectionSpec(Messages.FinalizerQuery_Finalizers);

        IResult result = SnapshotQuery.lookup("finalizer_in_processing", snapshot).execute(listener); //$NON-NLS-1$
        spec.add(new QuerySpec(Messages.FinalizerQuery_InProcessing, result));

        result = SnapshotQuery.lookup("finalizer_queue", snapshot).execute(listener); //$NON-NLS-1$
        QuerySpec finalizerQueue = new QuerySpec(Messages.FinalizerQuery_ReadyForFinalizer, result);
        spec.add(finalizerQueue);
        finalizerQueue.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());

        result = SnapshotQuery.lookup("finalizer_thread", snapshot).execute(listener); //$NON-NLS-1$
        spec.add(new QuerySpec(Messages.FinalizerQuery_FinalizerThread, result));

        result = SnapshotQuery.lookup("finalizer_thread_locals", snapshot).execute(listener); //$NON-NLS-1$
        spec.add(new QuerySpec(Messages.FinalizerQuery_FinalizerThreadLocals, result));

        return spec;
    }
}
