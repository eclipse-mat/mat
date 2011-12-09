/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.acquire;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.Messages;

public class LocalJavaProcessesUtils
{
	static List<JmapVmInfo> getLocalVMsUsingJPS(File jdkHome) throws SnapshotException
	{
		String jps = "jps"; //$NON-NLS-1$
		if (jdkHome != null && jdkHome.exists())
		{
			jps = jdkHome.getAbsolutePath() + File.separator + "bin" + File.separator + "jps"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		StreamCollector error = null;
		StreamCollector output = null;
		Process p = null;
		try
		{
			p = Runtime.getRuntime().exec(new String[]{jps});
			error = new StreamCollector(p.getErrorStream());
			error.start();
			output = new StreamCollector(p.getInputStream());
			output.start();

			int exitVal = p.waitFor();

			if (exitVal != 0) return null;

			List<JmapVmInfo> vms = new ArrayList<JmapVmInfo>();
			StringTokenizer tok = new StringTokenizer(output.buf.toString(), "\r\n"); //$NON-NLS-1$
			while (tok.hasMoreTokens())
			{
				String token = tok.nextToken();

				// System.err.println(token);
				JmapVmInfo info = parseJPSLine(token);
				if (info != null) vms.add(info);
			}
			return vms;
		}
		catch (IOException ioe)
		{
			throw new SnapshotException(Messages.LocalJavaProcessesUtils_ErrorGettingProcessListJPS, ioe); //$NON-NLS-1$
		}
		catch (InterruptedException ie)
		{
			throw new SnapshotException(Messages.LocalJavaProcessesUtils_ErrorGettingProcessListJPS, ie); //$NON-NLS-1$
		}
		finally
		{
			if (p != null)
				p.destroy();
		}

	}

	private static JmapVmInfo parseJPSLine(String line)
	{
		int firstSpaceIdx = line.indexOf(' ');
		if (firstSpaceIdx == -1) return null;
		int pid = Integer.parseInt(line.substring(0, firstSpaceIdx));
		String description = line.substring(firstSpaceIdx);
		return new JmapVmInfo(pid, description, false, null, null);
	}

	static class StreamCollector extends Thread
	{
		InputStream is;
		StringBuilder buf;

		StreamCollector(InputStream is)
		{
			this.is = is;
			this.buf = new StringBuilder();
		}

		public void run()
		{
			InputStreamReader isr = null;
			try
			{
				isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					buf.append(line).append("\r\n"); //$NON-NLS-1$
			}
			catch (IOException ioe)
			{
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, Messages.LocalJavaProcessesUtils_ErrorGettingProcesses, ioe);
			}
			finally
			{
				if (isr != null) try
				{
					isr.close();
				}
				catch (IOException e)
				{
					// ignore this
				}
			}
		}
	}
}
