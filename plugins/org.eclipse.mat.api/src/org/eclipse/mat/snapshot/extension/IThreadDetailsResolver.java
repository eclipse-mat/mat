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
package org.eclipse.mat.snapshot.extension;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.util.IProgressListener;

/**
 * Extracts detailed information about a thread
 */
public interface IThreadDetailsResolver
{
    /**
     * Detailed information as columns
     * @return an array of Columns
     */
    Column[] getColumns();

    /**
     * Extract basic information e.g. for ThreadOverviewQuery
     * @param thread to extract the information from and to attach the information
     * @param listener to log progress and report errors
     * @throws SnapshotException
     */
    void complementShallow(IThreadInfo thread, IProgressListener listener) throws SnapshotException;

    /**
     * Extract detailed information e.g. for ThreadInfoQuery
     * @param thread to extract the information from and to attach the information
     * @param listener to log progress and report errors
     * @throws SnapshotException
     */
    void complementDeep(IThreadInfo thread, IProgressListener listener) throws SnapshotException;

}
