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

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	
	private static final String BUNDLE_NAME = "org.eclipse.mat.jruby.operations.messages"; //$NON-NLS-1$
	
	public static String InstallJRubyBundleOperation_InstallJRubyBundle;
	public static String ReinstallJRubyBundleOperation_ReinstallJRubyBundle;
	public static String UninstallJRubyBundleOperation_UninstallJRubyBundle;
	public static String InstallJRubyBundleOperation_Installing;
	public static String UninstallJRubyBundleOperation_Uninstalling;
	public static String AbstractJRubyBundleOperation_RefreshingPackages;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
	
}
