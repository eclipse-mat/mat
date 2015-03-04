package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

public class ConcurrentSkipListCollectionExtractor extends HashMapCollectionExtractor {
	public ConcurrentSkipListCollectionExtractor(String arrayField,
            String keyField, String valueField) {
		super(null, arrayField, keyField, valueField);
	}

	@Override
	public boolean hasCapacity() {
		return false;
	}

	@Override
	public Integer getCapacity(IObject coll) throws SnapshotException {
		return null;
	}

	@Override
    public boolean hasFillRatio() {
		return false;
	}

	@Override
	public Double getFillRatio(IObject coll) throws SnapshotException {
		return null;
	}

    public Double getCollisionRatio(IObject coll) throws SnapshotException {
        return 0.0;
    }
}
