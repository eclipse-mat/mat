/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Kaloyan Raev - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.jruby;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public class JRubyPlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "org.eclipse.mat.jruby"; //$NON-NLS-1$
	public static final String RESOLVER_PLUGIN_ID = "org.eclipse.mat.jruby.resolver"; //$NON-NLS-1$
	public static final String JRUBY_PLUGIN_ID = "org.jruby.jruby"; //$NON-NLS-1$
	
	public static final String RUBY_CLASS = "org.jruby.Ruby"; //$NON-NLS-1$
	
	public static final String CHECK_ON_STARTUP_PREFERENCE = "checkOnStartup";  //$NON-NLS-1$

	// The shared instance
	private static JRubyPlugin plugin;
	
	private static ServiceTracker bundleTracker = null;
	
	/**
	 * The constructor
	 */
	public JRubyPlugin() {
		plugin = this;
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static JRubyPlugin getDefault() {
		return plugin;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public synchronized void start(BundleContext context) throws Exception {
		super.start(context);
		
		bundleTracker = new ServiceTracker(context, PackageAdmin.class.getName(), null);
		bundleTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public synchronized void stop(BundleContext context) throws Exception {
		if (bundleTracker != null) {
			bundleTracker.close();
			bundleTracker = null;
		}
		
		super.stop(context);
	}
	
	public static void log(String message) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message));
	}
	
	public static void log(String message, Throwable throwable) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, throwable));
	}
	
    public static boolean isRubyInstalled() {
    	try {
			JRubyPlugin.getResolverBundle().loadClass(RUBY_CLASS);
		} catch (ClassNotFoundException e) {
			return false;
		}
    	return true;
    }

	public static boolean checkOnStartup() {
		return getDefault().getPreferenceStore().getBoolean(CHECK_ON_STARTUP_PREFERENCE);
	}
    
    public static Bundle getResolverBundle() {
    	Bundle[] bundles = getDefault().getBundle().getBundleContext().getBundles();
    	for (Bundle b : bundles) {
    		if (b.getSymbolicName().equals(RESOLVER_PLUGIN_ID)) {
    			return b;
    		}
    	}
    	return null;
    }
	
	public static PackageAdmin getPackageAdmin() {
		if (bundleTracker == null) {
			log(Messages.JRubyPlugin_BundleNoActivatedError);
			return null;
		}
		return (PackageAdmin) bundleTracker.getService();
	}

}
