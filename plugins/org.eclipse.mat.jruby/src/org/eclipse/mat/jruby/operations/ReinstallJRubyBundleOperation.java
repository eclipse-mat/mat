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
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.mat.jruby.JRubyPlugin;

public class ReinstallJRubyBundleOperation implements IRunnableWithProgress {
	
	private String url;
	
	public ReinstallJRubyBundleOperation(String url) {
		this.url = url;
	}

	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		boolean jrubyInstalled = JRubyPlugin.isRubyInstalled();
		
		monitor.beginTask(Messages.ReinstallJRubyBundleOperation_ReinstallJRubyBundle, (jrubyInstalled) ? 4 : 2);
		
		if (jrubyInstalled) {
			new UninstallJRubyBundleOperation().run(new SubProgressMonitor(monitor, 2));
		}
		new InstallJRubyBundleOperation(url).run(new SubProgressMonitor(monitor, 2));
	}

}
