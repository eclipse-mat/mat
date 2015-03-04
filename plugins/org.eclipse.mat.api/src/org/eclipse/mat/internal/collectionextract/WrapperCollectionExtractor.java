package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollectionBase;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.MapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class WrapperCollectionExtractor implements CollectionExtractor {
    private final String field;
    private final CollectionExtractor extractor;

    public WrapperCollectionExtractor(String field) {
        this(field, null);
    }

    public WrapperCollectionExtractor(String field, CollectionExtractor extractor) {
        if (field == null)
            throw new IllegalArgumentException();
        this.field = field;
        this.extractor = extractor;
    }

    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        return extractCollection(coll).size();
    }

    public boolean hasCapacity() {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException {
        return extractCollection(coll).getCapacity();
    }

	public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return extractCollection(coll).getFillRatio();
	}

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        return extractCollection(coll).extractEntryIds();
    }

    public boolean hasExtractableArray() {
        return true;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        return extractCollection(coll).extractEntries();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return extractList(coll).getNumberOfNotNullElements();
    }

    protected ExtractedCollection extractList(IObject coll) {
        ExtractedCollectionBase ec = extractCollection(coll);
        if (ec instanceof ExtractedCollection)
            return (ExtractedCollection) ec;
        else
            throw new UnsupportedOperationException("not a list-ish collection: " + coll.getDisplayName() + "; " + ec.getDisplayName());
    }

    protected ExtractedMap extractMap(IObject coll) {
        ExtractedCollectionBase ec = extractCollection(coll);
        if (ec instanceof ExtractedMap)
            return (ExtractedMap) ec;
        else
            throw new UnsupportedOperationException("not a map: " + coll.getDisplayName() + "; " + ec.getDisplayName());
    }

    protected ExtractedCollectionBase extractCollection(IObject coll) {
        try {
            IObject value = (IObject)coll.resolveValue(field);
            if (value == null) {
        		// for PHD files, see if we can find exactly one outbound reference to a collection
            	return guessWrappedFromOutbounds(coll);
            } if (extractor == null) {
                return CollectionExtractionUtils.extractCollection(value);
            } else if (extractor instanceof MapExtractor) {
                return new ExtractedMap(value, (MapExtractor) extractor);
            } else {
                return new ExtractedCollection(value, extractor);
            }
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

	private ExtractedCollectionBase guessWrappedFromOutbounds(IObject coll) throws SnapshotException {
		ISnapshot snapshot = coll.getSnapshot();
		ExtractedCollectionBase extracted = null;

		int[] outbounds = snapshot.getOutboundReferentIds(coll.getObjectId());
		for (int outId: outbounds) {
			ExtractedCollectionBase ex = CollectionExtractionUtils.extractCollection(snapshot.getObject(outId));
			if (ex != null) {
				if (extracted == null) {
					extracted = ex;
				} else {
		            throw new IllegalArgumentException("Could not resolve field " + field
		            		+ " of " + coll.getTechnicalName() + " (found multiple outbound references to collections)");
				}
			}
		}

		if (extracted != null) {
			return extracted;
		} else {
		    throw new IllegalArgumentException("Could not resolve field " + field
		    		+ " of " + coll.getTechnicalName() + " (found no outbound references to collections)");
		}
	}
}
