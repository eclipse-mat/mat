/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
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
     */
    private IPreferenceStore preferenceStore;

    /**
     * Lazily load and return the preference store.
     * @return Current preference store.
     */
    public IPreferenceStore getPreferenceStore()
    {
        // Avoid hard dependency on org.eclipse.ui
        // Create the preference store lazily.
        if (preferenceStore == null)
        {
            preferenceStore = new ScopedPreferenceStore(new InstanceScope(), getBundle().getSymbolicName());
        }
        return preferenceStore;
    }
}
