/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.rcp;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class RCPPlugin extends AbstractUIPlugin
{

    public static final String PLUGIN_ID = "org.eclipse.mat.ui.rcp"; //$NON-NLS-1$

    private static RCPPlugin plugin;

    private IExtensionTracker tracker;

    public RCPPlugin()
    {}

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        tracker = new ExtensionTracker(Platform.getExtensionRegistry());
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;
        tracker.close();
        super.stop(context);
    }

    public static RCPPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }
}
