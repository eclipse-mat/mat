package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.MapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class WrapperMapExtractor extends WrapperCollectionExtractor implements MapExtractor {
    public WrapperMapExtractor(String field) {
        super(field);
    }

    public boolean hasCollisionRatio() {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException {
        return extractMap(coll).getCollisionRatio();
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) {
        return extractMap(coll).iterator();
    }
}
