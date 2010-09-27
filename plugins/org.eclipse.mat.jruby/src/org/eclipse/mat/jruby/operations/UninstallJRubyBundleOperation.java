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

import org.eclipse.mat.jruby.JRubyPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class UninstallJRubyBundleOperation extends AbstractJRubyBundleOperation {

	@Override
	protected void doExecute() throws BundleException {
		Bundle[] bundles = JRubyPlugin.getDefault().getBundle().getBundleContext().getBundles();
		for (Bundle b : bundles) {
			if (b.getSymbolicName().equals(JRubyPlugin.JRUBY_PLUGIN_ID)) {
				b.uninstall();
			}
		}
	}

	@Override
	protected String getTaskName() {
		return Messages.UninstallJRubyBundleOperation_UninstallJRubyBundle;
	}

	@Override
	protected String getSubTaskName() {
		return Messages.UninstallJRubyBundleOperation_Uninstalling;
	}

}
