package org.eclipse.mat.inspections.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.collectionextract.ArrayCollectionExtractor;
import org.eclipse.mat.internal.collectionextract.KnownCollectionInfo;
import org.eclipse.mat.internal.collectionextract.KnownCollectionInfo.Info;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;



/**
 * @since 1.5
 */
public class CollectionExtractionUtils {
    // in the original code, from-object searches in reverse order and from-classname goes forwards?!?
    public static CollectionExtractor findCollectionExtractor(IObject coll) throws SnapshotException {
        if (coll == null)
            return null;
        if (coll instanceof IObjectArray)
            return ArrayCollectionExtractor.INSTANCE;

        int version = KnownCollectionInfo.resolveVersion(coll.getSnapshot());
        IClass collectionClass = coll.getClazz();

        for (Info info : KnownCollectionInfo.knownCollections) {
            if ((info.version & version) == version) {
                if (collectionClass.doesExtend(info.className)) {
                    return info.extractor;
                }
            }
        }

        return null;
    }

    public static CollectionExtractor findCollectionExtractor(String className) throws SnapshotException {
        for (Info info : KnownCollectionInfo.knownCollections) {
            if (info.className.equals(className))
                return info.extractor;
        }
        return null;
    }

    public static ExtractedCollectionBase<?, ?> extractCollection(IObject coll) throws SnapshotException {
        CollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else if (extractor instanceof MapExtractor)
            return new ExtractedMap(coll, (MapExtractor) extractor);
        else
            return new ExtractedCollection(coll, extractor);
    }
    
    public static ExtractedCollectionBase<?, ?> extractCollectionUnlessSpecific(IObject coll,
    		String specificClass, CollectionExtractor specificExtractor) throws SnapshotException {
        if (specificClass != null && coll.getClazz().doesExtend(specificClass)) {
            return new ExtractedCollection(coll, specificExtractor);
        } else {
        	return extractCollection(coll);
        }
    }

	public static ExtractedMap extractMapUnlessSpecific(IObject coll,
			String specificClass, MapExtractor specificExtractor) throws SnapshotException {
        if (specificClass != null && coll.getClazz().doesExtend(specificClass)) {
            return new ExtractedMap(coll, specificExtractor);
        } else {
        	return extractMap(coll);
        }
	}

    public static ExtractedCollection extractList(IObject coll) throws SnapshotException {
        CollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else
            return new ExtractedCollection(coll, extractor);
    }

    public static ExtractedMap extractMap(IObject coll) throws SnapshotException {
        CollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else if (extractor instanceof MapExtractor)
            return new ExtractedMap(coll, (MapExtractor) extractor);
        else
            return null;
    }
}
