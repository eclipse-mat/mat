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
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

/**
 * The allows details about a local variable for a thread of a type understood
 * by this resolver to be interpreted and attached to the thread descrition.
 * The request might be a request into a web server, and the resolver could
 * extract the URL etc.
 *
 */
public interface IRequestDetailsResolver
{
    /**
     * Add extra details
     * @param snapshot the whole dump
     * @param thread the thread processing the request
     * @param javaLocals all the local variables, as ids
     * @param thisJavaLocal this particular object, as an id
     * @param listener to show progress and log errors
     * @throws SnapshotException
     */
    void complement(ISnapshot snapshot, IThreadInfo thread, int[] javaLocals, int thisJavaLocal,
                    IProgressListener listener) throws SnapshotException;
}
