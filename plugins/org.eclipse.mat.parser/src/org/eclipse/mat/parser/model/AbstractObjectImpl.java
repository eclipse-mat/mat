/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.model;

import java.io.Serializable;
import java.util.Comparator;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.SnapshotImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.registry.ClassSpecificNameResolverRegistry;
import org.eclipse.mat.util.MessageUtil;

/**
 * The general implementation of any Java object (plain object, array, class, classloader).
 * @noextend
 */
public abstract class AbstractObjectImpl implements IObject, Serializable
{
    private static final long serialVersionUID = 2451875423035843852L;

    protected transient SnapshotImpl source;
    protected ClassImpl classInstance;
    private long address;
    private int objectId;

    /**
     * Construct a general object, called from subclass.
     * @param objectId the index of the object
     * @param address the actual address
     * @param classInstance the type of the object
     */
    public AbstractObjectImpl(int objectId, long address, ClassImpl classInstance)
    {
        this.objectId = objectId;
        this.address = address;
        this.classInstance = classInstance;
    }

    public long getObjectAddress()
    {
        return address;
    }

    public int getObjectId()
    {
        return objectId;
    }

    /**
     * Used to set the address, for example after reconstructing an object from a file.
     * @param address
     */
    public void setObjectAddress(long address)
    {
        this.address = address;
    }

    /**
     * Set the index for the object
     * @param objectId the index into all the indexes for other object data
     */
    public void setObjectId(int objectId)
    {
        this.objectId = objectId;
    }

    public ClassImpl getClazz()
    {
        return classInstance;
    }

    /**
     * Returns the address of the class which is the type of this object. 
     * @return the address
     */
    public long getClassAddress()
    {
        return classInstance.getObjectAddress();
    }

    /**
     * Returns the id of the class which is the type of this object. 
     * @return the id
     */
    public int getClassId()
    {
        return classInstance.getObjectId();
    }

    /**
     * Changes the type of the object.
     * Used when constructing a ClassImpl for java.lang.Class.
     * @param classInstance
     */
    public void setClassInstance(ClassImpl classInstance)
    {
        this.classInstance = classInstance;
    }

    /**
     * Set the snapshot for an object.
     * Used once the entire snapshot has been built, or an object has been deserialized.
     * @param dump the actual current snapshot
     */
    public void setSnapshot(ISnapshot dump)
    {
        this.source = (SnapshotImpl) dump;
    }

    public ISnapshot getSnapshot()
    {
        return this.source;
    }

    abstract public long getUsedHeapSize();

    public long getRetainedHeapSize()
    {
        try
        {
            return source.getRetainedHeapSize(getObjectId());
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the outbound references from this object, as addresses.
     * @return a list of outbound references
     */
    public abstract ArrayLong getReferences();

    @Override
    public String toString()
    {
        StringBuffer s = new StringBuffer(256);
        s.append(this.getClazz().getName());
        s.append(" [");//$NON-NLS-1$
        appendFields(s);
        s.append("]");//$NON-NLS-1$
        return s.toString();
    }

    /**
     * Construct text information about this object.
     * @param buf
     * @return
     */
    protected StringBuffer appendFields(StringBuffer buf)
    {
        return buf.append("id=0x").append(Long.toHexString(getObjectAddress()));//$NON-NLS-1$
    }

    public String getClassSpecificName()
    {
        return ClassSpecificNameResolverRegistry.resolve(this);
    }

    public String getTechnicalName()
    {
        StringBuilder builder = new StringBuilder(256);
        builder.append(getClazz().getName());
        builder.append(" @ 0x");//$NON-NLS-1$
        builder.append(Long.toHexString(getObjectAddress()));
        return builder.toString();
    }

    public String getDisplayName()
    {
        String label = getClassSpecificName();
        if (label == null)
            return getTechnicalName();
        else
        {
            StringBuilder s = new StringBuilder(256).append(getTechnicalName()).append("  "); //$NON-NLS-1$
            if (label.length() <= 256)
            {
                s.append(label);
            }
            else
            {
                s.append(label.substring(0, 256));
                s.append("...");//$NON-NLS-1$
            }
            return s.toString();
        }
    }

    // If the name is in the form <FIELD>{.<FIELD>}
    // the fields are transiently followed
    public final Object resolveValue(String name) throws SnapshotException
    {
        int p = name.indexOf('.');
        String n = p < 0 ? name : name.substring(0, p);
        Field f = internalGetField(n);
        if (f == null || f.getValue() == null)
            return null;
        if (p < 0)
        {
            Object answer = f.getValue();
            if (answer instanceof ObjectReference)
                answer = ((ObjectReference) answer).getObject();
            return answer;
        }

        if (!(f.getValue() instanceof ObjectReference))
        {
            String msg = MessageUtil.format(Messages.AbstractObjectImpl_Error_FieldIsNotReference, new Object[] { n,
                            getTechnicalName(), name.substring(p + 1) });
            throw new SnapshotException(msg);
        }

        ObjectReference ref = (ObjectReference) f.getValue();
        if (ref == null)
            return null;

        int objectId = ref.getObjectId();
        if (objectId < 0)
        {
            String msg = MessageUtil.format(Messages.AbstractObjectImpl_Error_FieldContainsIllegalReference,
                            new Object[] { n, getTechnicalName(), Long.toHexString(ref.getObjectAddress()) });
            throw new SnapshotException(msg);
        }

        return this.source.getObject(objectId).resolveValue(name.substring(p + 1));
    }

    /**
     * Find the field of this object based on the name
     * @param name the name of the field
     * @return the field, containing the value
     */
    protected abstract Field internalGetField(String name);

    public GCRootInfo[] getGCRootInfo() throws SnapshotException
    {
        return source.getGCRootInfo(getObjectId());
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof IObject && this.objectId == ((IObject) obj).getObjectId();
    }

    @Override
    public int hashCode()
    {
        return this.objectId;
    }

    /**
     * Gets a comparator for sorting objects by technical name - type plus address.
     * Appears to be unused, and currently only returns null, so do not use.
     * @return null
     */
    @Deprecated
    public static Comparator<AbstractObjectImpl> getComparatorForTechnicalName()
    {
        return null;
    }

    /**
     * Gets a comparator for sorting objects by resolved name description.
     * Appears to be unused, and currently only returns null, so do not use.
     * @return null
     */
    @Deprecated
    public static Comparator<AbstractObjectImpl> getComparatorForClassSpecificName()
    {
        return null;
    }

    /**
     * Gets a comparator for sorting objects by used heap size.
     * Appears to be unused, and currently only returns null, so do not use.
     * @return null
     */
    @Deprecated
    public static Comparator<AbstractObjectImpl> getComparatorForUsedHeapSize()
    {
        return null;
    }

    // //////////////////////////////////////////////////////////////
    // internal helpers
    // //////////////////////////////////////////////////////////////

    /**
     * Helper for the net size calculation.
     * @param actual size
     * @return rounded up size
     */
    protected static long alignUpTo8(long n)
    {
        return n % 8 == 0 ? n : n + 8 - n % 8;
    }

}
