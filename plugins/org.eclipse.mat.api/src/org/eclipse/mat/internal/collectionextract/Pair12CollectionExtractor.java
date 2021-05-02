/*******************************************************************************
 * Copyright (c) 2020,2021 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation/Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class Pair12CollectionExtractor extends PairCollectionExtractor
{

    public Pair12CollectionExtractor(String field1, String field2)
    {
        super(field1, field2);
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        Object val2 = coll.resolveValue(field2);
        if (val2 instanceof IObject)
        {
            IObject obj2 = (IObject)val2;
            if (obj2.getClazz().getName().equals("java.lang.Object")) //$NON-NLS-1$
            {
                IObject empty = emptyValue(coll);
                if (empty != null && obj2.getObjectId() == empty.getObjectId())
                    return 1;
            }
            return 2;
        }
        return 1 + ((val2 != null) ? 1 : 0);
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        int id1 = ((IObject) coll.resolveValue(field1)).getObjectId();
        if (getSize(coll) == 2)
        {
            IObject value2 = (IObject) coll.resolveValue(field2);
            int id2= value2.getObjectId();
            return new int[] { id1, id2 };
        }
        else
        {
            return new int[] { id1 };
        }
    }

    /**
     * Java 15 used a special object instead of null to mark a 1 element collection
     * @param coll
     * @return the object or null if not found
     */
    protected IObject emptyValue(IObject coll)
    {
        try
        {
            Collection<IClass>cl = coll.getSnapshot().getClassesByName("java.util.ImmutableCollections", false); //$NON-NLS-1$
            if (cl != null && cl.size() >= 1)
            {
                IClass ic = cl.iterator().next();
                return (IObject) ic.resolveValue("EMPTY"); //$NON-NLS-1$
            }
        }
        catch (SnapshotException e)
        {
        }
        return null;
    }
}
