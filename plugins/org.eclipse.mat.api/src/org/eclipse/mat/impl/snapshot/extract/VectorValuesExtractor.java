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

/**
 * Extracts the object ids of the elements of java.util.Vector.
 */
public class VectorValuesExtractor extends ArrayListValuesExtractor
{
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.mat.snapshot.extract.IExtractor#appliesTo(org.eclipse.mat.snapshot.model.IObject)
     */
    @Override
    public boolean appliesTo(IObject object)
    {
        return "java.util.Vector".equals(object.getClazz().getName());
    }

    @Override
    protected int extractSize(IInstance list) throws SnapshotException
    {
        return (Integer) list.resolveValue("elementCount");
    }
}
