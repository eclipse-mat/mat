/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.impl.snapshot.extract;

import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

/**
 * Interface implemented by all extractors. An extractor extracts snapshot
 * objects from a single other snapshot object, e.g. a HashMap.
 */
public interface IExtractor
{
    /**
     * Tests whether the operation can be applied to the given object.
     * 
     * @param object
     *            an arbitrary snapshot object
     * @return true if the exraction can be applied
     */
    public boolean appliesTo(IObject object);

    /**
     * Extracts elements from the given object. The specific implementations
     * define which elements are extracted.
     * 
     * @param object
     *            an arbitrary snapshot object
     * @param progressListener
     *            progress listener informing about the current state of
     *            execution
     * @return an int array with the extracted object ids
     * @throws SnapshotException
     */
    public int[] extractFrom(IObject object, IProgressListener progressListener) throws SnapshotException;
}
