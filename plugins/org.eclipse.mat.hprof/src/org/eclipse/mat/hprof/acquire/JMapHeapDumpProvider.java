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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.Messages;
import org.eclipse.mat.hprof.acquire.LocalJavaProcessesUtils.StreamCollector;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;

@Help("Generates a binary HPROF heap dump using jmap")
@Name("HPROF jmap dump provider")
public class JMapHeapDumpProvider implements IHeapDumpProvider
{

	private static final String FILE_PATTERN = "java_pid%pid%.hprof"; //$NON-NLS-1$
	
	@Argument(isMandatory = false)
	@Help("Location of the appropriate jmap executable. If no location is specified simply \"jmap\" will be used")
	public File jmapExecutable;

	public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws Exception
	{
		listener.beginTask(Messages.JMapHeapDumpProvider_WaitForHeapDump, IProgressMonitor.UNKNOWN);

		String jmap = jmapExecutable == null ? "jmap" : jmapExecutable.getAbsolutePath(); //$NON-NLS-1$
		String execLine = jmap + " -dump:format=b,file=" + preferredLocation.getAbsolutePath() + " " + info.getPid(); //$NON-NLS-1$ //$NON-NLS-2$
		Logger.getLogger(getClass().getName()).info("Executing: " + execLine); //$NON-NLS-1$
		Process p = Runtime.getRuntime().exec(execLine);

		StreamCollector error = new StreamCollector(p.getErrorStream());
		error.start();
		StreamCollector output = new StreamCollector(p.getInputStream());
		output.start();

		if (listener.isCanceled()) return null;

		int exitCode = p.waitFor();
		if (exitCode != 0)
		{
			throw new SnapshotException(Messages.JMapHeapDumpProvider_ErrorCreatingDump + exitCode + "\r\n" + error.buf.toString()); //$NON-NLS-2$ //$NON-NLS-1$
		}

		if (!preferredLocation.exists())
		{
			throw new SnapshotException(Messages.JMapHeapDumpProvider_HeapDumpNotCreated + exitCode + "\r\nstdout:\r\n" + output.buf.toString() //$NON-NLS-2$ //$NON-NLS-1$
					+ "\r\nstderr:\r\n" + error.buf.toString()); //$NON-NLS-1$
		}

		listener.done();

		return preferredLocation;
	}

	public List<VmInfo> getAvailableVMs()
	{
		List<VmInfo> result = new ArrayList<VmInfo>();
		List<VmInfo> jvms = LocalJavaProcessesUtils.getLocalVMsUsingJPS();
		if (jvms != null)
		{
			for (VmInfo vmInfo : jvms)
			{
				vmInfo.setProposedFileName(FILE_PATTERN);
				vmInfo.setHeapDumpProvider(this);
				result.add(vmInfo);
			}
		}
		return result;
	}

}
