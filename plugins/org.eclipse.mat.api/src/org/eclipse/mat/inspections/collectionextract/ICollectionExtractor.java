/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.inspections.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * @since 1.5
 */
public interface ICollectionExtractor
{
    boolean hasSize();

    Integer getSize(IObject coll) throws SnapshotException;

    boolean hasCapacity();

    Integer getCapacity(IObject coll) throws SnapshotException;

    boolean hasFillRatio();

    Double getFillRatio(IObject coll) throws SnapshotException;

    boolean hasExtractableContents();

    int[] extractEntryIds(IObject coll) throws SnapshotException;

    boolean hasExtractableArray();

    IObjectArray extractEntries(IObject coll) throws SnapshotException;

    // requires hasExtractableContents || hasExtractableArray
    Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException;
}
