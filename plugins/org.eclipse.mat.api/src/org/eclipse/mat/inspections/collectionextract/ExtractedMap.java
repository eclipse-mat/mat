package org.eclipse.mat.inspections.collectionextract;

import java.util.Iterator;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * @since 1.5
 */
public class ExtractedMap extends ExtractedCollectionBase<Map.Entry<IObject,IObject>, MapExtractor> {
    public ExtractedMap(IObject coll, MapExtractor extractor) {
        super(coll, extractor);
    }

    public boolean hasCollisionRatio() {
        return getExtractor().hasCollisionRatio();
    }

    public Double getCollisionRatio() throws SnapshotException {
        return getExtractor().getCollisionRatio(getCollection());
    }

    /**
     * This matched by IDENTITY, so cannot be used to compare strings
     **/
    public IObject getByKeyIdentity(IObject key) throws SnapshotException {
        Iterator<Map.Entry<IObject,IObject>> it = iterator();
        while (it.hasNext()) {
            Map.Entry<IObject,IObject> me = it.next();
            if (me.getKey() == key)
                return me.getValue();
        }
        return null;
    }

    public Iterator<Map.Entry<IObject,IObject>> iterator() {
        try {
            return getExtractor().extractMapEntries(getCollection());
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }
}
