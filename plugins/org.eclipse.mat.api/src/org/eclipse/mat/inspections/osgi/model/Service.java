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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.util.MessageUtil;

public class Service
{
    // service name
    private String name;
    // ServiceRegistrationImpl objectId
    private int objectId;
    // bundle, that registers the service
    private BundleDescriptor bundleDescriptor;
    // bundles, that use the service
    private List<BundleDescriptor> bundlesUsing;

    private String[] keys;
    private String[] values;

    public class ServiceProperty
    {
        public String property;
        public String value;

        public ServiceProperty(String property, String value)
        {
            this.property = property;
            this.value = value;
        }
    }

    public Service(String name, int objectId, BundleDescriptor bundleDescriptor, List<BundleDescriptor> bundlesUsing,
                    String[] keys, String[] values)
    {
        this.name = name;
        this.objectId = objectId;
        this.bundleDescriptor = bundleDescriptor;
        this.bundlesUsing = bundlesUsing;
        this.keys = keys;
        this.values = values;
    }

    /**
     * Get bundle descriptor of a bundle, that registeres this service
     * 
     * @return BundleDescriptor
     */
    public BundleDescriptor getBundleDescriptor()
    {
        return bundleDescriptor;
    }

    /**
     * Get bundle descriptors for all the bundles, using this service
     * 
     * @return List<BundleDescriptor> list of descriptors of the bundles, using
     *         this service
     */
    public List<BundleDescriptor> getBundlesUsing()
    {
        return bundlesUsing;
    }

    /**
     * Get objectId of this service
     * 
     * @return int objectId
     */
    public int getObjectId()
    {
        return objectId;
    }

    /**
     * Get service's symbolic name
     * 
     * @return String name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get service's properties
     * 
     * @return List<ServiceProperty> properties, key-value pairs
     */
    public List<ServiceProperty> getProperties()
    {
        if (keys == null || values == null)
            return null;
        if (keys.length != values.length)
        {
            MATPlugin.log(MessageUtil.format(
                            "Number of keys does not correspond to the number of values for the service: 0x{0}", Long
                                            .toHexString(this.objectId)));
            return null;
        }
        List<ServiceProperty> properties = new ArrayList<ServiceProperty>(keys.length);
        for (int i = 0; i < keys.length; i++)
        {
            properties.add(new ServiceProperty(keys[i], values[i]));
        }
        return properties;
    }
}
