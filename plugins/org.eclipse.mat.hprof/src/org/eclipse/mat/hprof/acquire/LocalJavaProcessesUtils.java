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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.hprof.Messages;
import org.eclipse.mat.snapshot.acquire.VmInfo;

public class LocalJavaProcessesUtils
{
	static List<VmInfo> getLocalVMsUsingJPS()
	{
		StreamCollector error = null;
		StreamCollector output = null;
		try
		{
			Process p = Runtime.getRuntime().exec("jps"); //$NON-NLS-1$
			error = new StreamCollector(p.getErrorStream());
			error.start();
			output = new StreamCollector(p.getInputStream());
			output.start();

			int exitVal = p.waitFor();

			if (exitVal != 0) return null;

			List<VmInfo> vms = new ArrayList<VmInfo>();
			StringTokenizer tok = new StringTokenizer(output.buf.toString(), "\r\n"); //$NON-NLS-1$
			while (tok.hasMoreTokens())
			{
				String token = tok.nextToken();

				// System.err.println(token);
				VmInfo info = parseJPSLine(token);
				if (info != null) vms.add(info);
			}
			return vms;
		}
		catch (Throwable t)
		{
			Logger.getLogger("org.eclipse.mat").log(Level.SEVERE, Messages.LocalJavaProcessesUtils_ErrorGettingProcessListJPS, t); //$NON-NLS-1$
			return null;
		}

	}

	private static VmInfo parseJPSLine(String line)
	{
		int firstSpaceIdx = line.indexOf(' ');
		if (firstSpaceIdx == -1) return null;
		int pid = Integer.parseInt(line.substring(0, firstSpaceIdx));
		String description = line.substring(firstSpaceIdx);
		return new VmInfo(pid, description, false, null, null);
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
			try
			{
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					buf.append(line).append("\r\n"); //$NON-NLS-1$
			}
			catch (IOException ioe)
			{
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, Messages.LocalJavaProcessesUtils_ErrorGettingProcesses, ioe);
			}
		}
	}
}
