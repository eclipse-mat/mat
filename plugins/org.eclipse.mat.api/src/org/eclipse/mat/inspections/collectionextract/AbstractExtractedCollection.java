/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others
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

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.MessageUtil;

/**
 * An abstract class representing a collection extracted from the heap. It
 * provides convenience methods for querying certain properties of the
 * collection (e.g. size) and for extracting the elements of the collection
 * 
 * @since 1.5
 */
public abstract class AbstractExtractedCollection<E, X extends ICollectionExtractor> implements Iterable<E>, IObject
{
    private static final long serialVersionUID = 2237977092308177450L;

    private final X extractor;
    private final IObject collection;

    /**
     * @param collection
     *            an IObject representing a collection
     * @param extractor
     *            used to extract the contents
     */
    public AbstractExtractedCollection(IObject collection, X extractor)
    {
        if (collection == null)
            throw new IllegalArgumentException();
        if (extractor == null)
            throw new IllegalArgumentException(Messages.AbstractExtractedCollection_UnhandledCollection);
        this.collection = collection;
        this.extractor = extractor;
    }

    /**
     * Returns the ICollectionExtractor used to extract the data about the
     * collection
     * 
     * @return
     */
    protected X getExtractor()
    {
        return extractor;
    }

    /**
     * Get the IObject representing the collection
     * 
     * @return the {@link IObject}
     */
    protected IObject getCollection()
    {
        return collection;
    }

    /* capability detection */

    /**
     * Check if the collection has a size property
     * 
     * @return true if the collection has a valid {@link #size()}
     * @see #size()
     */
    public boolean hasSize()
    {
        return extractor.hasSize();
    }

    /**
     * Check if the the contents of the collection can be extracted
     * 
     * @return true if the collection has an extractable ids
     * @see #extractEntryIds()
     */
    public boolean hasExtractableContents()
    {
        return extractor.hasExtractableContents();
    }

    /**
     * Check if the contents of the collection can be extracted as an array
     * (e.g. for ArrayList)
     * 
     * @return true if the collection has an extractable contents as an {@link IObjectArray}
     * @see #extractEntries() 
     */
    public boolean hasExtractableArray()
    {
        return extractor.hasExtractableArray();
    }

    /**
     * Check if the collection has a capacity property, i.e. if it preallocates
     * space for its content
     * 
     * @return true if the collection has a valid {@link #getCapacity()}
     * @see #getCapacity()
     */
    public boolean hasCapacity()
    {
        return extractor.hasCapacity();
    }

    /**
     * Check if the collision ration can be calculated for the collection
     * 
     * @return true if the collection has a valid fill ratio
     * @see #size()
     */
    public boolean hasFillRatio()
    {
        return extractor.hasFillRatio();
    }

    /* accessors */

    /**
     * Get the capacity of the collection
     * 
     * @return the capacity of the collection as an Integer
     * @see #hasCapacity()
     */
    public Integer getCapacity()
    {
        try
        {
            return extractor.getCapacity(collection);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the size of the collection.
     * This is the number of entries it holds.
     * @return the size of the collection as an {@link Integer}
     * @see #hasSize()
     */
    public Integer size()
    {
        try
        {
            return extractor.getSize(collection);
        }
        catch (SnapshotException e)

        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the ratio to which the collection is filled (for collections which
     * preallocates a certain capacity)
     * 
     * @return a {@link Double} between 0.0 and 1.0 inclusive
     * @see #hasFillRatio()
     */
    public Double getFillRatio()
    {
        try
        {
            return extractor.getFillRatio(collection);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if the collection is empty
     * 
     * @return true if the collection is empty, either {@link #size()} is zero
     * or there are no entry ids.
     */
    public Boolean isEmpty()
    {
        Integer size = size();
        return (size != null) ? (size > 0) : iterator().hasNext();
    }

    /**
     * Get the object Ids (int) of the contents of the collection
     * 
     * @return an int[] array of object identifiers
     * @see #hasExtractableContents()
     */
    public int[] extractEntryIds()
    {
        try
        {
            return extractor.extractEntryIds(collection);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the array with contents of the collection
     * 
     * @return an {@link IObjectArray} holding the objects in the collection
     * @see #hasExtractableContents()
     */
    public IObjectArray extractEntries()
    {
        try
        {
            return extractor.extractEntries(collection);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    /* general utils */

    @Override
    public long getRetainedHeapSize()
    {
        return collection.getRetainedHeapSize();
    }

    @Override
    public long getUsedHeapSize()
    {
        return collection.getUsedHeapSize();
    }

    // IObject methods
    @Override
    public int getObjectId()
    {
        return collection.getObjectId();
    }

    @Override
    public long getObjectAddress()
    {
        return collection.getObjectAddress();
    }

    @Override
    public IClass getClazz()
    {
        return collection.getClazz();
    }

    @Override
    public String getTechnicalName()
    {
        return collection.getTechnicalName();
    }

    @Override
    public String getClassSpecificName()
    {
        return collection.getClassSpecificName();
    }

    @Override
    public String getDisplayName()
    {
        return collection.getDisplayName();
    }

    @Override
    public List<NamedReference> getOutboundReferences()
    {
        return collection.getOutboundReferences();
    }

    @Override
    public Object resolveValue(String field) throws SnapshotException
    {
        return collection.resolveValue(field);
    }

    @Override
    public GCRootInfo[] getGCRootInfo() throws SnapshotException
    {
        return collection.getGCRootInfo();
    }

    @Override
    public ISnapshot getSnapshot()
    {
        return collection.getSnapshot();
    }

    // for debugging
    @Override
    public String toString() {
        return MessageUtil.format(Messages.AbstractExtractedCollection_ToString, getClass().getName() ,getCollection().getDisplayName());
    }
}
