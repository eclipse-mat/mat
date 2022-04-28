/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - Javadoc
 *******************************************************************************/
package org.eclipse.mat.parser;

import java.io.IOException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

/**
 * Part of the parser which retrieves detailed information about an object
 */
public interface IObjectReader
{
    /**
     * Open the dump file associated with the snapshot
     * @param snapshot the snapshot
     * @throws SnapshotException some other problem
     * @throws IOException an IO problem, or corrupt indexes or unexpected data in the dump
     */
    void open(ISnapshot snapshot) //
                    throws SnapshotException, IOException;

    /**
     * Get detailed information about an object
     * @param objectId the object id
     * @param snapshot the snapshot
     * @return an IObject such as {@link org.eclipse.mat.parser.model.InstanceImpl}, {@link org.eclipse.mat.parser.model.ObjectArrayImpl}, {@link org.eclipse.mat.parser.model.PrimitiveArrayImpl}, {@link org.eclipse.mat.parser.model.ClassLoaderImpl}
     * @throws SnapshotException some other problem such as where the object is incompatible with the snapshot
     * @throws IOException an IO problem or unexpected data in the dump
     */
    IObject read(int objectId, ISnapshot snapshot) //
                    throws SnapshotException, IOException;

    /**
     * Get detailed information about a primitive array
     * @param array the array
     * @param offset where in the array to start
     * @param length how much to read
     * @return a byte[], short[], int[], long[], boolean[], char[], float[], double[]
     * @throws SnapshotException some other problem such as where the object is incompatible with the snapshot
     * @throws IOException an IO problem or unexpected data in the dump
     */
    Object readPrimitiveArrayContent(PrimitiveArrayImpl array, int offset, int length) //
                    throws IOException, SnapshotException;

    /**
     * Get detailed information about a object array
     * @param array the array
     * @param offset where in the array to start
     * @param length how much to read
     * @return an array of object addresses, with 0 for nulls 
     * @throws SnapshotException some other problem such as where the object is incompatible with the snapshot
     * @throws IOException an IO problem or unexpected data in the dump
     */
    long[] readObjectArrayContent(ObjectArrayImpl array, int offset, int length) //
                    throws IOException, SnapshotException;

    /**
     * Get additional information about the snapshot
     * @param addon type of the additional information
     * @param <A> used to set the return type
     * @return the additional information or null if none available
     * @throws SnapshotException an IO problem or unexpected data in the dump
     */
    <A> A getAddon(Class<A> addon) //
                    throws SnapshotException;

    /**
     * tidy up when snapshot no longer required
     * @throws IOException should not normally occur
     */
    void close() throws IOException;

}
