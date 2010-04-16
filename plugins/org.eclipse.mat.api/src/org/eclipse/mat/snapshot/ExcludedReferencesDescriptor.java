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
package org.eclipse.mat.snapshot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A way of describing which references should not be followed when calculating retained sets 
 * and other queries involving paths.
 */
public final class ExcludedReferencesDescriptor
{
    private int[] objectIds;
    private Set<String> fields;

    /**
     * Constructor based on objects and fields.
     * Excluded reference if the reference is from one of these objects going through the named fields.
     * @param objectIds don't go through these objects
     * @param fields then though these fields. null means all fields.
     */
    public ExcludedReferencesDescriptor(int[] objectIds, Set<String> fields)
    {
        this.fields = fields;
        this.objectIds = objectIds;
        Arrays.sort(this.objectIds);
    }

    public ExcludedReferencesDescriptor(int[] objectIds, String... fields)
    {
        this(objectIds, new HashSet<String>(Arrays.asList(fields)));
    }

    /**
     * The excluded fields
     * @return a set of field names
     */
    public Set<String> getFields()
    {
        return fields;
    }

    /**
     * See if this object is excluded.
     * @param objectId
     * @return true if excluded
     */
    public boolean contains(int objectId)
    {
        return Arrays.binarySearch(objectIds, objectId) >= 0;
    }

    /**
     * All the excluded object ids.
     * @return an array of object ids.
     */
    public int[] getObjectIds()
    {
        return objectIds;
    }
}
