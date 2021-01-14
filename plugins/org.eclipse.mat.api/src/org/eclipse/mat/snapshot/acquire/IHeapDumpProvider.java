/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG.
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
package org.eclipse.mat.snapshot.acquire;

import java.io.File;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.util.IProgressListener;

/**
 * Provides functionality to acquire a heap dump from a locally running Java process
 * Implementations of this interface need to be
 * registered using the <code>org.eclipse.mat.api.heapDumpProvider</code> extension point.
 * Arguments can be injected into the query using public fields marked with the {@link Argument} annotation.
 * Typical arguments to be supplied by the user of the heap dump provider include
 * <ul>
 * <li>boolean flags</li>
 * <li>int parm</li>
 * <li>File file optionally tagged with tagged with {@link Advice#DIRECTORY} or  {@link Advice#SAVE}.</li>
 * <li>enum - an enum</li>
 * </ul>
 * The implementation can be tagged with the following annotations to control the description and help text.
 * <ul>
 * <li>{@link org.eclipse.mat.query.annotations.Name}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Help}</li>
 * <li>{@link org.eclipse.mat.query.annotations.HelpUrl}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Usage}</li>
 * </ul>
 * 
 * @author ktsvetkov
 * @since 1.0
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
	 * @return A List of VMs, of a type which extends {@link VmInfo}.
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
