/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser;

import java.util.List;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;

/**
 * Where the parser collect informations when first opening a snapshot
 * @noimplement
 */
public interface IPreliminaryIndex
{
    /**
     * Get basic information about the snapshot
     * @return the basic data
     */
    XSnapshotInfo getSnapshotInfo();

    /**
     * Store the class id to ClassImpl mapping
     * @param classesById the map of class ID to ClassImp
     */
    void setClassesById(HashMapIntObject<ClassImpl> classesById);

    /**
     * store the GC roots information
     * @param gcRoots the map from object ID to list of GC roots
     */
    void setGcRoots(HashMapIntObject<List<XGCRootInfo>> gcRoots);

    /**
     * store the thread local variable information 
     * @param thread2objects2roots the map from thread ID to a map of object ID of local variables
     * to a list of GC root information
     */
    void setThread2objects2roots(HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thread2objects2roots);

    /**
     * store the object to outbound references table.
     * The type of the object must be the first reference.
     * @param outbound an index from object ID to all the outbound references as object IDs
     */
    void setOutbound(IIndexReader.IOne2ManyIndex outbound);

    /**
     * store the object id to address mapping
     * @param identifiers the index from object ID to object address
     */
    void setIdentifiers(IIndexReader.IOne2LongIndex identifiers);

    /**
     * store the object id to class id mapping
     * @param object2classId the index from object ID to its type as a class ID
     */
    void setObject2classId(IIndexReader.IOne2OneIndex object2classId);

    /**
     * store the array to size in bytes mapping
     * @param array2size an index from the object ID of an array to its size in bytes 
     * @since 1.0
     */
    void setArray2size(IIndexReader.IOne2SizeIndex array2size);

}
