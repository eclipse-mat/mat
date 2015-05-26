/*******************************************************************************
 * Copyright (c) 2008, 2015 IBM Corporation and others
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
            return lastIndex - firstIndex;
    }
}
