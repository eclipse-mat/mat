/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.acquire;

import java.util.Locale;

import org.eclipse.mat.query.registry.ExecutableDescriptor;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;

/**
 * @noextend This class is not intended to be subclassed by clients.
 * @author ktsvetkov
 *
 */
public class HeapDumpProviderDescriptor extends ExecutableDescriptor
{
	protected final Class<? extends IHeapDumpProvider> subject;

	public HeapDumpProviderDescriptor(String identifier, String name, String usage, String help, String helpUrl, Locale helpLocale,
			Class<? extends IHeapDumpProvider> subject)
	{
		super(identifier, name, usage, help, helpUrl, helpLocale);
		this.subject = subject;
	}

	public Class<? extends IHeapDumpProvider> getSubject()
	{
		return subject;
	}
}
