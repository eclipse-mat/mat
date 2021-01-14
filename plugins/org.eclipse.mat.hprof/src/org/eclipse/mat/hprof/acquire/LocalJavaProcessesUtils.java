/*******************************************************************************
 * Copyright (c) 2009, 2020 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - enable/disabled dumps
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
		    String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
			p = Runtime.getRuntime().exec(new String[]{jps, "-m", "-l", "-J-Dfile.encoding="+encoding}); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
			error = new StreamCollector(p.getErrorStream(), encoding);
			error.start();
			output = new StreamCollector(p.getInputStream(), encoding);
			output.start();

			int exitVal = p.waitFor();

			if (exitVal != 0) return null;

			List<JmapVmInfo> vms = new ArrayList<JmapVmInfo>();
			int jpsProcesses = 0;
			StringTokenizer tok = new StringTokenizer(output.buf.toString(), "\r\n"); //$NON-NLS-1$
			while (tok.hasMoreTokens())
			{
				String token = tok.nextToken();

				// System.err.println(token);
				JmapVmInfo info = parseJPSLine(token);
				String jpssig = "Jps -ml";
                if (info != null)
				{
				    vms.add(info);
				    if (info.getDescription().contains(jpssig)) //$NON-NLS-1$
				        ++jpsProcesses;
				}
				// Mark the jps process as not suitable for dumps
				if (jpsProcesses == 1)
				{
				    for (JmapVmInfo inf : vms)
				    {
				        if (info.getDescription().contains(jpssig)) //$NON-NLS-1$
				        {
				            info.setHeapDumpEnabled(false);
				            break;
				        }
				    }
				}
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
		String description = line.substring(firstSpaceIdx + 1);
		return new JmapVmInfo(pid, description, true, null, null);
	}

	static class StreamCollector extends Thread
	{
		InputStream is;
		StringBuilder buf;
		String encoding;

		StreamCollector(InputStream is)
		{
			this(is, System.getProperty("file.encoding", "UTF-8"));  //$NON-NLS-1$//$NON-NLS-2$
		}
		
	      StreamCollector(InputStream is, String encoding)
	        {
	            this.is = is;
	            this.buf = new StringBuilder();
	            this.encoding = encoding;
	        }

		public void run()
		{
			InputStreamReader isr = null;
			try
			{
				isr = new InputStreamReader(is, encoding);
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
