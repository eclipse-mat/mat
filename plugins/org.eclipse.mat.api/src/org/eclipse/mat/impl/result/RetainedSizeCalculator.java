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
package org.eclipse.mat.impl.result;

import java.text.MessageFormat;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.collect.HashMapObjectLong;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;


/* package */abstract class RetainedSizeCalculator
{

    public abstract Object getValue(Object row);

    public abstract void calculate(ISnapshot snapshot, ContextProvider provider, Object row, boolean approximation,
                    IProgressListener listener) throws SnapshotException;

    /* package */static class ArbitrarySet extends RetainedSizeCalculator
    {
        HashMapObjectLong<Object> values;

        /* package */ArbitrarySet()
        {
            this.values = new HashMapObjectLong<Object>();
        }

        public Object getValue(Object row)
        {
            try
            {
                return values.get(row);
            }
            catch (NoSuchElementException e)
            {
                // $JL-EXC$
                return null;
            }
        }

        public void calculate(ISnapshot snapshot, ContextProvider provider, Object row, boolean approximation,
                        IProgressListener listener) throws SnapshotException
        {
            IContextObject contextObject = provider.getContext(row);

            // nothing to calculate
            if (contextObject == null)
                return;

            try
            {
                long v = values.get(row);
                if (v > 0 || approximation)
                    return;
            }
            catch (NoSuchElementException e)
            {
                // $JL-EXC$
            }

            if (contextObject instanceof IContextObjectSet)
            {
                int retainedSet[] = ((IContextObjectSet) contextObject).getObjectIds();

                if (retainedSet != null)
                {
                    if (retainedSet.length == 1 && retainedSet[0] == -1)
                    {
                        String msg = "Context provider ''{0}'' returned an illegal context object set for ''{1}}'' with content ''{{2}}'''. Return null instead.";
                        Logger.getLogger(getClass().getName()).log(
                                        Level.SEVERE,
                                        MessageFormat.format(msg, provider.getClass().getName(), row.getClass()
                                                        .getName(), row.toString()));
                        return;
                    }
                    else
                    {
                        long retainedSize = 0;

                        if (retainedSet.length == 1)
                        {
                            retainedSize = snapshot.getRetainedHeapSize(retainedSet[0]);
                        }
                        else
                        {
                            if (approximation)
                            {
                                retainedSize = snapshot.getMinRetainedSize(retainedSet, listener);
                                retainedSize = -retainedSize;
                            }
                            else
                            {
                                retainedSet = snapshot.getRetainedSet(retainedSet, listener);
                                retainedSize = snapshot.getHeapSize(retainedSet);
                            }
                        }

                        values.put(row, retainedSize);
                    }
                }

            }
            else
            {
                int objectId = contextObject.getObjectId();
                if (objectId < 0)
                {
                    String msg = "Context provider ''{0}'' returned an context object with an illegeal object id for ''{1}}''. Return null instead.";
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                    MessageFormat.format(msg, provider.getClass().getName(), row.toString()));
                }
                else
                {
                    long retainedSize = snapshot.getRetainedHeapSize(contextObject.getObjectId());
                    values.put(row, retainedSize);
                }
            }

        }
    }

    /* package */static class AllClasses extends ArbitrarySet
    {

        /* package */AllClasses()
        {}

        @Override
        public Object getValue(Object row)
        {
            if (row instanceof ClassHistogramRecord)
            {
                long size = ((ClassHistogramRecord) row).getRetainedHeapSize();
                return size != 0 ? size : null;
            }
            else if (row instanceof ClassLoaderHistogramRecord)
            {
                long size = ((ClassLoaderHistogramRecord) row).getRetainedHeapSize();
                return size != 0 ? size : null;
            }
            else
            {
                return super.getValue(row);
            }
        }

        @Override
        public void calculate(ISnapshot snapshot, ContextProvider provider, Object row, boolean approximation,
                        IProgressListener listener) throws SnapshotException
        {
            if (row instanceof ClassHistogramRecord)
            {
                ((ClassHistogramRecord) row).calculateRetainedSize(snapshot, true, approximation, listener);
            }
            else if (row instanceof ClassLoaderHistogramRecord)
            {
                ((ClassLoaderHistogramRecord) row).calculateRetainedSize(snapshot, true, approximation, listener);
            }
            else
            {
                super.calculate(snapshot, provider, row, approximation, listener);
            }
        }
    }
}
