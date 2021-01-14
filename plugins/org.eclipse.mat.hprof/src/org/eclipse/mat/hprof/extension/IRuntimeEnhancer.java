/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
package org.eclipse.mat.hprof.extension;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;

/**
 * Using this interface an implementor can provide additional information to a
 * HPROF snapshot
 * 
 * See the documentation on the org.eclipse.mat.hprof.enhancer extension point
 * 
 */
public interface IRuntimeEnhancer
{

	/**
	 * Returns addon information of certain type if such information is attached
	 * to the HPROF snapshot
	 * 
	 * @param <A> The type of the additional information
	 * @param snapshot
	 * @param addon the class of the required extra information
	 * @return the extra information, or null
	 * @throws SnapshotException
	 */
	<A> A getAddon(ISnapshot snapshot, Class<A> addon) throws SnapshotException;
}
