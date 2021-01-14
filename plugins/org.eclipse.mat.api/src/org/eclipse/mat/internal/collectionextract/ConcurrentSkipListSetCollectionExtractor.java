/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation from HashSetCollectionExtractor
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;

public class ConcurrentSkipListSetCollectionExtractor extends ConcurrentSkipListCollectionExtractor
{
    public ConcurrentSkipListSetCollectionExtractor(String arrayField, String keyField, String valueField)
    {
        super(arrayField, keyField, valueField);
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        ArrayInt ret = new ArrayInt();

        int[] entries = super.extractEntryIds(coll);

        for (int entryId : entries)
        {
            IInstance entry = (IInstance) snapshot.getObject(entryId);
            Object f = entry.resolveValue(keyField);
            if (f instanceof IObject)
            {
                ret.add(((IObject) f).getObjectId());
            }
        }
        return ret.toArray();
    }
}
