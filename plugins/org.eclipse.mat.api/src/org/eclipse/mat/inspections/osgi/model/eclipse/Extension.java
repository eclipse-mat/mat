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
package org.eclipse.mat.inspections.osgi.model.eclipse;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.inspections.osgi.model.BundleDescriptor;

public class Extension
{

    // Extension objectId
    private int objectId;
    private String[] properties;
    // name of the bundle, that adds this extension
    private BundleDescriptor contributedBy;

    private Integer extensionId;
    private List<ConfigurationElement> configurationElements = new ArrayList<ConfigurationElement>();

    // The human readable name of the extension
    private static final byte LABEL = 0;
    // The fully qualified name of the extension point to which this extension
    // is attached to
    private static final byte XPT_NAME = 1;
    // ID of the actual contributor of this extension
    private static final byte CONTRIBUTOR_ID = 2;

    public Extension(int objectId, Integer extensionId, String[] properties)
    {
        this.objectId = objectId;
        this.extensionId = extensionId;
        this.properties = properties;
    }

    /**
     * Get objectId of the Extension in the heap dump
     * 
     * @return int objectId
     */
    public int getObjectId()
    {
        return objectId;
    }

    /**
     * Get extension's unique identifier. This extension is assigned a unique
     * identifier by the Framework.
     * 
     * @return Integer extension's unique identifier
     */
    public Integer getExtensionId()
    {
        return extensionId;
    }

    /**
     * @return String extension's fully qualified name
     */
    public String getName()
    {
        return properties[XPT_NAME];
    }

    /**
     * @return String human readable name of the extension
     */
    public String getLabel()
    {
        return properties[LABEL];
    }

    /**
     * Get unique id of the bundle contributing this extension
     * 
     * @return String unique id of the bundle contributing this extension
     */
    public String getContributorId()
    {
        return properties[CONTRIBUTOR_ID];
    }

    public void setContributedBy(BundleDescriptor contributedBy)
    {
        this.contributedBy = contributedBy;
    }

    /**
     * Get descriptor of the bundle contributing this extension
     * 
     * @return BundleDescriptor of the bundle contributing this extension
     */
    public BundleDescriptor getContributedBy()
    {
        return contributedBy;
    }

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
        result = prime * result + ((contributedBy == null) ? 0 : contributedBy.hashCode());
        result = prime * result + ((extensionId == null) ? 0 : extensionId.hashCode());
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
        Extension other = (Extension) obj;
        if (contributedBy == null)
        {
            if (other.contributedBy != null)
                return false;
        }
        else if (!contributedBy.equals(other.contributedBy))
            return false;
        if (extensionId == null)
        {
            if (other.extensionId != null)
                return false;
        }
        else if (!extensionId.equals(other.extensionId))
            return false;
        return true;
    }

}
