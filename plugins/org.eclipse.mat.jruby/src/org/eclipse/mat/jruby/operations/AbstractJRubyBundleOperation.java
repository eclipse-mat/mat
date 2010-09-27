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
package org.eclipse.mat.jruby.operations;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.mat.jruby.JRubyPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

public abstract class AbstractJRubyBundleOperation implements
		IRunnableWithProgress, FrameworkListener {

	private Object syncMonitor = new Object();
	private Throwable error;

	protected abstract void doExecute() throws BundleException;
	protected abstract String getTaskName();
	protected abstract String getSubTaskName();

	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		monitor.beginTask(getTaskName(), 2);
		
		JRubyPlugin.getDefault().getBundle().getBundleContext().addFrameworkListener(this);
		
		try {
			error = null;
			
			monitor.subTask(getSubTaskName());
			
			try {
				doExecute();
			} catch (BundleException e) {
				throw new InvocationTargetException(e);
			}

			monitor.worked(1);
			monitor.subTask(Messages.AbstractJRubyBundleOperation_RefreshingPackages);
			
			synchronized (syncMonitor) {
				checkForError();
				JRubyPlugin.getPackageAdmin().refreshPackages(new Bundle[] { JRubyPlugin.getResolverBundle() });
				syncMonitor.wait();
				checkForError();
			}

			monitor.worked(1);
		} finally {
			JRubyPlugin.getDefault().getBundle().getBundleContext().removeFrameworkListener(this);
		}
	}

	public void frameworkEvent(FrameworkEvent e) {
		if (e.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
			synchronized (syncMonitor) {
				syncMonitor.notify();
			}
		} else if (e.getType() == FrameworkEvent.ERROR && 
				e.getBundle().getSymbolicName().equals(JRubyPlugin.JRUBY_PLUGIN_ID)) {
			synchronized (syncMonitor) {
				error = e.getThrowable();
			}
		}
	}
	
	private void checkForError() throws InvocationTargetException {
		if (error != null) {
			throw new InvocationTargetException(error);
		}
	}

}
