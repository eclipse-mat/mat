package org.eclipse.mat.inspections.collectionextract;

import java.util.Iterator;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * @since 1.5
 */
public class ExtractedCollection extends ExtractedCollectionBase<IObject, CollectionExtractor> {
    public ExtractedCollection(IObject coll, CollectionExtractor extractor) {
        super(coll, extractor);
    }

    public Iterator<IObject> iterator() {
        // use ArrayList and LinkedList processing code from ExtractListValuesQuery
        throw new IllegalStateException("not implemented yet");
    }

    public Integer getNumberOfNotNullElements() throws SnapshotException {
        return getExtractor().getNumberOfNotNullElements(getCollection());
    }
}
