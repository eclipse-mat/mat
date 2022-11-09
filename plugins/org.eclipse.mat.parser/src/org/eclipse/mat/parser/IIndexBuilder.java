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
     * Initialize with file and prefix (needed for naming conventions).
     * @param file the dump file
     * @param prefix used to build index files
     * @throws SnapshotException for example, some problem with the dump file
     * @throws IOException for example, problem reading the dump file
     */
    void init(File file, String prefix) throws SnapshotException, IOException;

    /**
     * Pass1 and pass2 parsing.
     * @param index
     * @param listener for progress and error reporting
     * @throws SnapshotException major problem parsing the dump
     * @throws IOException for example, problem reading the dump file or wrong file type
     */
    void fill(IPreliminaryIndex index, IProgressListener listener) throws SnapshotException, IOException;

    /**
     * Memory Analyzer has discarded unreachable objects, so the parser may need to know
     * the discarded objects.
     * @param purgedMapping mapping from old id to new id, -1 indicates object has been discarded
     * @param listener for progress and error reporting
     * @throws IOException for example, problem writing a new index
     */
    void clean(final int[] purgedMapping, IProgressListener listener) throws IOException;

    /**
     * called in case of error to delete any files / close any file handles
     */
    void cancel();
}
