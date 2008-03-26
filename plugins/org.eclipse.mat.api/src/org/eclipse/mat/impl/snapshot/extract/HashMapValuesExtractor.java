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
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.util.IProgressListener;


/**
 * Extracts the object ids of the values of java.util.HashMap.
 */
public class HashMapValuesExtractor implements IExtractor
{
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.mat.snapshot.extract.IExtractor#appliesTo(org.eclipse.mat.snapshot.model.IObject)
     */
    public boolean appliesTo(IObject object)
    {
        return "java.util.HashMap".equals(object.getClazz().getName());
    }

    /**
     * Extracts the object ids of the values of java.util.HashMap.
     * 
     * @see org.eclipse.mat.impl.snapshot.extract.IExtractor#extractFrom(org.eclipse.mat.snapshot.model.IObject,
     *      org.eclipse.mat.util.IProgressListener)
     */
    public int[] extractFrom(IObject object, IProgressListener progressListener) throws SnapshotException
    {
        if (!appliesTo(object))
            return new int[0];

        IInstance map = (IInstance) object;

        int size = extractSize(map);

        if (size == 0)
            return new int[0];

        String taskMsg = MessageFormat.format("collecting {0} element(s) of {1}", new Object[] { size,
                        object.getTechnicalName() });
        progressListener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObject table = (IObject) map.resolveValue("table");

        for (NamedReference ref : table.getOutboundReferences())
        {
            if (ref instanceof PseudoReference)
                continue;

            IObject entry = object.getSnapshot().getObject(object.getSnapshot().mapAddressToId(ref.getObjectAddress()));

            while (entry != null)
            {
                processEntry(object.getSnapshot(), result, entry);
                entry = (IObject) entry.resolveValue("next");
            }
        }

        return result.toArray();
    }

    protected int extractSize(IInstance map) throws SnapshotException
    {
        return (Integer) map.resolveValue("size");
    }

    protected void processEntry(ISnapshot snapshot, ArrayInt result, IObject entry) throws SnapshotException
    {
        IObject obj = (IObject) entry.resolveValue("value");
        if (obj != null)
            result.add(obj.getObjectId());
    }
}
