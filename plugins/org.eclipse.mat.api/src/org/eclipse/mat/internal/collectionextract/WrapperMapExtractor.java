/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others
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
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class WrapperMapExtractor extends WrapperCollectionExtractor implements IMapExtractor
{
    public WrapperMapExtractor(String field)
    {
        super(field);
    }

    public WrapperMapExtractor(String field, ICollectionExtractor extractor)
    {
        super(field, extractor);
    }

    public boolean hasCollisionRatio()
    {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        return extractMap(coll).getCollisionRatio();
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(final IObject coll)
    {
        // Wrap the returned object so the wrapper collection is the entry object
        ExtractedMap em = extractMap(coll);
        final Iterator<Entry<IObject, IObject>> it = em.iterator();
        return new Iterator<Entry<IObject, IObject>>() {

            public boolean hasNext()
            {
                return it.hasNext();
            }

            public Entry<IObject, IObject> next()
            {
                Entry<IObject, IObject> e = it.next();
                return new EntryObject(coll, e.getKey(), e.getValue());
            }

        };
    }
}
