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
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class FieldSizeArrayCollectionExtractor extends FieldArrayCollectionExtractor
{
    protected final String sizeField;

    public FieldSizeArrayCollectionExtractor(String sizeField, String arrayField)
    {
        super(arrayField);
        if (sizeField == null)
            throw new IllegalArgumentException();
        this.sizeField = sizeField;
    }

    @Override
    public boolean hasSize()
    {
        return true;
    }

    @Override
    public Integer getSize(IObject coll) throws SnapshotException
    {
        // fast path, check the size field
        Integer value = ExtractionUtils.toInteger(coll.resolveValue(sizeField));
        if (value != null)
        {
            return value;
        }
        else
        {
            IObjectArray array = extractEntries(coll);
            if (array != null)
            {
                // E.g. ArrayList
                return ExtractionUtils.getNumberOfNotNullArrayElements(array);
            }
            else
            {
                return null;
            }
        }
    }
}
