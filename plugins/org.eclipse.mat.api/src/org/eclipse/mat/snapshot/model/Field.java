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
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;

/**
 * Describes a member variable, i.e. name, signature and value.
 */
public final class Field extends FieldDescriptor implements Serializable
{
    private static final long serialVersionUID = 2L;

    protected Object value;

    /**
     * Create a representation of member variable
     * @param name the name of the field
     * @param type the type {@link IObject.Type}
     * @param value
     * value is one of 
     * ObjectReference - for an object field
     * Byte - for a byte field
     * Short - for a short field
     * Integer - for an int field
     * Long - for a long field
     * Boolean - for a boolean field
     * Char - for a char field
     * Float - for a float field
     * Double - for a double field
     */
    public Field(String name, int type, Object value)
    {
        super(name, type);
        this.value = value;
    }

    /**
     * Gets the value of the field.
     * @return
     * ObjectReference - for an object field
     * Byte - for a byte field
     * Short - for a short field
     * Integer - for an int field
     * Long - for a long field
     * Boolean - for a boolean field
     * Char - for a char field
     * Float - for a float field
     * Double - for a double field
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * Set the value of the field.
     * Normally the value should not be changed.
     * Currently this is used after deserializing static fields
     * to change the object reference to one having a link to the current snapshot.
     * @param object
     * ObjectReference - for an object field
     * Byte - for a byte field
     * Short - for a short field
     * Integer - for an int field
     * Long - for a long field
     * Boolean - for a boolean field
     * Char - for a char field
     * Float - for a float field
     * Double - for a double field
     */
    public void setValue(Object object)
    {
        value = object;
    }

    /**
     * A readable representation of the field.
     * Do not rely on the format of the result.
     * @return a description of this field.
     */
    public String toString()
    {
        return type + " " + name + ": \t" + value; //$NON-NLS-1$//$NON-NLS-2$
    }
}
