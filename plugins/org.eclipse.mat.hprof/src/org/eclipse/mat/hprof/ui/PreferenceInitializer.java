/*******************************************************************************
 * Copyright (c) 2011,2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.ui;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mat.hprof.HprofPlugin;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer
{

    public void initializeDefaultPreferences()
    {
        try
        {
            IPreferenceStore store = (IPreferenceStore)HprofPlugin.getDefault().getPreferenceStore();
            store.setDefault(HprofPreferences.STRICTNESS_PREF, HprofPreferences.DEFAULT_STRICTNESS.toString());
        }
        catch (LinkageError e)
        {
            // Running in batch mode with no jface
        }
    }

}
