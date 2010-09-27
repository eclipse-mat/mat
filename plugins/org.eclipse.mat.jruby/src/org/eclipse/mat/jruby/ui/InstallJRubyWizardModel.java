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
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mat.jruby.JRubyPlugin;

public class InstallJRubyWizardModel {
	
	private IObservableValue locationValue;
	private IObservableValue checkOnStartupValue;
	
	public InstallJRubyWizardModel() {
		locationValue = new WritableValue(null, String.class);
		checkOnStartupValue = new WritableValue(JRubyPlugin.checkOnStartup(), Boolean.class);
	}
	
	public IObservableValue getLocationValue() {
		return locationValue;
	}

	public IObservableValue getCheckOnStartupValue() {
		return checkOnStartupValue;
	}
	
	static class LocationValidator implements IValidator {

		public IStatus validate(Object value) {
			String location = (String) value;
			
			if (location == null || location.trim().length() == 0) {
				return ValidationStatus.error(Messages.InstallJRubyWizardPage_EmptyLocationError);
			}
			
			File file = new File(location); 
			if (!file.exists()) {
				return ValidationStatus.error(Messages.InstallJRubyWizardPage_NotFoundError);
			}
			
			try {
				JarFile jar = new JarFile(file);
				Manifest manifest = jar.getManifest();
				if (manifest == null) {
					return ValidationStatus.error(Messages.InstallJRubyWizardPage_NoManifestError);
				}
				String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName"); //$NON-NLS-1$
				if (!JRubyPlugin.JRUBY_PLUGIN_ID.equals(symbolicName)) {
					return ValidationStatus.error(Messages.InstallJRubyWizardPage_InvalidJRubyJarError);
				}
			} catch (IOException e) {
				return ValidationStatus.error(Messages.InstallJRubyWizardPage_InvalidJarError);
			}
			
			
			return ValidationStatus.ok();
		}
		
	}

}
