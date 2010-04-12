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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.InstanceScope;
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

	private static final String LAST_DIRECTORY_KEY = JMapHeapDumpProvider.class.getName() + ".lastDir";

	public JMapHeapDumpProvider()
	{
		//        String lastDir = Platform.getPreferencesService().getString("org.eclipse.mat.api", LAST_DIRECTORY_KEY, "", null); //$NON-NLS-1$
		String lastDir = new InstanceScope().getNode("org.eclipse.mat.api").get(LAST_DIRECTORY_KEY, null);
		if (lastDir != null && !lastDir.trim().equals(""))
		{
			jpsExecutable = new File(lastDir);
		}
		System.out.println("lastDir=" + lastDir);
	}

	private static final String FILE_PATTERN = "java_pid%pid%.hprof"; //$NON-NLS-1$

	@Argument(isMandatory = false)
	@Help("Location of the appropriate jps executable. If no location is specified simply \"jps\" will be used")
	public File jpsExecutable;

	public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException
	{
		JmapVmInfo jmapProcessInfo = (JmapVmInfo) info;
		listener.beginTask(Messages.JMapHeapDumpProvider_WaitForHeapDump, IProgressMonitor.UNKNOWN);

		String jmap = jmapProcessInfo.jmapExecutable == null ? "jmap" : jmapProcessInfo.jmapExecutable.getAbsolutePath(); //$NON-NLS-1$
		String execLine = jmap + " -dump:format=b,file=" + preferredLocation.getAbsolutePath() + " " + info.getPid(); //$NON-NLS-1$ //$NON-NLS-2$
		Logger.getLogger(getClass().getName()).info("Executing: " + execLine); //$NON-NLS-1$
		Process p = null;
		try
		{
			p = Runtime.getRuntime().exec(execLine);

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
		}
		catch (IOException ioe)
		{
			throw new SnapshotException(Messages.JMapHeapDumpProvider_ErrorCreatingDump, ioe);
		}
		catch (InterruptedException ie)
		{
			throw new SnapshotException(Messages.JMapHeapDumpProvider_ErrorCreatingDump, ie);
		}
		finally
		{
			if (p != null) p.destroy();
		}

		listener.done();

		return preferredLocation;
	}

	public List<JmapVmInfo> getAvailableVMs(IProgressListener listener) throws SnapshotException
	{
		String jps = "jps";
		if (jpsExecutable != null && jpsExecutable.exists())
		{
			jps = jpsExecutable.getAbsolutePath();
			persistJPSLocation();
		}

		File vmLocation = getVMLocation();
		List<JmapVmInfo> result = new ArrayList<JmapVmInfo>();
		List<JmapVmInfo> jvms = LocalJavaProcessesUtils.getLocalVMsUsingJPS(jps);
		if (jvms != null)
		{
			for (JmapVmInfo vmInfo : jvms)
			{
				vmInfo.setProposedFileName(FILE_PATTERN);
				vmInfo.setHeapDumpProvider(this);
				if (vmLocation != null)
				{
					vmInfo.jmapExecutable = new File(vmLocation, "jmap");
				}
				result.add(vmInfo);
			}
		}
		return result;
	}

	private void persistJPSLocation()
	{
		new InstanceScope().getNode("org.eclipse.mat.api").put(LAST_DIRECTORY_KEY, jpsExecutable.getAbsolutePath());
		System.out.println("Saved location!");
	}

	private File getVMLocation()
	{
		if (jpsExecutable != null && jpsExecutable.exists())
		{
			return jpsExecutable.getParentFile();
		}
		return null;
	}

}
