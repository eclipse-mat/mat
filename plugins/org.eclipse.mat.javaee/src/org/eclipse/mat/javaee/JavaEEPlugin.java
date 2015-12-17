/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaEEPlugin extends Plugin implements BundleActivator {
    public static final String PLUGIN_ID = "org.eclipse.mat.javaee"; //$NON-NLS-1$

    private static JavaEEPlugin plugin;

    public JavaEEPlugin() {

    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static JavaEEPlugin getDefault() {
        return plugin;
    }

    public static void log(IStatus status) {
        getDefault().getLog().log(status);
    }

    public static void error(Throwable e) {
        error("Internal error", e);
    }

    public static void error(String message, Throwable e) {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
    }

    public static void error(String message) {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message));
    }

    public static void warning(String message) {
        log(new Status(IStatus.WARNING, PLUGIN_ID, message));
    }

    public static void warning(String message, Throwable e) {
        log(new Status(IStatus.WARNING, PLUGIN_ID, message, e));
    }

    public static void info(String message) {
        log(new Status(IStatus.INFO, PLUGIN_ID, message));

    }

    public static void log(int severity, String message) {
        log(new Status(severity, PLUGIN_ID, message));
    }
}
