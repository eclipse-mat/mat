/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;

/**
 * Implementation of a plain Java object.
 * This includes field information.
 * @noextend
 */
public class InstanceImpl extends AbstractObjectImpl implements IInstance
{
    private static final long serialVersionUID = 1L;

    private volatile List<Field> fields;
    private volatile Map<String, Field> name2field;

    /**
     * Construct a representation of plain java object in the snapshot.
     * @param objectId the object id
     * @param address the actual address
     * @param clazz the type of the object
     * @param fields the instance fields of the object (the static fields are held in the class)
     */
    public InstanceImpl(int objectId, long address, ClassImpl clazz, List<Field> fields)
    {
        super(objectId, address, clazz);
        this.fields = fields;
    }

    @Override
    public long getObjectAddress()
    {
        try
        {
            long address = super.getObjectAddress();

            if (address == Long.MIN_VALUE)
            {
                address = source.mapIdToAddress(getObjectId());
                setObjectAddress(address);
            }

            return address;
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getObjectId()
    {
        try
        {
            int objectId = super.getObjectId();

            if (objectId < 0)
            {
                objectId = source.mapAddressToId(getObjectAddress());
                setObjectId(objectId);
            }

            return objectId;
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<Field> getFields()
    {
        if (fields == null)
            readFully();

        return fields;
    }

    public Field getField(String name)
    {
        return internalGetField(name);
    }

    /**
     * Set the fields of this instance.
     * The order should match the order of {@link #getFields()}.
     */
    protected void setFields(List<Field> fields)
    {
        this.fields = fields;
    }

    /**
     * Fully build information about this object by getting all the field
     * data from the dump.
     */
    protected synchronized void readFully()
    {
        // test again after synchronization
        if (fields != null)
            return;

        try
        {
            int objectId = getObjectId();

            InstanceImpl fullCopy = (InstanceImpl) source.getHeapObjectReader().read(objectId, source);
            this.setObjectAddress(fullCopy.getObjectAddress());
            this.fields = fullCopy.fields;

        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getUsedHeapSize()
    {
        try
        {
            int objId;
            try
            {
                objId = getObjectId();
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                if (cause instanceof SnapshotException)
                    throw (SnapshotException) cause;
                else
                    throw e;
            }
            return getSnapshot().getHeapSize(objId);
        }
        catch (SnapshotException e)
        {
            return classInstance.getHeapSizePerInstance();
        }
    }

    public ArrayLong getReferences()
    {
        List<Field> fields = getFields();
        ArrayLong list = new ArrayLong(fields.size() + 1);

        list.add(classInstance.getObjectAddress());

        HashMapIntObject<HashMapIntObject<XGCRootInfo[]>> threadToLocalVars = source.getRootsPerThread();
        if (threadToLocalVars != null)
        {
            HashMapIntObject<XGCRootInfo[]> localVars = threadToLocalVars.get(getObjectId());
            if (localVars != null)
            {
                IteratorInt localsIds = localVars.keys();
                while (localsIds.hasNext())
                {
                    int localId = localsIds.next();
                    GCRootInfo[] rootInfo = localVars.get(localId);
                    list.add(rootInfo[0].getObjectAddress());
                }
            }
        }

        for (Field field : fields)
        {
            if (field.getValue() instanceof ObjectReference)
            {
                ObjectReference ref = (ObjectReference) field.getValue();
                list.add(ref.getObjectAddress());
            }
        }

        return list;
    }

    public List<NamedReference> getOutboundReferences()
    {
        List<NamedReference> list = new ArrayList<NamedReference>();

        list.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));//$NON-NLS-1$

        HashMapIntObject<HashMapIntObject<XGCRootInfo[]>> threadToLocalVars = source.getRootsPerThread();
        if (threadToLocalVars != null)
        {
            HashMapIntObject<XGCRootInfo[]> localVars = threadToLocalVars.get(getObjectId());
            if (localVars != null)
            {
                IteratorInt localsIds = localVars.keys();
                while (localsIds.hasNext())
                {
                    int localId = localsIds.next();
                    GCRootInfo[] rootInfo = localVars.get(localId);
                    ThreadToLocalReference ref = new ThreadToLocalReference(source, rootInfo[0].getObjectAddress(), "<"//$NON-NLS-1$
                                    + GCRootInfo.getTypeSetAsString(rootInfo) + ">", localId, rootInfo);//$NON-NLS-1$
                    list.add(ref);
                }
            }
        }

        for (Field field : getFields())
        {
            if (field.getValue() instanceof ObjectReference)
            {
                ObjectReference ref = (ObjectReference) field.getValue();
                list.add(new NamedReference(source, ref.getObjectAddress(), field.getName()));
            }
        }

        return list;
    }

    @Override
    protected Field internalGetField(String name)
    {
        if (name2field == null)
        {
            List<Field> fields = getFields();
            Map<String, Field> n2f = new HashMap<String, Field>(fields.size());
            for (Field f : fields)
            {
                n2f.put(f.getName(), f);
            }

            this.name2field = n2f;
        }

        return name2field.get(name);
    }

}
