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

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.CommonNameResolver;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_thread")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
public class FinalizerThreadQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int finalizerThreadObjects[] = getFinalizerThreads(snapshot);
        return new ObjectListResult.Outbound(snapshot, finalizerThreadObjects);
    }

    static int[] getFinalizerThreads(ISnapshot snapshot) throws Exception
    {
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName(
                        "java.lang.ref.Finalizer$FinalizerThread", false); //$NON-NLS-1$
        if (finalizerThreadClasses == null)
        {
            // IBM finalizer thread
            return getFinalizerThreads2(snapshot, Messages.FinalizerThreadQuery_FinalizerThread);
        }
        else
        {
            // Two sorts of finalizer threads
            int a[] = getFinalizerThreads1(snapshot);
            // Created by System.runFinalization()
            int b[] = getFinalizerThreads2(snapshot, Messages.FinalizerThreadQuery_SecondaryFinalizer);
            int ret[] = new int[a.length + b.length];
            System.arraycopy(a, 0, ret, 0, a.length);
            System.arraycopy(b, 0, ret, a.length, b.length);
            return ret;
        }
    }

    private static int[] getFinalizerThreads1(ISnapshot snapshot) throws SnapshotException, Exception
    {
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName(
                        "java.lang.ref.Finalizer$FinalizerThread", false); //$NON-NLS-1$
        if (finalizerThreadClasses == null)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_FinalizerThreadNotFound);
        if (finalizerThreadClasses.size() != 1)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_MultipleThreads);

        int[] finalizerThreadObjects = finalizerThreadClasses.iterator().next().getObjectIds();
        if (finalizerThreadObjects == null)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_FinalizerThreadInstanceNotFound);
        if (finalizerThreadObjects.length != 1)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_MultipleFinalizerThreadClasses);
        return finalizerThreadObjects;
    }

    private static int[] getFinalizerThreads2(ISnapshot snapshot, String finalizerThreadName) throws Exception
    {
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName("java.lang.Thread", false); //$NON-NLS-1$
        if (finalizerThreadClasses == null)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_ThreadClassNotFound);
        if (finalizerThreadClasses.size() != 1)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_MultipleThreadClassesFound);

        int[] finalizerThreadObjects = finalizerThreadClasses.iterator().next().getObjectIds();
        if (finalizerThreadObjects == null)
            throw new Exception(Messages.FinalizerThreadQuery_ErrorMsg_ThreadInstanceNotFound);
        int finalizerThreadObjectsLength = 0;
        for (int objectId : finalizerThreadObjects)
        {
            IObject o = snapshot.getObject(objectId);
            CommonNameResolver.ThreadResolver t = new CommonNameResolver.ThreadResolver();
            String name = t.resolve(o);
            if (name != null && name.equals(finalizerThreadName))
            {
                finalizerThreadObjects[finalizerThreadObjectsLength++] = objectId;
            }
        }
        int ret[] = new int[finalizerThreadObjectsLength];
        System.arraycopy(finalizerThreadObjects, 0, ret, 0, finalizerThreadObjectsLength);
        return ret;
    }

}
