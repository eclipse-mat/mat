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
package org.eclipse.mat.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.osgi.framework.BundleContext;

public class MATPlugin extends Plugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.api"; //$NON-NLS-1$

    private static MATPlugin plugin;

    private IExtensionTracker tracker;

    public MATPlugin()
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

    public static MATPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }

    public static void log(IStatus status)
    {
        getDefault().getLog().log(status);
    }

    public static void log(Throwable e)
    {
        log(e, Messages.MATPlugin_InternalError);
    }

    public static void log(Throwable e, String message)
    {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
    }

    public static void log(String message)
    {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message));
    }
    
    public static void log(int severity, String message)
    {
        log(new Status(severity, PLUGIN_ID, message));
    }

}
