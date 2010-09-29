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

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.databinding.wizard.WizardPageSupport;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.jruby.JRubyPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class InstallJRubyWizardPage extends WizardPage {
	
	private InstallJRubyWizardModel model;

	/**
	 * Constructor for SampleNewWizardPage.
	 * @param model 
	 * 
	 * @param pageName
	 */
	public InstallJRubyWizardPage(InstallJRubyWizardModel model) {
		super("wizardPage"); //$NON-NLS-1$
		setTitle(Messages.InstallJRubyWizardPage_Title);
		setDescription(Messages.InstallJRubyWizardPage_Description);
		
		this.model = model;
	}

	/**
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		DataBindingContext dbc = new DataBindingContext();
		WizardPageSupport.create(this, dbc);
		
		Composite container = new Composite(parent, SWT.NONE);
		
		Label locationLabel = new Label(container, SWT.NONE);
		locationLabel.setText(Messages.InstallJRubyWizardPage_FileLocation);
		
		final Text locationText = new Text(container, SWT.BORDER | SWT.SINGLE);
		locationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		dbc.bindValue(
				SWTObservables.observeText(locationText, SWT.Modify), 
				model.getLocationValue(), 
				new UpdateValueStrategy().setAfterConvertValidator(new InstallJRubyWizardModel.LocationValidator()), 
				null);
		
		final Button browseButton = new Button(container, SWT.PUSH);
		browseButton.setText(Messages.InstallJRubyWizardPage_Browse);
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(browseButton.getShell(), SWT.SHEET);
				dialog.setText(Messages.InstallJRubyWizardPage_FileDialogTitle);
				dialog.setFilterExtensions(new String[] { "*.jar" }); //$NON-NLS-1$
				String location = dialog.open();
				if (location != null) {
					locationText.setText(location);
				}
			}
		});
		
		final Label helpText = new Label(container, SWT.NONE);
		helpText.setText(Messages.InstallJRubyWizardPage_HelpMessage);
		helpText.setLayoutData(new GridData(SWT.WRAP, SWT.BOTTOM, true, true, 2, 1));
		
		if (isOnStartup()) {
			Button checkOnStartupCheckbox = new Button(container, SWT.CHECK);
			checkOnStartupCheckbox.setText(Messages.JRubyPreferencePage_CheckOnStartup);
			checkOnStartupCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, true, 2, 1));
			checkOnStartupCheckbox.setSelection(JRubyPlugin.checkOnStartup());
			dbc.bindValue(
					SWTObservables.observeSelection(checkOnStartupCheckbox), 
					model.getCheckOnStartupValue(), 
					null, 
					null);
		}

		GridLayoutFactory.swtDefaults().numColumns(3).generateLayout(container);
	    setControl(container);
		Dialog.applyDialogFont(container);
//		PlatformUI.getWorkbench().getHelpSystem().setHelp(container, "org.eclipse.mat.jruby.installJRuby"); //$NON-NLS-1$
	}

	private boolean isOnStartup() {
		IWizard wizard = getWizard();
		if (wizard instanceof InstallJRubyWizard) {
			InstallJRubyWizard jrubyWizard = (InstallJRubyWizard) wizard;
			return jrubyWizard.isOnStartup();
		}
		return false;
	}
	
}