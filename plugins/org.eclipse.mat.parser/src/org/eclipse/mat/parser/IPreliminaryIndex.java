/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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
     * @param classesById
     */
    void setClassesById(HashMapIntObject<ClassImpl> classesById);

    /**
     * store the GC roots information
     * @param gcRoots
     */
    void setGcRoots(HashMapIntObject<List<XGCRootInfo>> gcRoots);

    /**
     * store the thread local variable information 
     * @param thread2objects2roots
     */
    void setThread2objects2roots(HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thread2objects2roots);

    /**
     * store the object to outbound references table.
     * The type of the object must be the first reference.
     * @param outbound
     */
    void setOutbound(IIndexReader.IOne2ManyIndex outbound);

    /**
     * store the object id to address mapping
     * @param identifiers
     */
    void setIdentifiers(IIndexReader.IOne2LongIndex identifiers);

    /**
     * store the object id to class id mapping
     * @param object2classId
     */
    void setObject2classId(IIndexReader.IOne2OneIndex object2classId);

    /**
     * store the array to size in bytes mapping
     * @param array2size
     * @since 1.0
     */
    void setArray2size(IIndexReader.IOne2SizeIndex array2size);

}
