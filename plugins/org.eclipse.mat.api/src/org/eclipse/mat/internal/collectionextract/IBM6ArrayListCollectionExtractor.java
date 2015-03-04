package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

public class IBM6ArrayListCollectionExtractor extends FieldArrayCollectionExtractor {
    private String firstIndex;
    private String lastIndex;

    public IBM6ArrayListCollectionExtractor(String firstIndex, String lastIndex, String arrayField) {
        super(arrayField);
        if (firstIndex == null)
            throw new IllegalArgumentException();
        if (lastIndex == null)
            throw new IllegalArgumentException();
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
    }

    @Override
    public boolean hasSize() {
        return true;
    }

    @Override
    public Integer getSize(IObject coll) throws SnapshotException {
        Integer firstIndex = (Integer) coll.resolveValue(this.firstIndex);
        Integer lastIndex = (Integer) coll.resolveValue(this.lastIndex);

        if (lastIndex == null)
        	return null;
    	else if (firstIndex == null || lastIndex <= 0)
            return lastIndex;
        else
            return lastIndex - firstIndex;
    }
}
