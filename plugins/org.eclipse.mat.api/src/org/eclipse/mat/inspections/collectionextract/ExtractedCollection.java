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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * An class representing a collection extracted from the heap. It provides
 * convenience methods for querying certain properties of the collection (e.g.
 * size) and for extracting the elements of the collection
 * 
 * @since 1.5
 */
public class ExtractedCollection extends AbstractExtractedCollection<IObject, ICollectionExtractor>
{

    private static final long serialVersionUID = 1L;

    public ExtractedCollection(IObject coll, ICollectionExtractor extractor)
    {
        super(coll, extractor);
    }

    /**
     * not implemented yet
     */
    public Iterator<IObject> iterator()
    {
        // use ArrayList and LinkedList processing code from
        // ExtractListValuesQuery
        throw new IllegalStateException("not implemented yet");
    }

    /**
     * Get the number of non-null elements contained in the collection
     * 
     * @return
     * @throws SnapshotException
     */
    public Integer getNumberOfNotNullElements() throws SnapshotException
    {
        return getExtractor().getNumberOfNotNullElements(getCollection());
    }
}
