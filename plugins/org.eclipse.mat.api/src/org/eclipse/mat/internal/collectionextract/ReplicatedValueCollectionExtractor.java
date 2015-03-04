package org.eclipse.mat.internal.collectionextract;

import java.util.Arrays;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ReplicatedValueCollectionExtractor implements CollectionExtractor {
    private final String countField;
    private final String valueField;

    public ReplicatedValueCollectionExtractor(String countField, String valueField) {
        this.countField = countField;
        this.valueField = valueField;
    }

    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        return getCount(coll);
    }

    public boolean hasCapacity() {
        return true;
    }

	public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return 1.0;
	}

    public Integer getCapacity(IObject coll) throws SnapshotException {
        return getCount(coll);
    }

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        int id = ((IObject)coll.resolveValue(valueField)).getObjectId();
        int[] arr = new int[getCount(coll)];
        Arrays.fill(arr, id);
        return arr;
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return (coll.resolveValue(valueField) != null) ? getCount(coll) : 0;
    }

    public Integer getCount(IObject coll) throws SnapshotException {
        return ExtractionUtils.toInteger(coll.resolveValue(countField));
    }
}
