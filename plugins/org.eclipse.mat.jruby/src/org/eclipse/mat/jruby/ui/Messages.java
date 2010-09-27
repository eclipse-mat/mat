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
package org.eclipse.mat.jruby.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	
	private static final String BUNDLE_NAME = "org.eclipse.mat.jruby.ui.messages"; //$NON-NLS-1$
	
	public static String InstallJRubyWizard_Title;
	public static String InstallJRubyWizard_InstallRubyBundleError;

	public static String InstallJRubyWizardPage_Title;
	public static String InstallJRubyWizardPage_Description;
	public static String InstallJRubyWizardPage_FileLocation;
	public static String InstallJRubyWizardPage_Browse;
	public static String InstallJRubyWizardPage_FileDialogTitle;
	public static String InstallJRubyWizardPage_EmptyLocationError;
	public static String InstallJRubyWizardPage_NotFoundError;
	public static String InstallJRubyWizardPage_InvalidJarError;
	public static String InstallJRubyWizardPage_NoManifestError;
	public static String InstallJRubyWizardPage_InvalidJRubyJarError;

	public static String JRubyPreferencePage_Description;
	public static String JRubyPreferencePage_Status;
	public static String JRubyPreferencePage_Install;
	public static String JRubyPreferencePage_Reinstall;
	public static String JRubyPreferencePage_Uninstall;

	public static String JRubyPreferencePage_CheckOnStartup;
	public static String JRubyPreferencePage_JRubyNotInstalled;
	public static String JRubyPreferencePage_JRubyInstalled;
	public static String JRubyPreferencePage_JRubyInstalledButCannotIdentify;
	public static String JRubyPreferencePage_ConfirmUninstallTitle;
	public static String JRubyPreferencePage_ConfirmUninstallDescription;
	public static String JRubyPreferencePage_UninstallJRubyBundleError;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
	
}
