/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.impl.snapshot.extract;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * Extracts the object ids of the keys of java.util.HashMap.
 */
public class HashMapKeysExtractor extends HashMapValuesExtractor
{
    @Override
    protected void processEntry(ISnapshot snapshot, ArrayInt result, IObject entry) throws SnapshotException
    {
        IObject obj = (IObject)entry.resolveValue("key");
        if (obj != null)
            result.add(obj.getObjectId());
    }
}
