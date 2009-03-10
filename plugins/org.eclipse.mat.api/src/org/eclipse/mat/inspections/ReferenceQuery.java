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
package org.eclipse.mat.inspections;

import java.util.Collection;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

/**
 * Extract information about objects extending java.lang.ref.Reference, e.g.
 * weak and soft references, and Finalizer.
 */
public class ReferenceQuery
{

    /**
     * Important: the <strong>className</strong> must point to
     * java.lang.ref.Reference or one of its subclasses. It is not possible to
     * check this, as some heap dumps lack class hierarchy information.
     */
    public static IResult execute(String adverb, String className, ISnapshot snapshot, IProgressListener listener)
                    throws SnapshotException
    {
        listener.subTask("Computing Referent Set (objects referenced by the Reference objects)...");

        ArrayInt instanceSet = new ArrayInt();
        SetInt referentSet = new SetInt();

        Collection<IClass> classes = snapshot.getClassesByName(Pattern.compile(className), true);
        if (classes == null)
            throw new SnapshotException(MessageUtil.format("No classes matching pattern {0}", className));

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

        return execute(adverb, instanceSet, referentSet, snapshot, listener);
    }

    public static CompositeResult execute(String adverb, ArrayInt instanceSet, SetInt referentSet, ISnapshot snapshot,
                    IProgressListener listener) throws SnapshotException
    {
        CompositeResult result = new CompositeResult();

        Histogram histogram = snapshot.getHistogram(referentSet.toArray(), listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        String l = MessageUtil.format("Histogram of {0} Referenced", adverb);
        histogram.setLabel(l);
        result.addResult(l, histogram);

        listener.subTask("Computing retained set of reference set (assuming only the "
                        + "referents are no longer referenced by the Reference objects)...");
        int[] retainedSet = snapshot.getRetainedSet(instanceSet.toArray(), new String[] { "referent" }, listener); //$NON-NLS-1$
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram = snapshot.getHistogram(retainedSet, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        l = MessageUtil.format("Only {0} Retained", adverb);
        histogram.setLabel(l);
        result.addResult(l, histogram);

        return result;
    }

    public static ObjectReference getReferent(IInstance instance) throws SnapshotException
    {
        Field field = instance.getField("referent"); //$NON-NLS-1$
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
