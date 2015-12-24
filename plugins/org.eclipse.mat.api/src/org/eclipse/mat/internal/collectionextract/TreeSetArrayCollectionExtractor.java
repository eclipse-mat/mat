/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
        super(sizeField, keyField+"[]", valueField+"[]");
        mx2 = new TreeMapArrayCollectionExtractor(sizeField, keyField, valueField);
    }
    
    public Iterator<Entry<IObject, IObject>> extractMapEntries(IObject coll) throws SnapshotException
    {
        return mx2.extractMapEntries(coll);
    }
}
