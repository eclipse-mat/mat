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
package org.eclipse.mat.inspections.osgi.model.eclipse;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.inspections.osgi.model.BundleDescriptor;

public class ConfigurationElement
{
    private int objectId;
    private Integer parentId;
    private String[] propertiesAndValues;
    private BundleDescriptor contributingBundle;
    private String name;
    private Integer elementId;
    private List<ConfigurationElement> configurationElements = new ArrayList<ConfigurationElement>();

    public class PropertyPair
    {
        public String property;
        public String value;

        PropertyPair(String property, String value)
        {
            this.property = property;
            this.value = value;
        }
    }

    public ConfigurationElement(int objectId, String name, Integer parentId, Integer elementId,
                    BundleDescriptor contributingBundle, String[] propertiesAndValues)
    {
        this.objectId = objectId;
        this.name = name;
        this.parentId = parentId;
        this.elementId = elementId;
        this.contributingBundle = contributingBundle;
        this.propertiesAndValues = propertiesAndValues;
    }

    /**
     * Get objectId of the ConfigurationElement in the heap dump
     * 
     * @return int objectId
     */
    public int getObjectId()
    {
        return objectId;
    }

    public String getName()
    {
        return name;
    }

    /**
     * Get id of the parent element. It can be a configuration element or an
     * extension
     * 
     * @return Integer parentId
     */
    public Integer getParentId()
    {
        return parentId;
    }

    /**
     * Get element's unique identifier. This element is assigned a unique
     * identifier by the Framework.
     * 
     * @return long bundle's unique identifier
     */

    public Integer getElementId()
    {
        return elementId;
    }

    /**
     * Get the properties and the value of the configuration element.
     * 
     * @return List<PropertyPair> properties
     */
    public List<PropertyPair> getPropertiesAndValues()
    {
        // The format is the following:
        // [p1, v1, p2, v2, configurationElementValue]
        // If the array size is even, there is no
        // configurationElementValue. We ignore configurationElementValue in any
        // case
        int length = propertiesAndValues.length;
        if (length % 2 != 0)
            length = length - 1;
        List<PropertyPair> properties = new ArrayList<PropertyPair>(length / 2);
        for (int i = 0; i < length; i++)
        {
            if (i % 2 != 0)
                continue;

            properties.add(new PropertyPair(propertiesAndValues[i], propertiesAndValues[i + 1]));
        }

        return properties;
    }

    /**
     * Descriptor of the bundle contributing this element. This value can be
     * null when the element is loaded from disk and the owner has been
     * uninstalled.
     * 
     * @return BundleDescriptor of the bundle contributing this element
     */
    public BundleDescriptor getContributingBundle()
    {
        return contributingBundle;
    }

    /**
     * Get nested configuration elements
     * 
     * @return List<ConfigurationElement> nested configuration elements
     */
    public List<ConfigurationElement> getConfigurationElements()
    {
        return configurationElements;
    }

    public void addConfigurationElement(ConfigurationElement configurationElement)
    {
        if (!configurationElements.contains(configurationElement))
            configurationElements.add(configurationElement);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((elementId == null) ? 0 : elementId.hashCode());
        result = prime * result + ((parentId == null) ? 0 : parentId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConfigurationElement other = (ConfigurationElement) obj;
        if (elementId == null)
        {
            if (other.elementId != null)
                return false;
        }
        else if (!elementId.equals(other.elementId))
            return false;
        if (parentId == null)
        {
            if (other.parentId != null)
                return false;
        }
        else if (!parentId.equals(other.parentId))
            return false;
        return true;
    }

    
}
