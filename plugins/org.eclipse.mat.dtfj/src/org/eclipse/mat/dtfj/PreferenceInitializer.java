/*******************************************************************************
 * Copyright (c) 2011,2013 IBM Corporation.
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
package org.eclipse.mat.dtfj;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer
{

    /*
     * (non-Javadoc)
     * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#
     * initializeDefaultPreferences()
     */
    public void initializeDefaultPreferences()
    {
        try
        {
            IPreferenceStore store = (IPreferenceStore)InitDTFJ.getDefault().getPreferenceStore();
            store.setDefault(PreferenceConstants.P_METHODS, PreferenceConstants.NO_METHODS_AS_CLASSES);
            store.setDefault(PreferenceConstants.P_SUPPRESS_CLASS_NATIVE_SIZES, false);
            store.setDefault(PreferenceConstants.P_RELIABILITY_CHECK, PreferenceConstants.RELIABILITY_FATAL);
        }
        catch (LinkageError e)
        {
            // Running in batch mode with no jface
        }
    }

}
