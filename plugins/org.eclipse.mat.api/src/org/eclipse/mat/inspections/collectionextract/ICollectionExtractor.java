/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others
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
     * Check if the size of the collection can be extracted.
     * @return true if {@link #getSize(IObject)} could be called
     * @see #getSize(IObject)
     */
    boolean hasSize();

    /**
     * Extract the size of the collection.
     * @param collection
     *            - the collection to find the size of
     * @return the size, or null if not available
     * @throws SnapshotException
     * @see #hasSize()
     */
    Integer getSize(IObject collection) throws SnapshotException;

    /**
     * Check if the collection has capacity, e.g. ArrayList
     * @return true if {@link #getCapacity(IObject)} could be called
     * @see #getCapacity(IObject)
     */
    boolean hasCapacity();

    /**
     * Return the capacity of the collection, if applicable
     * @param collection
     *            - the collection to find the capacity of
     * @return the capacity in bytes, or null if unavailable
     * @throws SnapshotException
     * @see #hasCapacity()
     */
    Integer getCapacity(IObject collection) throws SnapshotException;

    /**
     * Check if fill ratio for the collection can be calculated, i.e. if it has
     * some predefined capacity and actual size.
     * @return true if {@link #getFillRatio(IObject)} could be called
     * @see #getFillRatio(IObject)
     */
    boolean hasFillRatio();

    /**
     * Calculate the fill ratio of a collection
     * 
     * @param collection
     *            - the collection to find the fill ratio of
     * @return the fill ratio, between 0.0 and 1.0, or null if unavailable
     * @throws SnapshotException
     * @see #hasFillRatio()
     */
    Double getFillRatio(IObject collection) throws SnapshotException;

    /**
     * Check if the collection has extractable contents
     * 
     * @return true if {@link #extractEntryIds(IObject)} could be called
     * @see #extractEntryIds(IObject)
     */
    boolean hasExtractableContents();

    /**
     * Returns the object ids (int) for all objects which are contained in the
     * collection
     * 
     * @param collection
     *            - the collection to find the objects it holds
     * @return an array of ints which are the object ids.
     * @throws SnapshotException
     * @see #hasExtractableContents()
     */
    int[] extractEntryIds(IObject collection) throws SnapshotException;

    /**
     * Return true if the collection array based and the array can be extracted
     * from the heap dump
     * @return true if {@link #extractEntries(IObject)} could be called
     * @see #extractEntries(IObject)
     */
    boolean hasExtractableArray();

    /**
     * Extracts the array containing the collection content
     * 
     * @param collection
     *            - the collection to find the object array holding its contents
     * @return the backing array for the collection
     * @throws SnapshotException
     * @see #hasExtractableArray()
     */
    IObjectArray extractEntries(IObject collection) throws SnapshotException;

    /**
     * Returns the number of non-null elements in the collection. Requires
     * hasExtractableContents or hasExtractableArray
     * 
     * @param collection
     *            - the collection to find the number of non-null content objects
     * @return the number of non-null elements, or null if not available
     * @throws SnapshotException
     * @see #hasExtractableContents() 
     * @see #hasExtractableArray()
     */
    Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException;
}
