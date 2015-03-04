package org.eclipse.mat.inspections.collectionextract;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;


/**
 * @since 1.5
 */
public abstract class ExtractedCollectionBase<E, X extends CollectionExtractor> implements Iterable<E>, IObject {
    private final X extractor;
    private final IObject coll;

    public ExtractedCollectionBase(IObject coll, X extractor) {
        if (coll == null)
            throw new IllegalArgumentException();
        if (extractor == null)
            throw new IllegalArgumentException("unhandled collection");
        this.coll = coll;
        this.extractor = extractor;
    }

    protected X getExtractor() {
        return extractor;
    }

    protected IObject getCollection() {
        return coll;
    }


    /* capability detection */
    public boolean hasSize() {
        return extractor.hasSize();
    }

    public boolean hasExtractableContents() {
        return extractor.hasExtractableContents();
    }

    public boolean hasExtractableArray() {
        return extractor.hasExtractableArray();
    }

    public boolean hasCapacity() {
        return extractor.hasCapacity();
    }

    public boolean hasFillRatio() {
        return extractor.hasFillRatio();
    }

    /* accessors */
    public Integer getCapacity() {
        try {
            return extractor.getCapacity(coll);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer size() {
        try {
            return extractor.getSize(coll);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    public Double getFillRatio() {
        try {
        	return extractor.getFillRatio(coll);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean isEmpty() {
        Integer size = size();
        return (size != null) ? (size > 0) : iterator().hasNext();
    }

    public int[] extractEntryIds() {
        try {
            return extractor.extractEntryIds(coll);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    public IObjectArray extractEntries() {
        try {
            return extractor.extractEntries(coll);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    /* general utils */

    public long getRetainedHeapSize() {
        return coll.getRetainedHeapSize();
    }

    public long getUsedHeapSize() {
        return coll.getUsedHeapSize();
    }


    // IObject methods
    public int getObjectId() {
        return coll.getObjectId();
    }

    public long getObjectAddress() {
        return coll.getObjectAddress();
    }

    public IClass getClazz() {
        return coll.getClazz();
    }

    public String getTechnicalName() {
        return coll.getTechnicalName();
    }

    public String getClassSpecificName() {
        return coll.getClassSpecificName();
    }

    public String getDisplayName() {
        return coll.getDisplayName();
    }

    public List<NamedReference> getOutboundReferences() {
        return coll.getOutboundReferences();
    }

    public Object resolveValue(String field) throws SnapshotException {
        return coll.resolveValue(field);
    }

    public GCRootInfo[] getGCRootInfo() throws SnapshotException {
        return coll.getGCRootInfo();
    }

    public ISnapshot getSnapshot() {
        return coll.getSnapshot();
    }
}
