package org.eclipse.mat.internal.collectionextract;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.MapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class EmptyMapExtractor extends EmptyCollectionExtractor implements MapExtractor {
    public boolean hasCollisionRatio() {
        return true;
    }

    public Double getCollisionRatio(IObject collection) throws SnapshotException {
        return 0.0;
    }

    public Iterator<Entry<IObject, IObject>> extractMapEntries( IObject collection) {
        return Collections.EMPTY_LIST.iterator();
    }
}
