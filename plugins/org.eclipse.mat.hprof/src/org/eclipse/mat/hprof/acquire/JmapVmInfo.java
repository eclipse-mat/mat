/*******************************************************************************
 * Copyright (c) 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.acquire;

import java.io.File;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;

public class JmapVmInfo extends VmInfo
{
	@Argument(isMandatory = false)
	@Help("Location of the appropriate jmap executable. If no location is specified simply \"jmap\" will be used")
	public File jmapExecutable;
	
	public JmapVmInfo(int pid, String description, boolean heapDumpEnabled, String proposedFileName, IHeapDumpProvider heapDumpProvider)
	{
		super(pid, description, heapDumpEnabled, proposedFileName, heapDumpProvider);
	}
}
