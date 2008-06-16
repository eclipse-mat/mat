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
package org.eclipse.mat.inspections.query;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.regex.Pattern;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.HistogramResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;


public class ReferenceQuery
{

    public static IResult execute(String label, String className, ISnapshot snapshot, IProgressListener listener)
                    throws Exception
    {
        listener.subTask("Computing Referent Set (objects referenced by the Reference objects)...");

        ArrayInt instanceSet = new ArrayInt();
        ArrayInt referentSet = new ArrayInt();

        Collection<IClass> classes = snapshot.getClassesByName(Pattern.compile(className), true);
        for (IClass clazz : classes)
        {
            int[] objs = clazz.getObjectIds();
            instanceSet.addAll(objs);

            for (int ii = 0; ii < objs.length; ii++)
            {
                IInstance obj = (IInstance) snapshot.getObject(objs[ii]);

                ObjectReference ref = (ObjectReference) obj.getField("referent").getValue();
                if (ref != null)
                    referentSet.add(ref.getObjectId());
            }

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        CompositeResult result = new CompositeResult();

        Histogram histogram = snapshot.getHistogram(referentSet.toArray(), listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        
        histogram.setLabel(MessageFormat.format("{0}: Referents", label));
        result.addResult(new HistogramResult(histogram));

        listener.subTask("Computing retained set of reference set (assuming only the "
                        + "referents are no longer referenced by the Reference objects)...");
        int[] retainedSet = snapshot.getRetainedSet(instanceSet.toArray(), new String[] { "referent" }, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram = snapshot.getHistogram(retainedSet, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram.setLabel(MessageFormat.format("{0}: Retained throu referent Field", label));
        result.addResult(new HistogramResult(histogram));

        return result;
    }

}
