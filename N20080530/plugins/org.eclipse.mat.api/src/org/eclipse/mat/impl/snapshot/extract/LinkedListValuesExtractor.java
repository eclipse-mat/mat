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
package org.eclipse.mat.impl.snapshot.extract;

import java.text.MessageFormat;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


/**
 * Extracts the object ids of the elements of java.util.LinkedList.
 */
public class LinkedListValuesExtractor implements IExtractor
{
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.mat.snapshot.extract.IExtractor#appliesTo(org.eclipse.mat.snapshot.model.IObject)
     */
    public boolean appliesTo(IObject object)
    {
        return "java.util.LinkedList".equals(object.getClazz().getName());
    }

    /**
     * Extracts the object ids of the elements of java.util.LinkedList.
     * 
     * @see org.eclipse.mat.impl.snapshot.extract.IExtractor#extractFrom(org.eclipse.mat.snapshot.model.IObject,
     *      org.eclipse.mat.util.IProgressListener)
     */
    public int[] extractFrom(IObject object, IProgressListener progressListener) throws SnapshotException
    {
        if (!appliesTo(object))
            return new int[0];

        IInstance linkedList = (IInstance) object;

        int size = (Integer) linkedList.resolveValue("size");

        if (size == 0)
            return new int[0];

        String taskMsg = MessageFormat.format("collecting {0} element(s) of {1}", new Object[] { size,
                        object.getTechnicalName() });
        progressListener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObject header = (IObject) linkedList.resolveValue("header");
        IObject current = (IObject) header.resolveValue("next");

        while (header != current)
        {
            IObject ref = (IObject) current.resolveValue("element");
            if (ref != null)
                result.add(ref.getObjectId());

            current = (IObject)current.resolveValue("next");
            progressListener.worked(1);
            if (progressListener.isCanceled())
                return null;
        }

        return result.toArray();
    }
}
