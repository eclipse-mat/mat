package org.eclipse.mat.inspections.collectionextract;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;

/**
 * @since 1.5
 */
public interface MapExtractor extends CollectionExtractor {
    boolean hasCollisionRatio();
    Double getCollisionRatio(IObject coll) throws SnapshotException;

    // if there is an actual entry object it would usually be a EntryObject
    Iterator<Map.Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException;

    public class EntryObject implements Map.Entry<IObject,IObject>, IObject {
        private final IObject self;
        private final IObject key;
        private final IObject value;

        public EntryObject(IObject self, IObject key, IObject value) {
            this.self = self;
            this.key = key;
            this.value = value;
        }

        public IObject getKey() {
            return key;
        }

        public IObject getValue() {
            return value;
        }

        public int getObjectId() {
            return self.getObjectId();
        }

        public long getObjectAddress() {
            return self.getObjectAddress();
        }

        public IClass getClazz() {
            return self.getClazz();
        }

        public long getUsedHeapSize() {
            return self.getUsedHeapSize();
        }

        public long getRetainedHeapSize() {
            return self.getRetainedHeapSize();
        }

        public String getTechnicalName() {
            return self.getTechnicalName();
        }

        public String getClassSpecificName() {
            return self.getClassSpecificName();
        }

        public String getDisplayName() {
            return self.getDisplayName();
        }

        public List<NamedReference> getOutboundReferences() {
            return self.getOutboundReferences();
        }

        public Object resolveValue(String field) throws SnapshotException {
            return self.resolveValue(field);
        }

        public GCRootInfo[] getGCRootInfo() throws SnapshotException {
            return self.getGCRootInfo();
        }

        public ISnapshot getSnapshot() {
            return self.getSnapshot();
        }

        public IObject setValue(IObject value) {
            throw new IllegalArgumentException();
        }
    }
}
