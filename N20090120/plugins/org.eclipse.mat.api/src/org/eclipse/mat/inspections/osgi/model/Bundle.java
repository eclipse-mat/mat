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

import java.util.List;

import org.eclipse.mat.inspections.osgi.model.eclipse.Extension;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;

public class Bundle 
{
    private BundleDescriptor descriptor;   
    private String location;    
    private List<ExtensionPoint> extensionPoints;
    private List<Extension> extensions;
    private List<BundleDescriptor> dependencies;
    private List<BundleDescriptor> dependents;
    private List<Service> registeredServices;
    private List<Service> usedServices;
    private List<BundleDescriptor> fragments;

    public Bundle(BundleDescriptor descriptor, String location, List<BundleDescriptor> dependencies,
                    List<BundleDescriptor> dependents, List<ExtensionPoint> extensionPoints, List<Extension> extensions,
                    List<Service> registeredServices, List<Service> usedServices, List<BundleDescriptor> fragments)
    {
        this.descriptor = descriptor;        
        this.location = location;        
        this.dependencies = dependencies;
        this.dependents = dependents;
        this.extensionPoints = extensionPoints;
        this.extensions = extensions;
        this.registeredServices = registeredServices;
        this.usedServices = usedServices;
        this.fragments = fragments;
    }

    public BundleDescriptor getBundleDescriptor()
    {
        return descriptor;
    }      

    public List<ExtensionPoint> getExtentionPoints()
    {
        return extensionPoints;
    }

    public List<Extension> getExtentions()
    {
        return extensions;
    }

    public List<BundleDescriptor> getDependencies()
    {
        return dependencies;
    }

    public String getLocation()
    {
        return location;
    }

    public List<BundleDescriptor> getDependents()
    {
        return dependents;
    }

    public List<Service> getRegisteredServices()
    {
        return registeredServices;
    }

    public List<Service> getUsedServices()
    {
        return usedServices;
    }
    
    public List<BundleDescriptor> getFragments()
    {
        return fragments;
    }

}
