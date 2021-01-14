/*******************************************************************************
 * Copyright (c) 2015,2020 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class TreeSetArrayCollectionExtractor extends TreeSetCollectionExtractor
{
    IMapExtractor mx2;
    public TreeSetArrayCollectionExtractor(String sizeField, String keyField, String valueField)
    {
        super(sizeField, keyField+"[]", valueField+"[]"); //$NON-NLS-1$ //$NON-NLS-2$
        mx2 = new TreeMapArrayCollectionExtractor(sizeField, keyField, valueField);
    }
    
    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException
    {
        return mx2.extractMapEntries(coll);
    }
}
