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
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * Known-empty collections
 */
public class EmptyCollectionExtractor implements ICollectionExtractor
{
    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return 0;
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        return 0;
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return new int[0];
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        return 0;
    }

    public boolean hasFillRatio()
    {
        return false;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return null;
    }
}
