/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation/Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

public class Pair12CollectionExtractor extends PairCollectionExtractor
{

    public Pair12CollectionExtractor(String field1, String field2)
    {
        super(field1, field2);
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return 1 + ((coll.resolveValue(field2) != null) ? 1 : 0);
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        int id1 = ((IObject) coll.resolveValue(field1)).getObjectId();
        IObject value2 = (IObject) coll.resolveValue(field2);
        if (value2 != null)
        {
            int id2= value2.getObjectId();
            return new int[] { id1, id2 };
        }
        else
        {
            return new int[] { id1 };
        }
    }
}
