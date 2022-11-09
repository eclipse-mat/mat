/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - lookup by address
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;
import java.util.Objects;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;

/**
 * The value of a field if it is an object reference.
 * Also can be used to retrieve an IObject from its address
 * even if the object has not been indexed provided that the 
 * snapshot parser supports the reading of unindexed or
 * discarded objects.
 */
public class ObjectReference implements Serializable
{
    private static final long serialVersionUID = 1L;

    private transient ISnapshot snapshot;
    private long address;

    /**
     * Create a reference to an object based on its address but in a form
     * where the object id can be found.
     * @param snapshot the snapshot
     * @param address the address of the object
     */
    public ObjectReference(ISnapshot snapshot, long address)
    {
        this.snapshot = snapshot;
        this.address = address;
    }

    /**
     * The actual address of the object
     * @return the address
     */
    public long getObjectAddress()
    {
        return address;
    }

    /**
     * The id of the object
     * @return the object id
     * @throws SnapshotException if the object has not been
     * indexed, for example if it is unreachable or has been
     * discarded.
     */
    public int getObjectId() throws SnapshotException
    {
        return snapshot.mapAddressToId(address);
    }

    /**
     * Get a detailed view of the object.
     * Can be used to find an unindexed object by its addresss
     * if the snapshot parser supports that.
     * @return the object detail
     * @throws SnapshotException if there is a problem retrieving the object
     */
    public IObject getObject() throws SnapshotException
    {
        int objectId;
        try
        {
            objectId = getObjectId();
        }
        catch (SnapshotException e)
        {
            ObjectReference proxy = snapshot.getSnapshotAddons(ObjectReference.class);
            if (proxy != null)
            {
                proxy.address = getObjectAddress();
                return proxy.getObject();
            }
            else
            {
                throw e;
            }
        }
        return snapshot.getObject(objectId);
    }

    /**
     * A simple view of the object as an address
     * @return the object address as a hexadecimal number.
     */
    public String toString()
    {
        return "0x" + Long.toHexString(address); //$NON-NLS-1$
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(address);
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
        ObjectReference other = (ObjectReference) obj;
        return address == other.address;
    }
}
