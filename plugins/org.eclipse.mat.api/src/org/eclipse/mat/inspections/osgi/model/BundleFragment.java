/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.osgi.model;

import java.util.List;

import org.eclipse.mat.inspections.osgi.model.eclipse.Extension;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;

public class BundleFragment extends Bundle
{
    private BundleDescriptor host;

    public BundleFragment(BundleDescriptor descriptor, String location, BundleDescriptor host)
    {
        // Fragments cannot have a BundleContext and therefore
        // cannot have any services (used or registered).
        super(descriptor, location, null, null, null, null, null, null, null);
        this.host = host;
    }

    public BundleFragment(BundleDescriptor descriptor, String location, BundleDescriptor host,
                    List<BundleDescriptor> dependencies, List<BundleDescriptor> dependents, 
                    List<ExtensionPoint> points, List<Extension> extensions)
    {
        // Fragments cannot have a BundleContext and therefore
        // cannot have any services (used or registered).
        // It appears they can have extensions or extension points.
        // See org.eclipse.m2e.jdt.ui/fragment.xml
        // The MANIFEST.MF does have Require-Bundle: for dependencies
        // There should not be dependents - that should be via the host
        super(descriptor, location, dependencies, dependents, points, extensions, null, null, null);
        this.host = host;
    }

    /**
     * @return resolved host that this fragment is attached to
     */
    public BundleDescriptor getHost()
    {
        return host;
    }

}
