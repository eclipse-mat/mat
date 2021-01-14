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
package org.eclipse.mat.snapshot.model;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * An interface for class loader.
 * 
 * @noimplement
 */
public interface IClassLoader extends IInstance
{
    /**
     * Returns the retained size of all objects and classes loaded by this class
     * loader.
     * @param calculateIfNotAvailable if false only return a cached version of the size
     * @param calculateMinRetainedSize if true then when calculating use an approximation
     * @param listener to indicate progress and errors
     * @return the retained size, negative if approximate
     * @throws SnapshotException
     */
    long getRetainedHeapSizeOfObjects(boolean calculateIfNotAvailable, boolean calculateMinRetainedSize,
                    IProgressListener listener) throws SnapshotException;

    /**
     * Returns the classes defined by this class loader instance.
     */
    List<IClass> getDefinedClasses() throws SnapshotException;
}
