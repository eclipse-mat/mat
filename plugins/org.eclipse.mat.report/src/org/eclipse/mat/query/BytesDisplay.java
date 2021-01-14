/*******************************************************************************
 * Copyright (c) 2014, 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.query;

import org.eclipse.core.runtime.Platform;

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
     * Units of megabytes (1,048,576 bytes).
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
    public static final String PROPERTY_NAME = "bytes_display"; //$NON-NLS-1$

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

    static
    {
        String propValue = System.getProperty(BytesDisplay.PROPERTY_NAME);
        if (propValue == null)
        {
            // Eclipse preference set by UI plugin
            String MATUI_PLUGIN = "org.eclipse.mat.ui"; //$NON-NLS-1$
            String prefValue = Platform.getPreferencesService().getString(MATUI_PLUGIN, BytesDisplay.PROPERTY_NAME, null, null);
            BytesDisplay.setCurrentValue(BytesDisplay.parse(prefValue));
        }
        else
        {
            // Set the current value from the system property, or default if it doesn't parse
            BytesDisplay.setCurrentValue(BytesDisplay.parse(propValue));
        }
    }
}
