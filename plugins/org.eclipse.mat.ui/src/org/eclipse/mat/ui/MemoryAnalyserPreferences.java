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
package org.eclipse.mat.ui;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mat.query.BytesDisplay;
import org.eclipse.ui.IStartup;

/**
 * We use this instead of a PreferenceInitializer because that only gets called
 * when the preference pane is opened. 
 * 
 * @see <a href="http://help.eclipse.org/index.jsp?topic=/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_startup.html">http://help.eclipse.org/index.jsp?topic=/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_startup.html</a>
 */
public class MemoryAnalyserPreferences implements IStartup
{
    /**
     * Called when Eclipse starts.
     */
    public void earlyStartup()
    {
        // We set the system property if there isn't an explicit system property
        // already and if a preference exists
        if (System.getProperty(BytesDisplay.PROPERTY_NAME) == null)
        {
            loadPreferenceValue();
        }
    }

    public static void loadPreferenceValue()
    {
        IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
        if (prefs != null)
        {
            String prefValue = Platform.getPreferencesService().getString(
                            MemoryAnalyserPlugin.getDefault().getBundle().getSymbolicName(),
                            BytesDisplay.PROPERTY_NAME, null, null);
            if (prefValue != null)
            {
                BytesDisplay.setCurrentValue(BytesDisplay.parse(prefValue));
            }
        }
    }
}
