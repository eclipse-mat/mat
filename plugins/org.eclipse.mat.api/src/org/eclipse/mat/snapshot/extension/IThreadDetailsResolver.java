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
package org.eclipse.mat.snapshot.extension;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.util.IProgressListener;

/**
 * Extracts detailed information about a thread.
 * This is used by the thread queries ThreadOverviewQuery and ThreadInfoQuery.
 * Implementations of this interface need to be
 * registered using the <code>org.eclipse.mat.api.threadResolver</code> extension point.
 */
public interface IThreadDetailsResolver
{
    /**
     * Detailed information as columns
     * @return an array of Columns
     */
    Column[] getColumns();

    /**
     * Extract basic information about a thread, for example for ThreadOverviewQuery.
     * @param thread to extract the information from and to attach the information
     * @param listener to log progress and report errors
     * @throws SnapshotException problem retrieving or converting the data
     */
    void complementShallow(IThreadInfo thread, IProgressListener listener) throws SnapshotException;

    /**
     * Extract detailed information about a thread, for example for ThreadInfoQuery.
     * @param thread to extract the information from and to attach the information
     * @param listener to log progress and report errors
     * @throws SnapshotException problem retrieving or converting the data
     */
    void complementDeep(IThreadInfo thread, IProgressListener listener) throws SnapshotException;

}
