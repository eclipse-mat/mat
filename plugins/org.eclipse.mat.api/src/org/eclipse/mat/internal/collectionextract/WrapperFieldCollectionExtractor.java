/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class WrapperFieldCollectionExtractor extends WrapperCollectionExtractor
{

    String entryfield;
    public WrapperFieldCollectionExtractor(String field, String entryfield)
    {
        this(field, entryfield, null);
    }

    public WrapperFieldCollectionExtractor(String field, String entryfield, ICollectionExtractor extractor)
    {
        super(field, extractor);
        this.entryfield = entryfield;

    }

    @Override
    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        int r[] = super.extractEntryIds(coll);
        ArrayInt a = new ArrayInt(r.length);
        for (int i = 0; i < r.length; ++i)
        {
            IObject o = coll.getSnapshot().getObject(r[i]);
            Object o2 = o.resolveValue(entryfield);
            if (o2 instanceof IObject)
            {
                a.add(((IObject)o2).getObjectId());
            }
        }
        return a.toArray();
    }

    @Override
    public boolean hasExtractableArray()
    {
        return false;
    }
}
