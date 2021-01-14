/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *     Andrew Johnson (IBM Corporation) - help
 *******************************************************************************/
// Goals:
//
// 1.) Histogram on all objects with finalize() method
//
// 2A.) Histogram on all objects not ready for finalization
// 2B.) Histogram on all objects ready for finalization (including pending) +
// Overall Retained Size/Set
// Maybe instead (References distributed per state):
// 2A.) Active: queue = ReferenceQueue with which instance is registered, or
// ReferenceQueue.NULL if it was not registered with a queue; next = null.
// 2B.) Pending: queue = ReferenceQueue with which instance is registered;
// next = Following instance in queue, or this if at end of list.
// 2C.) Enqueued: queue = ReferenceQueue.ENQUEUED; next = Following instance
// in queue, or this if at end of list.
// 2D.) Inactive: queue = ReferenceQueue.NULL; next = this.
//
// Finalizer specific:
// 3.) Object currently in finalization (possibly hanging)
// 4.) Histogram on all objects which are already finalized but which are
// retained by unfinalized objects (possible illegal dependency)
//
// Weak specific:
// 5.) Weak referents only

package org.eclipse.mat.inspections.finalizer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.ReferenceQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_in_processing")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingfinalizer.html")
public class FinalizerInProcessingQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] finalizerThreadObjects = FinalizerThreadQuery.getFinalizerThreads(snapshot);

        SetInt result = new SetInt();

        for (int i : finalizerThreadObjects)
        {
            IObject finalizerThreadObject = snapshot.getObject(i);
            IObject[] localVars = getLocalVarsForThread(finalizerThreadObject);
            boolean foundFinalizer = false;
            for (IObject object : localVars)
            {
                if ("java.lang.ref.Finalizer".equals(object.getClazz().getName())) //$NON-NLS-1$
                {
                    foundFinalizer = true;
                    ObjectReference ref = ReferenceQuery.getReferent((IInstance)object);
                    if (ref != null)
                    {
                        result.add(ref.getObjectId());
                    }
                }
            }
            if (!foundFinalizer)
            {
                // No java.lang.ref.Finalizer object found found, so add all the
                // thread locals
                // to ensure the finalizable object is found.
                for (IObject object : localVars)
                {
                    if ((object.getObjectId() != i) // shouldn't be the
                                    // finalizer thread itself
                                    && !object.getClazz().getName().equals("java.lang.ref.ReferenceQueue") // exclude the queue also //$NON-NLS-1$
                                    && !object.getClazz().getName().equals("java.lang.ref.ReferenceQueue$Lock")) // exclude also the queue lock //$NON-NLS-1$
                    {
                        result.add(object.getObjectId());
                    }
                }
            }
        }

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

    private static IObject[] getLocalVarsForThread(IObject thread) throws SnapshotException
    {
        List<NamedReference> refs = thread.getOutboundReferences();
        List<IObject> result = new ArrayList<IObject>();
        for (NamedReference ref : refs)
        {
            if (ref instanceof ThreadToLocalReference)
                result.add(ref.getObject());
        }
        return result.toArray(new IObject[result.size()]);
    }
}
