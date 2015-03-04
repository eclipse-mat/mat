package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ArrayCollectionExtractor implements CollectionExtractor {
    public static final CollectionExtractor INSTANCE = new ArrayCollectionExtractor();
    private ArrayCollectionExtractor() {}

    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
        return ExtractionUtils.getNumberOfNotNullArrayElements((IObjectArray) coll);
    }

    public boolean hasCapacity() {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException {
        return ((IObjectArray)coll).getLength();
    }

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        return ExtractionUtils.referenceArrayToIds(coll.getSnapshot(),
                extractEntries(coll).getReferenceArray());
    }

    public boolean hasExtractableArray() {
        return true;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        return (IObjectArray) coll;
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return ExtractionUtils.getNumberOfNotNullArrayElements(extractEntries(coll));
    }

	public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return ((double) getNumberOfNotNullElements(coll)) / ((double)getCapacity(coll));
	}
}
