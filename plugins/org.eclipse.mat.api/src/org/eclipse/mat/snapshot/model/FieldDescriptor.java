/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;

/**
 * Describes a field of an object, i.e. its name and signature.
 */
public class FieldDescriptor implements Serializable
{
    private static final long serialVersionUID = 2L;

    protected String name;
    protected int type;

    /**
     * Create a field for a class - just contains the field name and type,
     * not the value
     * @param name field name
     * @param type field type from {@link IObject.Type}
     */
    public FieldDescriptor(String name, int type)
    {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the field name
     * @return the actual field name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets the type as a number.
     * @return as {@link IObject.Type}
     */
    public int getType()
    {
        return type;
    }

    /**
     * Sets the name of the field.
     * Normally the name should not be changed.
     * @param name the name of the field.
     * @noreference
     */
    public void setName(String name)
    {
        this.name = name;
    }


    /**
     * Sets the type of the field.
     * Normally the type should not be changed.
     * @param type  the type of the field as {@link IObject.Type}
     * @noreference
     */
    public void setType(int type)
    {
        this.type = type;
    }

    /**
     * Returns the type of the field.
     * Used for example by the object inspector pane.
     * @return
     * ref
     * byte
     * short
     * int
     * long
     * boolean
     * char
     * float
     * double
     */
    public String getVerboseSignature()
    {
        if (type == IObject.Type.OBJECT)
            return "ref"; //$NON-NLS-1$

        String t = IPrimitiveArray.TYPE[type];
        return t.substring(0, t.length() - 2);
    }

    /**
     * A readable representation of the field descriptor.
     * Do not rely on the format of the result.
     * @return a description of this field descriptor.
     */
    public String toString()
    {
        return getVerboseSignature() + " " + name; //$NON-NLS-1$
    }
}
