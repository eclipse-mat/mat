/*******************************************************************************
 * Copyright (c) 2009, 2021 SAP AG and IBM Corporation
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
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class LocalJavaProcessesUtils
{
    static List<JmapVmInfo> getLocalVMsUsingJPS(File jdkHome, IProgressListener listener) throws SnapshotException
    {
        final String modules[] = {"jcmd", "jcmd.exe", "jps", "jps.exe"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        String jps = "jps"; //$NON-NLS-1$
        String cmd = jps;
        if (jdkHome != null && jdkHome.exists())
        {
            for (String mod : modules)
            {
                File mod1 = new File(jdkHome.getAbsoluteFile(), "bin"); //$NON-NLS-1$
                mod1 = new File(mod1, mod);
                if (mod1.canExecute())
                {
                    jps = mod1.getAbsolutePath();
                    cmd = mod;
                    break;
                }
            }
        }
        String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        String cmds[] = cmd.startsWith("jcmd") ?  //$NON-NLS-1$
                        new String[]{jps, "-l", "-J-Dfile.encoding="+encoding} //$NON-NLS-1$//$NON-NLS-2$
        : new String[]{jps, "-l", "-J-Dfile.encoding="+encoding}; //$NON-NLS-1$//$NON-NLS-2$
                        listener.subTask(jps);
                        StreamCollector error = null;
                        StreamCollector output = null;
                        Process p = null;
                        try
                        {
                            p = Runtime.getRuntime().exec(cmds);
                            error = new StreamCollector(p.getErrorStream(), encoding);
                            error.start();
                            output = new StreamCollector(p.getInputStream(), encoding, listener);
                            output.start();

                            int exitVal = p.waitFor();

                            if (exitVal != 0) return null;

                            List<JmapVmInfo> vms = new ArrayList<JmapVmInfo>();
                            int jpsProcesses = 0;
                            String jpssig = cmd.startsWith("jcmd") ? "JCmd -l" : "Jps -m -l"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            StringTokenizer tok = new StringTokenizer(output.buf.toString(), "\r\n"); //$NON-NLS-1$
                            while (tok.hasMoreTokens())
                            {
                                String token = tok.nextToken();

                                // System.err.println(token);
                                JmapVmInfo info = parseJPSLine(token);
                                if (info != null)
                                {
                                    vms.add(info);
                                    if (info.getDescription().contains(jpssig))
                                        ++jpsProcesses;
                                }
                            }
                            // Mark the jps process as not suitable for dumps
                            if (jpsProcesses == 1)
                            {
                                for (JmapVmInfo info : vms)
                                {
                                    if (info.getDescription().contains(jpssig))
                                    {
                                        info.setHeapDumpEnabled(false);
                                        break;
                                    }
                                }
                            }
                            return vms;
                        }
                        catch (IOException ioe)
                        {
                            throw new SnapshotException(MessageUtil.format(Messages.LocalJavaProcessesUtils_ErrorGettingProcessListJPS, jps), ioe);
                        }
                        catch (InterruptedException ie)
                        {
                            throw new SnapshotException(MessageUtil.format(Messages.LocalJavaProcessesUtils_ErrorGettingProcessListJPS, jps), ie);
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
        IProgressListener listener;
        int count;

        StreamCollector(InputStream is)
        {
            this(is, System.getProperty("file.encoding", "UTF-8"), null);  //$NON-NLS-1$//$NON-NLS-2$
        }

        StreamCollector(InputStream is, String encoding)
        {
            this(is, encoding, null);
        }

        StreamCollector(InputStream is, String encoding, IProgressListener listener)
        {
            this.is = is;
            this.buf = new StringBuilder();
            this.encoding = encoding;
            this.listener = listener;
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
                {
                    buf.append(line).append("\r\n"); //$NON-NLS-1$
                    if (listener != null)
                    {
                        listener.worked(1);
                    }
                }
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
