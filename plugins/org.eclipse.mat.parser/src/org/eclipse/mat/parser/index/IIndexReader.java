/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
        int get(int index);

        int[] getAll(int index[]);

        int[] getNext(int index, int length);
    }

    /**
     * Index from object id to size, stored compressed as an int.
     * @since 1.0
     */
    public interface IOne2SizeIndex extends IOne2OneIndex
    {
        long getSize(int index);
    }

    /**
     * Index from object id to a long.
     * For example, object id to object address or object id to retained size.
     */
    public interface IOne2LongIndex extends IIndexReader
    {
        long get(int index);

        int reverse(long value);

        long[] getNext(int index, int length);
    }

    /**
     * Index from object id to several object ids.
     * For example outbound references from an object or outbound dominated ids.
     */
    public interface IOne2ManyIndex extends IIndexReader
    {
        int[] get(int index);
    }

    /**
     * Index from object id to several object ids.
     * For example inbound references from an object.
     */
    public interface IOne2ManyObjectsIndex extends IOne2ManyIndex
    {
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
