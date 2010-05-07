/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.finalizer;

import java.util.Collection;

import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.ReferenceQuery;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_queue")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
public class FinalizerQueueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> finalizerClasses = snapshot.getClassesByName("java.lang.ref.Finalizer", false); //$NON-NLS-1$

        // Avoid duplicates by using a set
        SetInt result = new SetInt();

        if (finalizerClasses == null || finalizerClasses.isEmpty())
        {
            // Ignore as there may be finalizable objects marked via GC roots
        }
        else
        {
            if (finalizerClasses.size() != 1)
                throw new Exception(Messages.FinalizerQueueQuery_ErrorMsg_MultipleFinalizerClasses);

            // Extracting objects ready for finalization from queue
            IClass finalizerClass = finalizerClasses.iterator().next();
            IObject queue = (IObject) finalizerClass.resolveValue("queue"); //$NON-NLS-1$

            if (queue != null)
            {

                IInstance item = (IInstance) queue.resolveValue("head"); //$NON-NLS-1$
                int length = ((Long) queue.resolveValue("queueLength")).intValue(); //$NON-NLS-1$
                int threshold = length / 100;
                int worked = 0;
                listener.beginTask(Messages.FinalizerQueueQuery_Msg_ExtractingObjects, length);

                while (item != null)
                {
                    if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }

                    ObjectReference ref = ReferenceQuery.getReferent(item);
                    if (ref != null)
                    {
                        result.add(ref.getObjectId());
                    }

                    IInstance next = (IInstance) item.resolveValue("next"); //$NON-NLS-1$
                    if (next == item)
                    {
                        next = null;
                    }
                    item = next;

                    if (++worked >= threshold)
                    {
                        listener.worked(worked);
                        if (listener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();
                        worked = 0;
                    }
                }

                listener.done();
            }
        }

        // Add other objects marked as finalizable
        for (int root : snapshot.getGCRoots())
        {
            GCRootInfo ifo[] = snapshot.getGCRootInfo(root);
            for (GCRootInfo rootInfo : ifo)
            {
                if (rootInfo.getType() == GCRootInfo.Type.FINALIZABLE)
                {
                    result.add(rootInfo.getObjectId());
                    break;
                }
            }
        }

        SectionSpec spec = new SectionSpec(Messages.FinalizerQueueQuery_ReadyForFinalizerThread);
        QuerySpec objList = new QuerySpec(Messages.FinalizerQueueQuery_ReadyForFinalizerThread_List, //
                        new ObjectListResult.Outbound(snapshot, result.toArray()));
        spec.add(objList);

        QuerySpec histogram = new QuerySpec(Messages.FinalizerQueueQuery_ReadyForFinalizerThread_Histogram, //
                        snapshot.getHistogram(result.toArray(), listener));
        histogram.set("sort_column", Messages.Column_RetainedHeap); //$NON-NLS-1$
        histogram.set("derived_data_column", "_default_=APPROXIMATE"); //$NON-NLS-1$//$NON-NLS-2$
        spec.add(histogram);
        return spec;
    }
}
