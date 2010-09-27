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

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.mat.jruby.JRubyPlugin;
import org.eclipse.mat.jruby.operations.InstallJRubyBundleOperation;
import org.eclipse.mat.jruby.operations.ReinstallJRubyBundleOperation;


public class InstallJRubyWizard extends Wizard {
	
	private boolean onStartup;
	private InstallJRubyWizardModel model;
	private InstallJRubyWizardPage page;
	
	public InstallJRubyWizard() {
		this(false);
	}

	/**
	 * Constructor for SampleNewWizard.
	 */
	public InstallJRubyWizard(boolean onStartup) {
		super();
		setWindowTitle(Messages.InstallJRubyWizard_Title);
		setNeedsProgressMonitor(true);
		
		this.onStartup = onStartup;
		
		model = new InstallJRubyWizardModel();
	}
	
	/**
	 * Adding the page to the wizard.
	 */
	public void addPages() {
		page = new InstallJRubyWizardPage(model);
		addPage(page);
	}
	
	public boolean isOnStartup() {
		return onStartup;
	}

	@Override
	public boolean performCancel() {
		storeCheckOnStartupPreference();
		return super.performCancel();
	}

	/**
	 * This method is called when 'Finish' button is pressed in
	 * the wizard. We will create an operation and run it
	 * using wizard as execution context.
	 */
	public boolean performFinish() {
		storeCheckOnStartupPreference();
		
		String location = (String) model.getLocationValue().getValue();
		String url = new File(location).toURI().toString();
		
		IRunnableWithProgress operation = null;
		if (JRubyPlugin.isRubyInstalled()) {
			operation = new ReinstallJRubyBundleOperation(url);
		} else {
			operation = new InstallJRubyBundleOperation(url);
		}
		try {
			getContainer().run(false, false, operation);
		} catch (InvocationTargetException e) {
			JRubyPlugin.log(Messages.InstallJRubyWizard_InstallRubyBundleError, e);
		} catch (InterruptedException e) {
			// not possible - the operation is not interruptible
		}
		
		return true;
	}

	private void storeCheckOnStartupPreference() {
		if (onStartup) {
			boolean currentValue = JRubyPlugin.checkOnStartup();
			boolean newValue = (Boolean) model.getCheckOnStartupValue().getValue();
			if (newValue != currentValue) {
				IPreferenceStore store = JRubyPlugin.getDefault().getPreferenceStore();
				store.setValue(JRubyPlugin.CHECK_ON_STARTUP_PREFERENCE, newValue);
			}
		}
	}

}