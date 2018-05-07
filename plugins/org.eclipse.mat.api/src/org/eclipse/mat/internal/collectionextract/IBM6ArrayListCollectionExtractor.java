/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation  - initial API and implementation
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class IBM6ArrayListCollectionExtractor extends FieldArrayCollectionExtractor
{
    private String firstIndex;
    private String lastIndex;

    public IBM6ArrayListCollectionExtractor(String firstIndex, String lastIndex, String arrayField)
    {
        super(arrayField);
        if (firstIndex == null)
            throw new IllegalArgumentException();
        if (lastIndex == null)
            throw new IllegalArgumentException();
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
    }

    @Override
    public boolean hasSize()
    {
        return true;
    }

    @Override
    public Integer getSize(IObject coll) throws SnapshotException
    {
        Integer firstIndex = (Integer) coll.resolveValue(this.firstIndex);
        Integer lastIndex = (Integer) coll.resolveValue(this.lastIndex);

        if (lastIndex == null)
            return null;
        else if (firstIndex == null || lastIndex <= 0)
            return lastIndex;
        else
        {
            if (lastIndex >= firstIndex)
            {
                return lastIndex - firstIndex;
            }
            else
            {
                // Queue wraps
                return lastIndex - firstIndex + getCapacity(coll);
            }
        }
    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        long referenceArray[] = extractEntries(coll).getReferenceArray();
        Integer size = getSize(coll);
        if (size == null)
        {
            return new int[0];
        }
        ArrayInt arr = new ArrayInt(size);
        if (size > 0)
        {
            int firstIndex = (Integer) coll.resolveValue(this.firstIndex);
            int lastIndex = (Integer) coll.resolveValue(this.lastIndex);
            int end = getCapacity(coll);
            for (int i = firstIndex; i < (firstIndex <= lastIndex  ? lastIndex : end); i++)
            {
                if (referenceArray[i] != 0)
                    arr.add(snapshot.mapAddressToId(referenceArray[i]));
            }
            for (int i = 0; i < (firstIndex <= lastIndex ? 0 : lastIndex); i++)
            {
                if (referenceArray[i] != 0)
                    arr.add(snapshot.mapAddressToId(referenceArray[i]));
            }
        }
        return arr.toArray();

    }
}
