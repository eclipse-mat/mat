/*******************************************************************************
 * Copyright (c) 2011,2018 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
        }
        catch (LinkageError e)
        {
            // Running in batch mode with no jface
        }
    }

}
