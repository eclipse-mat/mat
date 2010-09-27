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
package org.eclipse.mat.jruby.internal;

import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.jruby.JRubyPlugin;
import org.eclipse.mat.jruby.ui.InstallJRubyWizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;

public class StartupHandler implements IStartup {

	public void earlyStartup() {
		if (JRubyPlugin.checkOnStartup() && !JRubyPlugin.isRubyInstalled()) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					InstallJRubyWizard wizard = new InstallJRubyWizard(true);
					Shell shell = Display.getDefault().getActiveShell();
					new WizardDialog(shell, wizard).open();
				}
			});
		}
	}
	
}
