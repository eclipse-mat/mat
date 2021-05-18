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
 *    Andrew Johnson/IBM Corporation - enhancements and fixes
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
     * <dl>
     * <dt>{@link ObjectReference}</dt><dd>for an object field</dd>
     * <dt>{@link Byte}</dt><dd>for a byte field</dd>
     * <dt>{@link Short}</dt><dd>for a short field</dd>
     * <dt>{@link Integer}</dt><dd>for an int field</dd>
     * <dt>{@link Long}</dt><dd>for a long field</dd>
     * <dt>{@link Boolean}</dt><dd>for a boolean field</dd>
     * <dt>{@link Character}</dt><dd>for a char field</dd>
     * <dt>{@link Float}</dt><dd>for a float field</dd>
     * <dt>{@link Double}</dt><dd>for a double field</dd>
     * </dl>
     */
    public Field(String name, int type, Object value)
    {
        super(name, type);
        this.value = value;
    }

    /**
     * Gets the value of the field.
     * @return
     * <dl>
     * <dt>{@link ObjectReference}</dt><dd>for an object field</dd>
     * <dt>{@link Byte}</dt><dd>for a byte field</dd>
     * <dt>{@link Short}</dt><dd>for a short field</dd>
     * <dt>{@link Integer}</dt><dd>for an int field</dd>
     * <dt>{@link Long}</dt><dd>for a long field</dd>
     * <dt>{@link Boolean}</dt><dd>for a boolean field</dd>
     * <dt>{@link Character}</dt><dd>for a char field</dd>
     * <dt>{@link Float}</dt><dd>for a float field</dd>
     * <dt>{@link Double}</dt><dd>for a double field</dd>
     * </dl>
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
     * object is one of
     * <dl>
     * <dt>{@link ObjectReference}</dt><dd>for an object field</dd>
     * <dt>{@link Byte}</dt><dd>for a byte field</dd>
     * <dt>{@link Short}</dt><dd>for a short field</dd>
     * <dt>{@link Integer}</dt><dd>for an int field</dd>
     * <dt>{@link Long}</dt><dd>for a long field</dd>
     * <dt>{@link Boolean}</dt><dd>for a boolean field</dd>
     * <dt>{@link Character}</dt><dd>for a char field</dd>
     * <dt>{@link Float}</dt><dd>for a float field</dd>
     * <dt>{@link Double}</dt><dd>for a double field</dd>
     * </dl>
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
        return super.toString() + ": \t" + value; //$NON-NLS-1$
    }
}
