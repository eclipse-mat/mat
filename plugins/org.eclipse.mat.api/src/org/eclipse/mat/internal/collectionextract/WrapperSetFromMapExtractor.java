/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * Creates an extractor to view a map as a set.
 * Just treating it as a map does not work as extractEntryIds needs to return the keys,
 * not the nodes of type Map.Entry.
 *
 */
public class WrapperSetFromMapExtractor extends WrapperCollectionExtractor implements IMapExtractor
{
    WrapperMapExtractor mapext;
    public WrapperSetFromMapExtractor(String setField, String mapField)
    {
        super(setField);
        mapext = new WrapperMapExtractor(mapField);
    }

    public boolean hasCollisionRatio()
    {
        return mapext.hasCollisionRatio();
    }

    public Double getCollisionRatio(IObject collection) throws SnapshotException
    {
        return mapext.getCollisionRatio(collection);
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject collection) throws SnapshotException
    {
        return mapext.extractMapEntries(collection);
    }

}
