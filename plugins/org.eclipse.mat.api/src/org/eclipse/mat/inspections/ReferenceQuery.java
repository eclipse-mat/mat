/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation

 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

/**
 * Extract information about objects extending java.lang.ref.Reference, e.g.
 * weak and soft references, and Finalizer.
 */
@CommandName("references_statistics")
public class ReferenceQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;
    
    static final String DEFAULT_REFERENT = "referent"; //$NON-NLS-1$
    @Argument(isMandatory = false)
    public String referent_attribute = DEFAULT_REFERENT;
    
    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.ReferenceQuery_Msg_ComputingReferentSet);

        ArrayInt instanceSet = new ArrayInt();
        SetInt referentSet = new SetInt();

        for (int[] objs : objects)
        {
            instanceSet.addAll(objs);
            for (int ii = 0; ii < objs.length; ii++)
            {
                IObject o = snapshot.getObject(objs[ii]);
                if (o instanceof IInstance)
                {
                    IInstance obj = (IInstance) o;
                    ObjectReference ref = getReferent(obj, referent_attribute);
                    if (ref != null)
                        referentSet.add(ref.getObjectId());
                }
            }
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        return execute(instanceSet, referentSet, snapshot, Messages.ReferenceQuery_HistogramOfReferentObjects,
                        Messages.ReferenceQuery_OnlyRetainedByReferents,
                        Messages.ReferenceQuery_StronglyRetainedByReferences, referent_attribute, listener);
    }
    /**
     * Important: the <strong>className</strong> must point to
     * java.lang.ref.Reference or one of its subclasses. It is not possible to
     * check this, as some heap dumps lack class hierarchy information.
     */
    public static IResult execute(String className, ISnapshot snapshot, String labelHistogramReferenced,
                    String labelHistogramRetained, String labelHistogramStronglyRetainedReferents, IProgressListener listener) throws SnapshotException
    {
        listener.subTask(Messages.ReferenceQuery_Msg_ComputingReferentSet);

        ArrayInt instanceSet = new ArrayInt();
        SetInt referentSet = new SetInt();

        Collection<IClass> classes = snapshot.getClassesByName(Pattern.compile(className), true);
        if (classes == null)
            throw new SnapshotException(MessageUtil.format(Messages.ReferenceQuery_ErrorMsg_NoMatchingClassesFound,
                            className));

        for (IClass clazz : classes)
        {
            int[] objs = clazz.getObjectIds();
            instanceSet.addAll(objs);

            for (int ii = 0; ii < objs.length; ii++)
            {
                IInstance obj = (IInstance) snapshot.getObject(objs[ii]);

                ObjectReference ref = getReferent(obj);
                if (ref != null)
                    referentSet.add(ref.getObjectId());
            }

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        return execute(instanceSet, referentSet, snapshot, labelHistogramReferenced, labelHistogramRetained, labelHistogramStronglyRetainedReferents, DEFAULT_REFERENT, listener);
    }

    public static CompositeResult execute(ArrayInt instanceSet, SetInt referentSet, ISnapshot snapshot,
                    String labelHistogramReferenced, String labelHistogramRetained, String labelHistogramStronglyRetainedReferents, IProgressListener listener)
                    throws SnapshotException
    {
        return execute(instanceSet, referentSet, snapshot, labelHistogramReferenced, labelHistogramRetained,
                        labelHistogramStronglyRetainedReferents, DEFAULT_REFERENT, listener);
    }
    
    public static CompositeResult execute(ArrayInt instanceSet, SetInt referentSet, ISnapshot snapshot,
                    String labelHistogramReferenced, String labelHistogramRetained, String labelHistogramStronglyRetainedReferents, String referentField, IProgressListener listener)
                    throws SnapshotException
    {
        CompositeResult result = new CompositeResult();

        Histogram histogram = snapshot.getHistogram(referentSet.toArray(), listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(labelHistogramReferenced);
        result.addResult(labelHistogramReferenced, histogram);

        listener.subTask(Messages.ReferenceQuery_Msg_ComputingRetainedSet);
        int[] retainedSet = snapshot.getRetainedSet(instanceSet.toArray(), new String[] { referentField }, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram = snapshot.getHistogram(retainedSet, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(labelHistogramRetained);
        result.addResult(labelHistogramRetained, histogram);

        listener.subTask(Messages.ReferenceQuery_Msg_ComputingStronglyRetainedSet);
        int[] allRetainedSet = snapshot.getRetainedSet(instanceSet.toArray(), listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        // Exclude referent retained
        Arrays.sort(allRetainedSet);
        int newWeakRetained[] = new int[retainedSet.length];
        System.arraycopy(retainedSet, 0, newWeakRetained, 0, retainedSet.length);
        Arrays.sort(newWeakRetained);
        int[] r = new int[allRetainedSet.length - newWeakRetained.length];
        int t2 = -1;
        for (int s = 0, d = 0, w = 0; s < allRetainedSet.length; ++s)
        {
            int t1 = allRetainedSet[s];
            while (w < newWeakRetained.length && (t2 < t1))
            {
                t2 = newWeakRetained[w++];
            }
            if (t1 != t2)
                r[d++] = t1;
        }

        // Find which of the referent set are strongly retained elsewhere
        int referents[] = referentSet.toArray();
        Arrays.sort(referents);
        t2 = -1;
        int d = 0;
        for (int s = 0, w = 0; s < referents.length; ++s)
        {
            int t1 = referents[s];
            while (w < r.length && (t2 < t1))
            {
                t2 = r[w++];
            }
            if (t1 == t2)
                referents[d++] = t1;
        }
        int leakingReferents[] = new int[d];
        System.arraycopy(referents, 0, leakingReferents, 0, d);

        histogram = snapshot.getHistogram(leakingReferents, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(labelHistogramStronglyRetainedReferents);
        result.addResult(labelHistogramStronglyRetainedReferents, histogram);
        return result;
    }

    public static ObjectReference getReferent(IInstance instance) throws SnapshotException
    {
        return getReferent(instance, DEFAULT_REFERENT);
    }
    
    static ObjectReference getReferent(IInstance instance, String referentName) throws SnapshotException
    {
        Field field = instance.getField(referentName);
        if (field != null)
        {
            return (ObjectReference) field.getValue();
        }
        else if (REFERENCE_PATTERN.matcher(instance.getClazz().getName()).matches()
                        || "java.lang.ref.Finalizer".equals(instance.getClazz().getName())) //$NON-NLS-1$

        {
            // see [266231] guess the referent field for references objects from
            // java
            final ISnapshot snapshot = instance.getSnapshot();

            int[] outbounds = snapshot.getOutboundReferentIds(instance.getObjectId());
            for (int outboundId : outbounds)
            {
                IClass outboundType = snapshot.getClassOf(outboundId);

                // pick outbound reference only if it is not the <class>
                // reference, a reference type (next) or reference queue (queue)

                if (instance.getClazz().getObjectId() != outboundId // <class>
                                && !isReferenceType(outboundType) // 'next'
                                && !isReferenceQueueType(outboundType)) // 'queue'
                {
                    long address = snapshot.mapIdToAddress(outboundId);
                    return new ObjectReference(snapshot, address);
                }
            }

            return null;
        }
        else
        {
            return null;
        }
    }

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("java\\.lang\\.ref\\..*Reference"); //$NON-NLS-1$
    private static final Pattern QUEUE_PATTERN = Pattern.compile("java\\.lang\\.ref\\..*ReferenceQueue.*"); //$NON-NLS-1$

    private static boolean isReferenceType(IClass clazz) throws SnapshotException
    {
        return REFERENCE_PATTERN.matcher(clazz.getName()).matches()
                        || "java.lang.ref.Finalizer".equals(clazz.getName()) //$NON-NLS-1$
                        || clazz.doesExtend("java.lang.ref.Reference"); //$NON-NLS-1$
    }

    private static boolean isReferenceQueueType(IClass clazz) throws SnapshotException
    {
        return QUEUE_PATTERN.matcher(clazz.getName()).matches() //
                        || clazz.doesExtend("java.lang.ref.ReferenceQueue"); //$NON-NLS-1$
    }

}
