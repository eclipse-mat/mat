package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollectionBase;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class AbstractFillRatioQuery {
    protected void runQuantizer(IProgressListener listener, Quantize quantize,
            CollectionExtractor specificExtractor, String specificClass,
            ISnapshot snapshot, Iterable<int[]> objects) throws SnapshotException {
        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    return;

                IObject obj = snapshot.getObject(objectId);
                try {
                	ExtractedCollectionBase coll = CollectionExtractionUtils.extractCollectionUnlessSpecific(obj, specificClass, specificExtractor);
                    if (coll != null) {
                    	Double fillRatio = coll.getFillRatio();
                    	if (fillRatio != null)
                    		quantize.addValue(objectId, fillRatio, 1, coll.getUsedHeapSize());
	                }
                } catch (RuntimeException e) {
                    listener.sendUserMessage(IProgressListener.Severity.INFO,
                                    MessageUtil.format(Messages.CollectionFillRatioQuery_IgnoringCollection, obj.getTechnicalName()), e);
                }
            }
        }
    }
}