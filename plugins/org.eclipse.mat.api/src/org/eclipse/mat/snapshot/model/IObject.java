/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;

/**
 * Base interface for all objects found in a snapshot. Other model interfaces
 * derive from this interface, e.g. for classes, plain objects, object arrays,
 * primitive arrays...
 * 
 * @noimplement
 */
public interface IObject extends Serializable
{
    /**
     * The type of the primitive array.
     */
    public interface Type
    {
        /** An object array/field */
        int OBJECT = 2;
        /** A array of booleans or boolean field */
        int BOOLEAN = 4;
        /** A array of char or char field */
        int CHAR = 5;
        /** A array of float or float field */
        int FLOAT = 6;
        /** A array of double or double field */
        int DOUBLE = 7;
        /** A array of byte or byte field */
        int BYTE = 8;
        /** A array of short or short field */
        int SHORT = 9;
        /** A array of int or int field */
        int INT = 10;
        /** A array of long or long field */
        int LONG = 11;
    }

    /**
     * Get id for the snapshot object. The id is not the address, but an
     * internally assigned number fitting into an <code>int</code> (this helps
     * reducing the memory footprint of the snapshot considerably - addresses
     * are only used for visualization purposes).
     * 
     * @return id for the snapshot object
     */
    public int getObjectId();

    /**
     * Get address for the snapshot object. This is the address at which the
     * object was stored in memory. Use the address only for visualization
     * purposes and try to use the id wherever possible as the snapshot API is
     * optimized to handle ids and not addresses. Addresses are bigger (
     * <code>long</code>), have no consecutive order (with gaps), and are not
     * used for hashing.
     * 
     * @return address for the snapshot object
     */
    public long getObjectAddress();

    /**
     * Get class snapshot object this object is an instance of.
     * 
     * @return class snapshot object this object is an instance of
     */
    public IClass getClazz();

    /**
     * Get used heap size of just this object.
     * 
     * @return used heap size of this object
     * @since 1.0
     */
    public long getUsedHeapSize();

    /**
     * Get retained heap size of this object.
     * 
     * @return retained heap size of this object (returns 0 if the dominator
     *         tree wasn't calculated for the corresponding snapshot)
     */
    public long getRetainedHeapSize();

    /**
     * Get technical name of this object which is something like class@address.
     * 
     * @return technical name of this object which is something like class@address
     */
    public String getTechnicalName();

    /**
     * Get class specific name of this object which depends on the availability
     * of the appropriate name resolver, e.g. for a String the value of the
     * char[].
     * 
     * @return class specific name of the given snapshot object or null if it
     *         can't be resolved
     */
    public String getClassSpecificName();

    /**
     * Get concatenation of {@link #getTechnicalName()} and
     * {@link #getClassSpecificName()}.
     * 
     * @return concatenation of {@link #getTechnicalName()} and
     *         {@link #getClassSpecificName()}
     */
    public String getDisplayName();

    /**
     * Get list of snapshot objects referenced from this snapshot object with
     * the name of the field over which it was referenced.
     * 
     * @return list of snapshot objects referenced from this snapshot object
     *         with the name of the field over which it was referenced
     */
    public List<NamedReference> getOutboundReferences();

    /**
     * Resolves and returns the value of a field specified by a dot notation. If
     * the field is a primitive type, the value the returns the corresponding
     * object wrapper, e.g. a java.lang.Boolean is returned for a field of type
     * boolean. If the field is an object reference, the corresponding IObject
     * is returned.
     * <p>
     * The field can be specified using the dot notation, i.e. object references
     * are followed and its fields are evaluated. If any of the object
     * references is null, null is returned.
     * 
     * @param field
     *            the field name in dot notation
     * @return the value of the field
     * @throws SnapshotException when there is a problem retrieving the field
     */
    public Object resolveValue(String field) throws SnapshotException;

    /**
     * Get {@link GCRootInfo} if the object is a garbage collection root or null
     * otherwise. An object may or may not be a garbage collection root, it may
     * even be one for multiple reasons (described in the {@link GCRootInfo}
     * object).
     * 
     * @return {@link GCRootInfo} if the object is a garbage collection root or
     *         null otherwise
     * @throws SnapshotException when there is a problem retrieving the information
     */
    public GCRootInfo[] getGCRootInfo() throws SnapshotException;

    /**
     * Returns the snapshot from which this object has been read.
     * 
     * @return the snapshot from which this object has been read.
     */
    public ISnapshot getSnapshot();
}
