/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/

package org.eclipse.mat.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IconLabels
{
    public static final String ICON_KEY_PREFIX = "IconLabel"; //$NON-NLS-1$
    public static final String UNKNOWN_ICON_KEY = ICON_KEY_PREFIX + "Unknown"; //$NON-NLS-1$

    // Avoid making the same name as a class file
    private static final String BUNDLE_NAME = "org.eclipse.mat.ui.icon_labels"; //$NON-NLS-1$
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private IconLabels()
    {}

    public static String getString(String key)
    {
        try
        {
            return RESOURCE_BUNDLE.getString(key);
        }
        catch (MissingResourceException e)
        {
        	Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.WARNING, "Missing key: "+key, e); //$NON-NLS-1$
            return RESOURCE_BUNDLE.getString(UNKNOWN_ICON_KEY); // Should exist.
        }
    }
}
