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

    /**
     * @return resolved host that this fragment is attached to
     */
    public BundleDescriptor getHost()
    {
        return host;
    }

}
