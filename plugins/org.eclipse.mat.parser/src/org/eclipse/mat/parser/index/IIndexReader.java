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
package org.eclipse.mat.parser.index;

import java.io.IOException;
import java.io.Serializable;

import org.eclipse.mat.SnapshotException;

/**
 * Interfaces for reading various indexes into the snapshot.
 */
public interface IIndexReader
{
    /**
     * Index from object id to another int.
     * For example, object id to type id.
     */
    public interface IOne2OneIndex extends IIndexReader
    {
        /**
         * Look up an int in the underlying index
         * @param index the int key
         * @return the int value
         */
        int get(int index);

        /**
         * Look up all the items from the index array and return the version
         * in the index
         * @param index an array of items to look up
         * @return an array of the result items
         */
        int[] getAll(int index[]);

        /**
         * Look up all the items from the index from index to index + length - 1
         * and return the result in the index for each on
         * @param index the start index
         * @param length the number of consecutive items to look up
         * @return an array of the result items
         */
        int[] getNext(int index, int length);
    }

    /**
     * Index from object id to size, stored compressed as an int.
     * @since 1.0
     */
    public interface IOne2SizeIndex extends IOne2OneIndex
    {
        /**
         * Look up the size of an object
         * @param index the object ID
         * @return size in bytes
         */
        long getSize(int index);
    }

    /**
     * Index from object id to a long.
     * For example, object id to object address or object id to retained size.
     */
    public interface IOne2LongIndex extends IIndexReader
    {
        /**
         * Look up a long from an int in the index.
         * @param index the key
         * @return the long
         */
        long get(int index);

        /**
         * Find the int corresponding to the long in the index value.
         * The reverse of {@link IIndexReader.IOne2LongIndex#get(int)} 
         * @param value the value to look up
         * @return the correspond int key in the index
         */
        int reverse(long value);

        /**
         * Look up long from an range of int keys in the index.
         * @param index the starting point
         * @param length the number of items to look up
         * @return an array of longs corresponding to the input
         */
        long[] getNext(int index, int length);
    }

    /**
     * Index from object id to several object ids.
     * For example outbound references from an object or outbound dominated ids.
     */
    public interface IOne2ManyIndex extends IIndexReader
    {
        /**
         * Get the object IDs corresponding to the input object ID
         * @param index
         * @return an array holding the object IDs
         */
        int[] get(int index);
    }

    /**
     * Index from object id to several object ids.
     * For example inbound references from an object.
     */
    public interface IOne2ManyObjectsIndex extends IOne2ManyIndex
    {
        /**
         * Get the object IDs corresponding to the input key
         * @param key
         * @return a list of object IDs corresponding to the key
         * @throws SnapshotException
         * @throws IOException
         */
        int[] getObjectsOf(Serializable key) throws SnapshotException, IOException;
    }

    /**
     * Size of the index
     * @return number of entries
     */
    int size();

    /**
     * Clear the caches. Used when the indexes are not current in use
     * and the memory needs to be reclaimed such as when building the dominator tree. 
     * @throws IOException
     */
    void unload() throws IOException;

    /**
     * Close the backing file.
     * @throws IOException
     */
    void close() throws IOException;

    /**
     * Delete the backing file.
     */
    void delete();
}
