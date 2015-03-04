package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class SingletonCollectionExtractor implements CollectionExtractor {
    private final String field;

    public SingletonCollectionExtractor(String field) {
        this.field = field;
    }

    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        return 1;
    }

    public boolean hasCapacity() {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException {
        return 1;
    }

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        int id = ((IObject)coll.resolveValue(field)).getObjectId();
        return new int[] {id};
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return (coll.resolveValue(field) != null) ? 1 : 0;
    }

	public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return getNumberOfNotNullElements(coll).doubleValue();
	}
}
