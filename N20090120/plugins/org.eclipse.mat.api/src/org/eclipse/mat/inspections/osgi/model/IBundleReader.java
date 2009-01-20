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
package org.eclipse.mat.inspections.osgi.model;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

public interface IBundleReader
{

    /** get a model describing the OSGi framework
     * @param listener
     * @return OSGi model
     * @throws SnapshotException
     */
    public OSGiModel readOSGiModel(IProgressListener listener) throws SnapshotException;

    /** Load the bundle by its descriptor
     * @param descriptor
     * @return Bundle, which contains full information (dependencies, dependents, services, extension points, extensions)
     * @throws SnapshotException
     */
    public Bundle getBundle(BundleDescriptor descriptor) throws SnapshotException;

}
