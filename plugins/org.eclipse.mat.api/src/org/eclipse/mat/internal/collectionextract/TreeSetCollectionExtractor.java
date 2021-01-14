/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others
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

import org.eclipse.mat.inspections.collectionextract.IMapExtractor;

public class TreeSetCollectionExtractor extends HashSetCollectionExtractor
{
    IMapExtractor mx;
    public TreeSetCollectionExtractor(String sizeField, String keyField, String valueField)
    {
        super(sizeField, "dummy", keyField, valueField); //$NON-NLS-1$
        mx = new TreeMapCollectionExtractor(sizeField, keyField, valueField);
    }

    IMapExtractor createHashMapExtractor()
    {
        return mx;
    }
}
