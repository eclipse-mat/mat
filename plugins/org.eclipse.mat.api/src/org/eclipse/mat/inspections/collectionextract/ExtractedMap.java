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
package org.eclipse.mat.inspections.collectionextract;

import java.util.Iterator;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * @since 1.5
 */
public class ExtractedMap extends AbstractExtractedCollection<Map.Entry<IObject, IObject>, IMapExtractor>
{
    public ExtractedMap(IObject coll, IMapExtractor extractor)
    {
        super(coll, extractor);
    }

    public boolean hasCollisionRatio()
    {
        return getExtractor().hasCollisionRatio();
    }

    public Double getCollisionRatio() throws SnapshotException
    {
        return getExtractor().getCollisionRatio(getCollection());
    }

    /**
     * This matched by IDENTITY, so cannot be used to compare strings
     **/
    public IObject getByKeyIdentity(IObject key) throws SnapshotException
    {
        Iterator<Map.Entry<IObject, IObject>> it = iterator();
        while (it.hasNext())
        {
            Map.Entry<IObject, IObject> me = it.next();
            if (me.getKey() == key)
                return me.getValue();
        }
        return null;
    }

    public Iterator<Map.Entry<IObject, IObject>> iterator()
    {
        try
        {
            return getExtractor().extractMapEntries(getCollection());
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }
}
