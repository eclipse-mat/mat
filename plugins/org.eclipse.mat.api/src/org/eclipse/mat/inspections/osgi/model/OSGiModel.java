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
package org.eclipse.mat.inspections.osgi.model;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;

public class OSGiModel
{
    private IBundleReader bundleReader;
    private List<Service> services;
    private List<ExtensionPoint> extensionPoints;
    private List<BundleDescriptor> bundleDescriptors;

    public OSGiModel(IBundleReader bundleReader, List<BundleDescriptor> bundleDescriptors, List<Service> services,
                    List<ExtensionPoint> extensionPoints)
    {
        this.bundleReader = bundleReader;
        this.bundleDescriptors = bundleDescriptors;
        this.services = services;
        this.extensionPoints = extensionPoints;
    }

    /**
     * Get descriptors of all the bundles, found in BundleRepository
     * 
     * @return List&lt;BundleDescriptor&gt; list of objects, describing the bundle
     */
    public List<BundleDescriptor> getBundleDescriptors()
    {
        return bundleDescriptors;
    }

    /**
     * Get bundle by its descriptor
     * 
     * @param descriptor
     * @return Bundle
     * @throws SnapshotException
     */
    public Bundle getBundle(BundleDescriptor descriptor) throws SnapshotException
    {
        return bundleReader.getBundle(descriptor);
    }

    /**
     * Get all the services found in ServiceRegistry
     * 
     * @return List&lt;Service&gt; list of services
     */
    public List<Service> getServices()
    {
        return services;
    }

    /**
     * Get all the extension points found in ExtensionRegistry
     * 
     * @return List&lt;ExtensionPoint&gt; list of extension points
     */
    public List<ExtensionPoint> getExtensionPoints()
    {
        return extensionPoints;
    }

}
