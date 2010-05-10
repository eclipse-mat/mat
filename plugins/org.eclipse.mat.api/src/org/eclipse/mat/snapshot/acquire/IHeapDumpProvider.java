/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.acquire;

import java.io.File;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Provides functionality to acquire a heap dump from a locally running Java process
 * 
 * @author ktsvetkov
 *
 */
public interface IHeapDumpProvider
{
	/**
	 * Returns a list of locally running Java processes from which the heap dump
	 * provider can attempt to acquire a heap dump
	 * 
	 * @param listener
	 *            a progress listener
	 * 
	 * @return List<? extends VmInfo> the list of processes ({@link VmInfo})
	 * 
	 * @throws SnapshotException
	 */
	public List<? extends VmInfo> getAvailableVMs(IProgressListener listener) throws SnapshotException;

	/**
	 * Acquire a heap dump from a locally running Java process. The
	 * 
	 * @param info
	 *            a descriptor of the Java process which should be dumped
	 * @param preferredLocation
	 *            a preferred filename under which the heap dump should be
	 *            saved. The {@link IHeapDumpProvider} is not obliged to provide
	 *            the heap dump at this location
	 * @param listener
	 *            a progress listener
	 * @return the File under which the successfully generated heap dump is
	 *         saved
	 * @throws SnapshotException
	 */
	public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException;
}
