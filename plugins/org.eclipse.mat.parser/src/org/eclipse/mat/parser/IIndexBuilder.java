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

import java.io.File;
import java.io.IOException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Part of the parser which builds the indexes
 */
public interface IIndexBuilder
{
    /**
     * initialize with file and prefix (needed for naming conventions)
     * @param file the dump file
     * @param prefix used to build index files
     * @throws SnapshotException
     * @throws IOException
     */
    void init(File file, String prefix) throws SnapshotException, IOException;

    /**
     * pass1 and pass2 parsing 
     * @param index
     * @param listener for progress and error reporting
     * @throws SnapshotException
     * @throws IOException
     */
    void fill(IPreliminaryIndex index, IProgressListener listener) throws SnapshotException, IOException;

    /**
     * Memory Analyzer has discard unreachable objects, so the parser may need to known
     * the discarded objects
     * @param purgedMapping mapping from old id to new id, -1 indicates object has been discarded
     * @param listener for progress and error reporting
     * @throws IOException
     */
    void clean(final int[] purgedMapping, IProgressListener listener) throws IOException;

    /**
     * called in case of error to delete any files / close any file handles
     */
    void cancel();
}
