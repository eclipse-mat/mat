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

public class BundleDescriptor
{
    private int bundleObjectId;
    private Long bundleId;
    private String bundleName;
    private Type type;
    private String state;

    public enum Type
    {
        BUNDLE, FRAGMENT
    }

    public BundleDescriptor(int objectId, Long bundleId, String bundleName, String state, Type type)
    {
        this.bundleObjectId = objectId;
        this.bundleId = bundleId;
        this.bundleName = bundleName;
        this.type = type;
        this.state = state;
    }

    public String getBundleName()
    {
        return bundleName;
    }

    /**
     * Get bundle's unique identifier. This bundle is assigned a unique
     * identifier by the Framework when it was installed in the OSGi
     * environment.
     * 
     * @return long bundle's unique identifier
     */
    public Long getBundleId()
    {
        return bundleId;
    }

    /**
     * Get objectId of the bundle in the heap dump
     * 
     * @return int objectId
     */
    public int getObjectId()
    {
        return bundleObjectId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bundleId == null) ? 0 : bundleId.hashCode());
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
        BundleDescriptor other = (BundleDescriptor) obj;
        if (bundleId == null)
        {
            if (other.bundleId != null)
                return false;
        }
        else if (!bundleId.equals(other.bundleId))
            return false;
        return true;
    }

    /**
     * Get bundle's type (fragment or bundle)
     * 
     * @return Type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Get bundle's state (installed, resolved, starting, active, stopping,
     * uninstalled)
     * 
     * @return String state
     */
    public String getState()
    {
        return state;
    }

}
