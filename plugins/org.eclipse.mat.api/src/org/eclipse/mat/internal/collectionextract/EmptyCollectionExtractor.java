package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * Known-empty collections
 */
public class EmptyCollectionExtractor implements CollectionExtractor {
    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        return 0;
    }

    public boolean hasCapacity() {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException {
        return 0;
    }

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        return new int[0];
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException {
        return 0;
    }

	public boolean hasFillRatio() {
		return false;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return null;
	}
}
