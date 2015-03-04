package org.eclipse.mat.inspections.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * @since 1.5
 */
public interface CollectionExtractor {
    boolean hasSize();
    Integer getSize(IObject coll) throws SnapshotException;
    boolean hasCapacity();
    Integer getCapacity(IObject coll) throws SnapshotException;
    boolean hasFillRatio();
    Double getFillRatio(IObject coll) throws SnapshotException;

    boolean hasExtractableContents();
    int[] extractEntryIds(IObject coll) throws SnapshotException;
    boolean hasExtractableArray();
    IObjectArray extractEntries(IObject coll) throws SnapshotException;

    // requires hasExtractableContents || hasExtractableArray
    Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException;
}
