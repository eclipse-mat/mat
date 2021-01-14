/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
 * An abstract class representing a map extracted from the heap. It provides
 * convenience methods for querying certain properties of the collection (e.g.
 * size) and for extracting the elements of the collection
 * 
 * @since 1.5
 */
public class ExtractedMap extends AbstractExtractedCollection<Map.Entry<IObject, IObject>, IMapExtractor>
{

    private static final long serialVersionUID = 1L;

    public ExtractedMap(IObject coll, IMapExtractor extractor)
    {
        super(coll, extractor);
    }

    /**
     * Check if collision ratio can be calculated for the map
     * @return
     */
    public boolean hasCollisionRatio()
    {
        return getExtractor().hasCollisionRatio();
    }

    /**
     * Get the ration of collisions inside the map
     * @return
     * @throws SnapshotException
     */
    public Double getCollisionRatio() throws SnapshotException
    {
        return getExtractor().getCollisionRatio(getCollection());
    }

    /**
     * Gets an object from the Map, searching by the key. The keys are matched
     * by IDENTITY, so cannot be used to compare for example Strings
     * 
     * @param key
     * @return
     * @throws SnapshotException
     */
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
