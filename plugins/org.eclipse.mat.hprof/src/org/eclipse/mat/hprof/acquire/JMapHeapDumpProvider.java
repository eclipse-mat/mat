/*******************************************************************************
 * Copyright (c) 2009, 2018 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - message fixes
 *******************************************************************************/
package org.eclipse.mat.hprof.acquire;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.Messages;
import org.eclipse.mat.hprof.acquire.LocalJavaProcessesUtils.StreamCollector;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.osgi.service.prefs.BackingStoreException;

@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#task_acquiringheapdump__1")
public class JMapHeapDumpProvider implements IHeapDumpProvider
{

	private static final String PLUGIN_ID = "org.eclipse.mat.hprof"; //$NON-NLS-1$
	private static final String LAST_JDK_DIRECTORY_KEY = JMapHeapDumpProvider.class.getName() + ".lastJDKDir"; //$NON-NLS-1$
	private static final String LAST_JMAP_JDK_DIRECTORY_KEY = JMapHeapDumpProvider.class.getName() + ".lastJmapJDKDir"; //$NON-NLS-1$
	private static final String FILE_PATTERN = "java_pid{1,number,0}.{2,number,0000}.hprof"; //$NON-NLS-1$

	@Argument(isMandatory = false, advice = Advice.DIRECTORY)
	public File jdkHome;

	public JMapHeapDumpProvider()
	{
		// initialize JDK from previously saved data
		jdkHome = readSavedLocation(LAST_JDK_DIRECTORY_KEY);

		// No user settings saved -> check if current java.home is a JDK
		if (jdkHome == null)
		{
			jdkHome = guessJDK();
		}
	}

	public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException
	{
		JmapVmInfo jmapProcessInfo = (JmapVmInfo) info;
		listener.beginTask(Messages.JMapHeapDumpProvider_WaitForHeapDump, IProgressMonitor.UNKNOWN);

		// just use jmap by default ...
		String jmap = "jmap"; //$NON-NLS-1$
		// check for previously saved / user defined location 
		if (jmapProcessInfo.jdkHome != null && jmapProcessInfo.jdkHome.exists())
		{
			jmap = jmapProcessInfo.jdkHome.getAbsolutePath() + File.separator + "bin" + File.separator + "jmap"; //$NON-NLS-1$ //$NON-NLS-2$
			persistJDKLocation(LAST_JMAP_JDK_DIRECTORY_KEY, jmapProcessInfo.jdkHome.getAbsolutePath());
		}
		
		// build the line to execute as a String[] because quotes in the name cause
		// problems on Linux - See bug 313636
		String[] execLine = new String[] { jmap, // jmap command
				"-dump:format=b,file=" + preferredLocation.getAbsolutePath(), //$NON-NLS-1$ 
				String.valueOf(info.getPid()) // pid
		};
		
		// log what gets executed
		StringBuilder logMessage = new StringBuilder();
		logMessage.append("Executing { "); //$NON-NLS-1$
		for (int i = 0; i < execLine.length; i++)
		{
			logMessage.append("\"").append(execLine[i]).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
			if (i < execLine.length - 1) logMessage.append(", "); //$NON-NLS-1$
		}
		logMessage.append(" }"); //$NON-NLS-1$
		
		Logger.getLogger(getClass().getName()).info(logMessage.toString()); //$NON-NLS-1$
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
				throw new SnapshotException(MessageUtil.format(Messages.JMapHeapDumpProvider_ErrorCreatingDump, exitCode, error.buf.toString()));
			}

			if (!preferredLocation.exists())
			{
				throw new SnapshotException(MessageUtil.format(Messages.JMapHeapDumpProvider_HeapDumpNotCreated, exitCode, output.buf.toString(), error.buf.toString()));
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
	    listener.beginTask(Messages.JMapHeapDumpProvider_ListProcesses, IProgressListener.UNKNOWN_TOTAL_WORK); //$NON-NLS-1$
		// was something injected from outside?
		if (jdkHome != null && jdkHome.exists())
		{
			persistJDKLocation(LAST_JDK_DIRECTORY_KEY, jdkHome.getAbsolutePath());
		}

		List<JmapVmInfo> result = new ArrayList<JmapVmInfo>();
		List<JmapVmInfo> jvms = LocalJavaProcessesUtils.getLocalVMsUsingJPS(jdkHome);
		if (jvms != null)
		{
			// try to get jmap specific location for the JDK
			File jmapJdkHome = readSavedLocation(LAST_JMAP_JDK_DIRECTORY_KEY);
			if (jmapJdkHome == null) jmapJdkHome = this.jdkHome;
			
			for (JmapVmInfo vmInfo : jvms)
			{
				vmInfo.setProposedFileName(FILE_PATTERN);
				vmInfo.setHeapDumpProvider(this);
				vmInfo.jdkHome = jmapJdkHome;
				result.add(vmInfo);
			}
		}
		listener.done();
		return result;
	}

	private void persistJDKLocation(String key, String value)
	{
		IEclipsePreferences prefs = new InstanceScope().getNode(PLUGIN_ID);
		prefs.put(key, value);
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			// e.printStackTrace();
			// ignore this exception
		}
	}

	private File readSavedLocation(String key)
	{
		String lastDir = Platform.getPreferencesService().getString(PLUGIN_ID, key, "", null); //$NON-NLS-1$
		if (lastDir != null && !lastDir.trim().equals("")) //$NON-NLS-1$
		{
			return new File(lastDir);
		}
		return null;
	}

	private File guessJDK()
	{
		String javaHomeProperty = System.getProperty("java.home"); //$NON-NLS-1$
		File parentFolder = new File(javaHomeProperty).getParentFile();
		File binDir = new File(parentFolder + File.separator + "bin"); //$NON-NLS-1$
		if (binDir.exists()) return parentFolder;

		return null;
	}

}
