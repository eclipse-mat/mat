/*******************************************************************************
 * Copyright (c) 2011,2020 IBM Corporation.
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
            store.setDefault(HprofPreferences.ADDITIONAL_CLASS_REFERENCES, Boolean.FALSE);
            store.setDefault(HprofPreferences.DISCARD_ENABLE, Boolean.FALSE);
            store.setDefault(HprofPreferences.DISCARD_RATIO, 0.0);
            store.setDefault(HprofPreferences.DISCARD_PATTERN, "char\\[\\]|java\\.lang\\.String"); //$NON-NLS-1$
            store.setDefault(HprofPreferences.DISCARD_SEED, 1L);
            store.setDefault(HprofPreferences.DISCARD_OFFSET, 0.0);
        }
        catch (LinkageError e)
        {
            // Running in batch mode with no jface
        }
    }

}
