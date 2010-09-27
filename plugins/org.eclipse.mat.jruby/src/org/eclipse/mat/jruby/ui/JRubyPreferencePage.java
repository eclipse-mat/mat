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

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.jruby.JRubyPlugin;
import org.eclipse.mat.jruby.operations.UninstallJRubyBundleOperation;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class JRubyPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	
	private Label jrubyStatusLabel;
	private Button installButton;
	private Button uninstallButton;
	
	private Bundle bundle;
	private BundleContext bundleContext;
	
	private IObservableValue checkOnStartupValue;

	public JRubyPreferencePage() {
		setDescription(Messages.JRubyPreferencePage_Description);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
		bundle = JRubyPlugin.getDefault().getBundle();
		bundleContext = bundle.getBundleContext();
		checkOnStartupValue = new WritableValue(JRubyPlugin.checkOnStartup(), Boolean.class);
	}

	@Override
	protected Control createContents(Composite parent) {
		DataBindingContext dbc = new DataBindingContext();
//		PreferencePageSupport.create(this, dbc); // bug 300232
		
		Composite container = new Composite(parent, SWT.NONE);
		
		jrubyStatusLabel = new Label(container, SWT.NONE);
		jrubyStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		
		installButton = new Button(container, SWT.PUSH);
		installButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		installButton.setText(Messages.JRubyPreferencePage_Install);
		installButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				InstallJRubyWizard wizard = new InstallJRubyWizard();
				new WizardDialog(getShell(), wizard).open();
				updateLabels();
			}
		});
		
		uninstallButton = new Button(container, SWT.PUSH);
		uninstallButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		uninstallButton.setText(Messages.JRubyPreferencePage_Uninstall);
		uninstallButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean confirmed = MessageDialog.openQuestion(getShell(), 
						Messages.JRubyPreferencePage_ConfirmUninstallTitle, 
						Messages.JRubyPreferencePage_ConfirmUninstallDescription);
				
				if (confirmed) {
					IRunnableWithProgress operation = new UninstallJRubyBundleOperation();
					IProgressService service = PlatformUI.getWorkbench().getProgressService();
					try {
						service.run(false, false, operation);
					} catch (InvocationTargetException exc) {
						JRubyPlugin.log(Messages.JRubyPreferencePage_UninstallJRubyBundleError, exc);
					} catch (InterruptedException exc) {
						// not possible - the operation is not interruptible
					}
					
					updateLabels();
				}
			}
		});
		
		new Label(container, SWT.NONE); // separator
		
		Button checkOnStartupCheckbox = new Button(container, SWT.CHECK);
		checkOnStartupCheckbox.setText(Messages.JRubyPreferencePage_CheckOnStartup);
		checkOnStartupCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		dbc.bindValue(
				SWTObservables.observeSelection(checkOnStartupCheckbox), 
				checkOnStartupValue, 
				null, 
				null);
		
		updateLabels();
		
		GridLayoutFactory.swtDefaults().margins(0, 5).numColumns(2).generateLayout(container);
		return container;
	}
	
	@Override
	protected IPreferenceStore doGetPreferenceStore() {
		return JRubyPlugin.getDefault().getPreferenceStore();
	}

	@Override
	protected void performDefaults() {
		boolean defaultValue = getPreferenceStore().getDefaultBoolean(JRubyPlugin.CHECK_ON_STARTUP_PREFERENCE);
		checkOnStartupValue.setValue(defaultValue);
		
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		boolean value = (Boolean) checkOnStartupValue.getValue();
		getPreferenceStore().setValue(JRubyPlugin.CHECK_ON_STARTUP_PREFERENCE, value);
		
		return super.performOk();
	}

	private void updateLabels() {
		String statusText;
		String installButtonText;
		boolean enableUninstall;
		
		if (JRubyPlugin.isRubyInstalled()) {
			Bundle[] bundles = bundleContext.getBundles();
			Bundle jrubyBundle = null;
			for (Bundle b : bundles) {
				if (b.getSymbolicName().equals(JRubyPlugin.JRUBY_PLUGIN_ID)) {
					jrubyBundle = b;
					break;
				}
			}
			if (jrubyBundle == null) {
				statusText = Messages.JRubyPreferencePage_JRubyInstalledButCannotIdentify;
				installButtonText = Messages.JRubyPreferencePage_Install;
				enableUninstall = false;
			} else {
				statusText = NLS.bind(Messages.JRubyPreferencePage_JRubyInstalled, jrubyBundle.getVersion());
				installButtonText = Messages.JRubyPreferencePage_Reinstall;
				enableUninstall = true;
			}
		} else {
			statusText = Messages.JRubyPreferencePage_JRubyNotInstalled;
			installButtonText = Messages.JRubyPreferencePage_Install;
			enableUninstall = false;
		}
		
		jrubyStatusLabel.setText(NLS.bind(Messages.JRubyPreferencePage_Status, statusText));
		installButton.setText(installButtonText);
		uninstallButton.setEnabled(enableUninstall);
		
		installButton.getParent().pack();
	}

}