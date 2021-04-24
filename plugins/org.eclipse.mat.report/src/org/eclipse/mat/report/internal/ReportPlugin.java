/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
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
package org.eclipse.mat.report.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.osgi.framework.BundleContext;

public class ReportPlugin extends Plugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.report"; //$NON-NLS-1$

    private static ReportPlugin plugin;

    private IExtensionTracker tracker;

    public ReportPlugin()
    {}

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        tracker = new ExtensionTracker(Platform.getExtensionRegistry());
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception
    {
        for (Runnable r : stop)
        {
            try
            {
                r.run();
            }
            catch (RuntimeException e)
            {
                log(e);
            }
        }
        stop.clear();
        plugin = null;
        tracker.close();
        super.stop(context);
    }

    public static ReportPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }

    public static void log(IStatus status)
    {
        ReportPlugin default1 = getDefault();
        if (default1 != null)
            default1.getLog().log(status);
        else
        {
            Level l;
            switch (status.getSeverity())
            {
                case IStatus.INFO:
                case IStatus.OK:
                    l = Level.INFO;
                    break;
                case IStatus.ERROR:
                    l = Level.SEVERE;
                    break;
                default:
                    l = Level.WARNING;
                    break;
            }
            Logger.getLogger(ReportPlugin.class.getName()).log(l, status.getMessage(), status.getException());
        }
    }

    public static void log(Throwable e)
    {
        log(e, Messages.ReportPlugin_InternalError);
    }

    public static void log(Throwable e, String message)
    {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
    }

    public static void log(int status, String message)
    {
        log(new Status(status, PLUGIN_ID, message));
    }

    static List<Runnable>stop = new ArrayList<Runnable>();
    public static void onStop(Runnable r)
    {
        synchronized(stop)
        {
            if (!stop.contains(r))
                stop.add(r);
        }
    }
}
