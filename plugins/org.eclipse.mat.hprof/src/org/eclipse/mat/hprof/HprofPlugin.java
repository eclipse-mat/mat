/*******************************************************************************
 * Copyright (c) 2012,2013 IBM Corporation
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
package org.eclipse.mat.hprof;

import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class HprofPlugin extends Plugin implements BundleActivator
{
    private static HprofPlugin plugin;

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;
        super.stop(context);
    }

    public static HprofPlugin getDefault()
    {
        return plugin;
    }

    /**
     * Storage for preferences.
     * Use Object instead of IPreferenceStore to avoid a hard dependency on org.eclipse.jface
     */
    private Object preferenceStore;

    /**
     * Lazily load and return the preference store.
     * @return Current preference store.
     */

    public Object getPreferenceStore()
    {
        // Avoid hard dependency on org.eclipse.ui
        // Create the preference store lazily.
        if (preferenceStore == null)
        {
            try
            {
                preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, getBundle().getSymbolicName());
            }
            catch (LinkageError e)
            {
                preferenceStore = new PreferenceStore();
            }
        }
        return preferenceStore;
    }
}
