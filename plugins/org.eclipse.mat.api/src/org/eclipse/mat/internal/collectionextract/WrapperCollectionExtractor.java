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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class WrapperCollectionExtractor implements ICollectionExtractor
{
    private final String field;
    private final ICollectionExtractor extractor;

    public WrapperCollectionExtractor(String field)
    {
        this(field, null);
    }

    public WrapperCollectionExtractor(String field, ICollectionExtractor extractor)
    {
        if (field == null)
            throw new IllegalArgumentException();
        this.field = field;
        this.extractor = extractor;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        AbstractExtractedCollection<?, ?> extractCollection = extractCollection(coll);
        return extractCollection != null ? extractCollection.size() : null; //TODO: report exception?
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        AbstractExtractedCollection<?, ?> extractCollection = extractCollection(coll);
        return extractCollection != null ? extractCollection(coll).getCapacity() : null; //TODO: report exception?
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        AbstractExtractedCollection<?, ?> extractCollection = extractCollection(coll);
        return extractCollection != null ? extractCollection.getFillRatio() : null; //TODO: report exception?
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return extractCollection(coll).extractEntryIds();
    }

    public boolean hasExtractableArray()
    {
        return true;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        return extractCollection(coll).extractEntries();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return extractList(coll).getNumberOfNotNullElements();
    }

    protected ExtractedCollection extractList(IObject coll)
    {
        AbstractExtractedCollection ec = extractCollection(coll);
        if (ec instanceof ExtractedCollection)
            return (ExtractedCollection) ec;
        else
            throw new UnsupportedOperationException("not a list-ish collection: " + coll.getDisplayName() + "; "
                            + ec.getDisplayName());
    }

    protected ExtractedMap extractMap(IObject coll)
    {
        AbstractExtractedCollection ec = extractCollection(coll);
        if (ec instanceof ExtractedMap)
            return (ExtractedMap) ec;
        else
            throw new UnsupportedOperationException("not a map: " + coll.getDisplayName() + "; " + ec.getDisplayName());
    }

    protected AbstractExtractedCollection extractCollection(IObject coll)
    {
        try
        {
            IObject value = (IObject) coll.resolveValue(field);
            if (value == null)
            {
                // for PHD files, see if we can find exactly one outbound
                // reference to a collection
                return guessWrappedFromOutbounds(coll);
            }
            if (extractor == null)
            {
                return CollectionExtractionUtils.extractCollection(value);
            }
            else if (extractor instanceof IMapExtractor)
            {
                return new ExtractedMap(value, (IMapExtractor) extractor);
            }
            else
            {
                return new ExtractedCollection(value, extractor);
            }
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    private AbstractExtractedCollection guessWrappedFromOutbounds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        AbstractExtractedCollection extracted = null;

        int[] outbounds = snapshot.getOutboundReferentIds(coll.getObjectId());
        for (int outId : outbounds)
        {
            AbstractExtractedCollection ex = CollectionExtractionUtils.extractCollection(snapshot.getObject(outId));
            if (ex != null)
            {
                if (extracted == null)
                {
                    extracted = ex;
                }
                else
                {
                    throw new IllegalArgumentException("Could not resolve field " + field + " of "
                                    + coll.getTechnicalName() + " (found multiple outbound references to collections)");
                }
            }
        }

        if (extracted != null)
        {
            return extracted;
        }
        else
        {
            throw new IllegalArgumentException("Could not resolve field " + field + " of " + coll.getTechnicalName()
                            + " (found no outbound references to collections)");
        }
    }
}
