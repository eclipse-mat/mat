package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ConcurrentHashMapCollectionExtractor extends HashedMapCollectionExtractorBase {
    public ConcurrentHashMapCollectionExtractor(String arrayField,
            String keyField, String valueField) {
        //FIXME: should avoid passing null
        super(null, arrayField, keyField, valueField);
    }

    @Override
    public boolean hasSize() {
        return true;
    }

    /*
     * overwrite the getSize method to return correct result for a
     * concurrent map
     */
    @Override
    public Integer getSize(IObject collection) throws SnapshotException
    {
        IObjectArray segmentsArray = extractBackingArray(collection);
        if (segmentsArray == null)
            return 0;
        ISnapshot snapshot = collection.getSnapshot();
        CollectionExtractor segmentInfo = getSegmentExtractor();
        int size = 0;

        long[] refs = segmentsArray.getReferenceArray();
        for (long addr : refs)
        {
            if (addr != 0)
            {
                int segmentId = snapshot.mapAddressToId(addr);
                size += segmentInfo.getSize(snapshot.getObject(segmentId));
            }
        }

        return size;
    }


    @Override
    public Integer getCapacity(IObject collection) throws SnapshotException
    {
        IObjectArray segmentsArray = extractBackingArray(collection);
        if (segmentsArray == null)
            return 0;
        ISnapshot snapshot = collection.getSnapshot();
        CollectionExtractor extractor = getSegmentExtractor();
        int result = 0;

        long[] refs = segmentsArray.getReferenceArray();
        for (long addr : refs) {
            if (addr != 0) {
                int segmentId = snapshot.mapAddressToId(addr);
                IObject segment = snapshot.getObject(segmentId);
                Integer cap = extractor.getCapacity(segment);
                if (cap != null && extractor.hasSize()) {
                	cap = extractor.getSize(segment);
                }

                if (cap != null)
                	result += cap;
            }
        }
        return result;
    }


    public boolean hasExtractableContents() {
        return !arrayField.endsWith(".");
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException {
        ISnapshot snapshot = coll.getSnapshot();
        IObjectArray segmentsArray = extractBackingArray(coll);
        if (segmentsArray == null)
            return new int[0];
        ArrayInt result = new ArrayInt();
        CollectionExtractor segmentInfo = getSegmentExtractor();

        long[] refs = segmentsArray.getReferenceArray();
        for (long addr : refs) {
            if (addr != 0) {
                int[] segmentEntries = segmentInfo.extractEntryIds(snapshot.getObject(snapshot.mapAddressToId(addr)));
                result.addAll(segmentEntries);
            }
        }

        return result.toArray();
    }


    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        IObjectArray segmentsArray = extractBackingArray(collection);
        if (segmentsArray == null)
            return null;

        ISnapshot snapshot = collection.getSnapshot();
        CollectionExtractor segmentInfo = getSegmentExtractor();
        int result = 0;
        long[] refs = segmentsArray.getReferenceArray();
        for (long addr : refs) {
            if (addr != 0) {
                int segmentId = snapshot.mapAddressToId(addr);
                result += segmentInfo.getNumberOfNotNullElements(snapshot.getObject(segmentId));
            }
        }
        return result;
    }

    private CollectionExtractor getSegmentExtractor() throws SnapshotException {
        return CollectionExtractionUtils.findCollectionExtractor("java.util.concurrent.ConcurrentHashMap$Segment"); //$NON-NLS-1$
    }
}
