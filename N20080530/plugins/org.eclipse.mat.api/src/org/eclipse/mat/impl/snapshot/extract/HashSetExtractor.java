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

import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

/**
 * Extracts the object ids of the elements of java.util.HashSet.
 */
public class HashSetExtractor implements IExtractor
{
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.mat.snapshot.extract.IExtractor#appliesTo(org.eclipse.mat.snapshot.model.IObject)
     */
    public boolean appliesTo(IObject object)
    {
        return "java.util.HashSet".equals(object.getClazz().getName());
    }

    /**
     * Extracts the object ids of the values of java.util.HashSet.
     * 
     * @see org.eclipse.mat.impl.snapshot.extract.IExtractor#extractFrom(org.eclipse.mat.snapshot.model.IObject,
     *      org.eclipse.mat.util.IProgressListener)
     */
    public int[] extractFrom(IObject object, IProgressListener progressListener) throws SnapshotException
    {
        if (!appliesTo(object))
            return new int[0];

        IInstance set = (IInstance) object;

        IObject map = (IObject) set.resolveValue("map");

        return new HashMapKeysExtractor().extractFrom(map, progressListener);
    }
}
