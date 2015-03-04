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
 * CollectionExtractors are used to extract from the heap dump the contents of
 * an object which represents a collection of a certain type. It knows the
 * internal details of how the collection contents are stored and if the
 * collection has certain properties or not
 * 
 * @since 1.5
 */
public interface ICollectionExtractor
{
    /**
     * Check if the size of the collection can be extracted
     * 
     * @return
     */
    boolean hasSize();

    /**
     * Extract the size of the collection
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    Integer getSize(IObject collection) throws SnapshotException;

    /**
     * Check if the collection has capacity, e.g. ArrayList
     * 
     * @return
     */
    boolean hasCapacity();

    /**
     * Return the capacity of the collection, if applicable
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    Integer getCapacity(IObject collection) throws SnapshotException;

    /**
     * Check if fill ratio for the collection can be calculated, i.e. if it has
     * some predefined capacity and actual size
     * 
     * @return
     */
    boolean hasFillRatio();

    /**
     * Calculate the fill ration of a collection
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    Double getFillRatio(IObject collection) throws SnapshotException;

    /**
     * Check if the collection has extractable contents
     * 
     * @return
     */
    boolean hasExtractableContents();

    /**
     * Returns the object ids (int) for all objects which are contained in the
     * collection
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    int[] extractEntryIds(IObject collection) throws SnapshotException;

    /**
     * Return true if the collection array based and the array can be extracted
     * from the heap dump
     * 
     * @return
     */
    boolean hasExtractableArray();

    /**
     * Extracts the array containing the collection content
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    IObjectArray extractEntries(IObject collection) throws SnapshotException;

    /**
     * Returns the number of non-null elements in the collection. Requires
     * hasExtractableContents or hasExtractableArray
     * 
     * @param collection
     * @return
     * @throws SnapshotException
     */
    Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException;
}
