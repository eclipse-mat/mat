/*******************************************************************************
 * Copyright (c) 2014 IBM
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM
 *******************************************************************************/
package org.eclipse.mat.query;

/**
 * This enumeration specifies how to display a number of bytes. It can be
 * configured using -Dbytes_display=(bytes|kilobytes|megabytes|gigabytes|smart)
 * or through the Eclipse preferences dialog.
 * 
 * @since 1.5
 */
public enum BytesDisplay
{
    /**
     * Units of bytes (8 bits).
     */
    Bytes,

    /**
     * Units of kilobytes (1,024 bytes).
     */
    Kilobytes,

    /**
     * Unites of megabytes (1,048,576 bytes).
     */
    Megabytes,

    /**
     * Units of gigabytes (1,073,741,824 bytes).
     */
    Gigabytes,

    /**
     * If the value is a gigabyte or more, display in gigabytes; similarly for
     * megabytes and kilobytes; otherwise, display in bytes.
     */
    Smart;

    /**
     * Default bytes display format.
     */
    public static final BytesDisplay DEFAULT = Bytes;

    /**
     * System property name to specify a bytes display.
     */
    public static final String PROPERTY_NAME = "bytes_display";

    /**
     * We store the preference value statically to avoid constantly looking it
     * up.
     */
    private static BytesDisplay currentValue;

    static
    {
        currentValue = parse(System.getProperty(PROPERTY_NAME));
    }

    /**
     * Given a stored preference value, return the enumeration value, or
     * otherwise the default.
     * 
     * @param value
     *            The preference value.
     * @return Given a stored preference value, return the enumeration value, or
     *         otherwise the default.
     */
    public static BytesDisplay parse(String value)
    {
        if (value != null && value.length() > 0)
        {
            for (BytesDisplay v : values())
            {
                if (v.toString().equalsIgnoreCase(value)) { return v; }
            }
        }
        return DEFAULT;
    }

    /**
     * Return the currently selected preference from the system properties.
     * 
     * @return Current preference or reflection of command line setting.
     */
    public static BytesDisplay getCurrentValue()
    {
        return currentValue;
    }

    /**
     * Uses system properties to set the current value.
     * 
     * @param val The new value.
     */
    public static void setCurrentValue(BytesDisplay val)
    {
        System.setProperty(PROPERTY_NAME, val.toString());
        currentValue = val;
    }
}
