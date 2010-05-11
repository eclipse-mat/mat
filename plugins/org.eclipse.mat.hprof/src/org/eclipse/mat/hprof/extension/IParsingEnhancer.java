/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.extension;

import java.io.IOException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotInfo;

/**
 * This interface provides the possibility to perform some actions and add
 * information the a parsed snapshot, just after the parsing of an HPROF file is
 * done. Thus if there is a file separate from the HPROF file which provides
 * additional information, an implementor of this interface can attach this
 * additional information to the snapshot
 * 
 * See the documentation on the org.eclipse.mat.hprof.enhancer extension point
 */
public interface IParsingEnhancer
{

	/**
	 * The method within the process of initially parsing a heap dump, just
	 * after the snapshot and SnapshotInfo objects have been created.
	 * 
	 * @param snapshotInfo
	 *            the SnapshotInfo objects for the snapshot being parsed
	 * 
	 * @throws SnapshotException
	 * @throws IOException
	 */
	void onParsingCompleted(SnapshotInfo snapshotInfo) throws SnapshotException, IOException;
}
