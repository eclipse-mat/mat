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
import org.osgi.framework.BundleException;

public class InstallJRubyBundleOperation extends AbstractJRubyBundleOperation {
	
	private String url;
	
	public InstallJRubyBundleOperation(String url) {
		this.url = url;
	}

	@Override
	protected void doExecute() throws BundleException {
		JRubyPlugin.getDefault().getBundle().getBundleContext().installBundle(url);
	}

	@Override
	protected String getTaskName() {
		return Messages.InstallJRubyBundleOperation_InstallJRubyBundle;
	}

	@Override
	protected String getSubTaskName() {
		return Messages.InstallJRubyBundleOperation_Installing;
	}

}
