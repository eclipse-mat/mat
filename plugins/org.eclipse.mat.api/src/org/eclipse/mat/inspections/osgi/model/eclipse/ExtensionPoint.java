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

public class ExtensionPoint
{
    private int objectId;
    private Integer extensionPointId;
    private String[] properties;
    // name of the bundle, that adds this extension
    private BundleDescriptor contributedBy;
    private List<Extension> extensions = new ArrayList<Extension>();
    /** The human readable name for the extension point */
    private static final byte LABEL = 0;
    /** The fully qualified name of the extension point */
    private static final byte CONTRIBUTOR_ID = 4;
    /** The ID of the actual contributor of the extension point */
    private static final byte QUALIFIED_NAME = 2;

    /*
     * Not used info: private static final byte NAMESPACE = 3; // The name of
     * the namespace of the extension point private static final byte SCHEMA =
     * 1; // The schema of the extension point
     */

    public ExtensionPoint(int objectId, Integer extensionPointId, String[] properties)
    {
        this.objectId = objectId;
        this.extensionPointId = extensionPointId;
        this.properties = properties;
    }

    /**
     * Get objectId of the ExtensionPoint in the heap dump
     * 
     * @return int objectId
     */
    public int getObjectId()
    {
        return objectId;
    }

    /**
     * Get extension point's unique identifier. This extension point is assigned
     * a unique identifier by the OSGi framework.
     * 
     * @return Integer extension's unique identifier
     */
    public Integer getExtensionPointId()
    {
        return extensionPointId;
    }

    public String getName()
    {
        return properties[QUALIFIED_NAME];
    }

    /**
     * Get unique id of the bundle contributing this extension point
     * 
     * @return String unique id of the bundle contributing this extension point
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
     * Get descriptor of the bundle contributing this extension point
     * 
     * @return BundleDescriptor of the bundle contributing this extension point
     */
    public BundleDescriptor getContributedBy()
    {
        return contributedBy;
    }

    public String getLabel()
    {
        return properties[LABEL];
    }

    /**
     * @return List<Extension> list of extensions of this extension point
     */
    public List<Extension> getExtensions()
    {
        return extensions;
    }

    public void addExtension(Extension extension)
    {
        if (!extensions.contains(extension))
            extensions.add(extension);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((contributedBy == null) ? 0 : contributedBy.hashCode());
        result = prime * result + ((extensionPointId == null) ? 0 : extensionPointId.hashCode());
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
        ExtensionPoint other = (ExtensionPoint) obj;
        if (contributedBy == null)
        {
            if (other.contributedBy != null)
                return false;
        }
        else if (!contributedBy.equals(other.contributedBy))
            return false;
        if (extensionPointId == null)
        {
            if (other.extensionPointId != null)
                return false;
        }
        else if (!extensionPointId.equals(other.extensionPointId))
            return false;
        return true;
    }

}
