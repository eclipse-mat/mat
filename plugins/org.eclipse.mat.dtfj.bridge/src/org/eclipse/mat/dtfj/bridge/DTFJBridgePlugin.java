/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj.bridge;

import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

/**
 * Controls the loading of this plugin
 */
public class DTFJBridgePlugin extends Plugin
{
    private static DTFJBridgePlugin plugin;

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

    public void log(int level, String msg, Throwable e)
    {
        getLog().log(new Status(level, getBundle().getSymbolicName(), msg, e));
    }

    static DTFJBridgePlugin getDefault()
    {
        return plugin;
    }
}
