/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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

import org.eclipse.mat.snapshot.model.IArray;

/**
 * The general implementation of any Java array object (primitive array, object array).
 * @noextend
 */
public abstract class AbstractArrayImpl extends AbstractObjectImpl implements IArray
{
    private static final long serialVersionUID = 2L;

    protected int length;

    private Object info;

    /**
     * Construct a general object, called from subclass.
     * @param objectId the index of the object
     * @param address the actual address
     * @param classInstance the type of the object
     * @param length the length of the array in elements
     */
    public AbstractArrayImpl(int objectId, long address, ClassImpl classInstance, int length)
    {
        super(objectId, address, classInstance);
        this.length = length;
    }

    /**
     * Gets the cached information about the contents of the array.
     * @return the cached data (parser specific).
     */
    public Object getInfo()
    {
        return info;
    }

    /**
     * Sets the cached information about the contents of the array.
     */
    public void setInfo(Object content)
    {
        this.info = content;
    }

    public int getLength()
    {
        return length;
    }

    /**
     * Sets the length in elements.
     * @param i the new length
     */
    public void setLength(int i)
    {
        length = i;
    }

    protected StringBuffer appendFields(StringBuffer buf)
    {
        return super.appendFields(buf).append(";length=").append(length);//$NON-NLS-1$
    }

    public String getTechnicalName()
    {
        StringBuilder builder = new StringBuilder(256);
        String name = getClazz().getName();

        int p = name.indexOf('[');
        if (p < 0)
            builder.append(name);
        else
            builder.append(name.subSequence(0, p + 1)).append(getLength()).append(name.substring(p + 1));

        builder.append(" @ 0x");//$NON-NLS-1$
        builder.append(Long.toHexString(getObjectAddress()));
        return builder.toString();
    }

}
