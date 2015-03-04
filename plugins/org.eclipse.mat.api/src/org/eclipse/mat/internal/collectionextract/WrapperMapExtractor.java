/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class WrapperMapExtractor extends WrapperCollectionExtractor implements IMapExtractor
{
    public WrapperMapExtractor(String field)
    {
        super(field);
    }

    public boolean hasCollisionRatio()
    {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return extractMap(coll).getCollisionRatio();
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll)
    {
        return extractMap(coll).iterator();
    }
}
