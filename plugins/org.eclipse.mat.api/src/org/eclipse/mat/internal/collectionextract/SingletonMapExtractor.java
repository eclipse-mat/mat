package org.eclipse.mat.internal.collectionextract;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.MapExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class SingletonMapExtractor implements MapExtractor {
    private final String keyField;
    private final String valueField;

    public SingletonMapExtractor(String keyField, String valueField) {
        this.keyField = keyField;
        this.valueField = valueField;
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
        return new int[] {coll.getObjectId()};
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return (coll.resolveValue(valueField) != null) ? 1 : 0;
    }

    public boolean hasCollisionRatio() {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException {
        return 0d;
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException {
        return Collections.singletonMap((IObject)coll.resolveValue(keyField),
                (IObject)coll.resolveValue(valueField)).entrySet().iterator();
    }

	public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return getNumberOfNotNullElements(coll).doubleValue();
	}
}
