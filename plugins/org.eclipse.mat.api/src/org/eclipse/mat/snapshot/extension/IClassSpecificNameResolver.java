/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
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
import org.eclipse.mat.snapshot.model.IObject;

/**
 * Interface describing a name resolver for objects of specific classes (found
 * in an snapshot), e.g. String (where the char[] is evaluated) or as specific
 * class loader (where the appropriate field holding its name and thereby
 * deployment unit is evaluated). Objects of this interface need to be
 * registered using the <code>org.eclipse.mat.api.nameResolver</code> extension point.
 * Implementations of this interface should be tagged with the {@link Subject} or 
 * {@link Subjects} annotation to specify the types of objects 
 * in the dump they describe.
 */
public interface IClassSpecificNameResolver
{
    /**
     * Resolve the name for snapshot object.
     * 
     * @param object
     *            object for which the name should be resolved
     * @return name for snapshot object
     * @throws SnapshotException
     */
    public String resolve(IObject object) throws SnapshotException;
}
