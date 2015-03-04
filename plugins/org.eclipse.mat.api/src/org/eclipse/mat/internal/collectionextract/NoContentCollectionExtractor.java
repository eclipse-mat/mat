package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/*
 * This will be used for unknown subclasses of AbstractList etc
 */
public class NoContentCollectionExtractor implements CollectionExtractor {
    public boolean hasSize() {
        return false;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public boolean hasCapacity() {
        return false;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
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
        return null;
    }

	public boolean hasFillRatio() {
		return false;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return null;
	}
}
