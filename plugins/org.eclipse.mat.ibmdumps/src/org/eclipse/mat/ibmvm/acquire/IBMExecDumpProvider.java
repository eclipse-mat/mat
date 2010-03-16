/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

@Name("IBM VM")
abstract class IBMExecDumpProvider extends BaseProvider
{

    private static final String PLUGIN_ID = "org.eclipse.mat.ibmdump"; //$NON-NLS-1$
    private static final String JAVA_EXEC = "java"; //$NON-NLS-1$
    private static boolean abort = false;

    public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws Exception
    {
        listener.beginTask(Messages.getString("IBMExecDumpProvider.GeneratingDump"), TOTAL_WORK); //$NON-NLS-1$
        ProcessBuilder pb = new ProcessBuilder();
        Process p = null;
        String home = getJavaHome();
        File javaExec;
        javaExec = javaExec(home);
        String vm = ((IBMVmInfo) info).getPidName();
        String jar = getExecJar().getAbsolutePath();
        final String execPath = javaExec.getPath();
        pb.command(execPath, "-jar", jar, agentCommand(), vm, info.getProposedFileName()); //$NON-NLS-1$
        p = pb.start();
        StringBuffer err = new StringBuffer();
        StringBuffer in = new StringBuffer();
        InputStreamReader os = new InputStreamReader(p.getInputStream());
        try
        {
            InputStreamReader es = new InputStreamReader(p.getErrorStream());
            try
            {
                int rc = 0;
                do
                {
                    while (os.ready())
                    {
                        in.append((char) os.read());
                    }
                    while (es.ready())
                    {
                        int c = es.read();
                        if (c == '.')
                            listener.worked(1);
                        err.append((char) c);
                    }
                    try
                    {
                        rc = p.exitValue();
                        break;
                    }
                    catch (IllegalThreadStateException e)
                    {
                        Thread.sleep(SLEEP_TIMEOUT);
                    }
                    if (listener.isCanceled())
                    {
                        p.destroy();
                        return null;
                    }
                }
                while (true);
                if (rc != 0) 
                {
                    throw new IOException(MessageFormat.format(Messages
                                .getString("IBMExecDumpProvider.ReturnCode"), execPath, rc, err.toString())); //$NON-NLS-1$
                }
                String ss[] = in.toString().split("[\\n\\r]+"); //$NON-NLS-1$
                String filename = ss[0];
                listener.done();
                final File file = new File(filename);
                if (!file.canRead()) { throw new FileNotFoundException(filename); }
                return file;
            }
            finally
            {
                es.close();
            }
        }
        finally
        {
            os.close();
        }

    }

    private File javaExec(String home)
    {
        return jvmExec(home, JAVA_EXEC);
    }

    static File jvmExec(String home, String exec)
    {
        File javaExec;
        if (home != null)
        {
            File homebin = new File(home, "bin"); //$NON-NLS-1$
            javaExec = new File(homebin, exec);
        }
        else
        {
            javaExec = new File(exec);
        }
        return javaExec;
    }

    public List<VmInfo> getAvailableVMs()
    {
        List<VmInfo> ret;
        try
        {
            ret = IBMDumpProvider.getDumpProvider(this).getAvailableVMs();
            if (ret != null)
                return null;
        }
        catch (LinkageError e)
        {}
        if (abort)
            return null;
        /*
         * 1.Try previous/no directory 2.Query directory - based on
         * previous/Java.home 3.If no directory/failed, offer abort/retry/ignore
         */

        String home = getJavaHome();

        File javaExec;
        javaExec = javaExec(home);

        ret = execGetVMs(javaExec, home);
        while (ret == null)
        {
            if (home == null)
            {
                home = defaultJavaHome();
            }
            // Failed, so try choosing a VM
            String home2 = chooseJavaHome(home);
            if (home2 != null)
            {
                javaExec = javaExec(home2);
                ret = execGetVMs(javaExec, home2);
                if (ret != null)
                {
                    setJavaHome(home2);
                    break;
                }
                home = home2;
            }
            int r = retry();
            if (r == SWT.ABORT)
            {
                // Never try IBM dumps again
                abort = true;
                break;
            }
            if (r != SWT.RETRY)
                break;
        }
        return ret;
    }

    /**
     * Guess a suitable VM to suggest to the user
     * 
     * @return
     */
    private String defaultJavaHome()
    {
        String home;
        String path = System.getenv("PATH"); //$NON-NLS-1$
        if (path != null)
        {
            for (String p : path.split(File.pathSeparator))
            {
                File dir = new File(p);
                File parentDir = dir.getParentFile();
                // Recent IBM VMs have diagnostics collector and late attach
                File dll = new File(dir, "dgcollector.dll"); //$NON-NLS-1$
                if (dll.exists())
                {
                    if (parentDir != null)
                        return parentDir.getPath();
                }
                dll = new File(p, "dgcollector.so"); //$NON-NLS-1$
                if (dll.exists())
                {
                    if (parentDir != null)
                        return parentDir.getPath();
                }
                // Perhaps we were given the sdk/bin directory, so look for the
                // sdk/jre/bin
                dir = new File(parentDir, "jre"); //$NON-NLS-1$
                dir = new File(dir, "bin"); //$NON-NLS-1$
                dll = new File(dir, "dgcollector.dll"); //$NON-NLS-1$
                if (dll.exists())
                {
                    if (parentDir != null)
                        return parentDir.getPath();
                }
                dll = new File(p, "dgcollector.so"); //$NON-NLS-1$
                if (dll.exists())
                {
                    if (parentDir != null)
                        return parentDir.getPath();
                }
            }
        }
        home = System.getProperty("java.home"); //$NON-NLS-1$
        return home;
    }

    private static final String last_directory_key = IBMExecDumpProvider.class.getName() + ".lastDir"; //$NON-NLS-1$
    private static String savedJavaHome;

    private static synchronized String getJavaHome()
    {
        String home = Platform.getPreferencesService().getString(PLUGIN_ID, last_directory_key, savedJavaHome, null);
        return home;
    }

    private static synchronized void setJavaHome(String home)
    {
        new InstanceScope().getNode(PLUGIN_ID).put(last_directory_key, home);
        savedJavaHome = home;
    }

    private List<VmInfo> execGetVMs(File javaExec, String home)
    {
        ArrayList<VmInfo> ar = new ArrayList<VmInfo>();
        ProcessBuilder pb = new ProcessBuilder();
        Process p = null;
        final String execPath = javaExec.getPath();
        try
        {
            String jar = getExecJar().getAbsolutePath();
            pb.command(execPath, "-jar", jar, agentCommand()); //$NON-NLS-1$
            p = pb.start();
            StringBuffer err = new StringBuffer();
            StringBuffer in = new StringBuffer();
            InputStreamReader os = new InputStreamReader(p.getInputStream());
            try
            {
                InputStreamReader es = new InputStreamReader(p.getErrorStream());
                try
                {
                    int rc = 0;
                    do
                    {
                        while (os.ready())
                        {
                            in.append((char) os.read());
                        }
                        while (es.ready())
                        {
                            err.append((char) es.read());
                        }
                        try
                        {
                            rc = p.exitValue();
                            break;
                        }
                        catch (IllegalThreadStateException e)
                        {
                            Thread.sleep(100);
                        }
                    }
                    while (true);
                    if (rc != 0)
                    {
                        getLogger().log(Level.WARNING,
                                        MessageFormat.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMsRC"), execPath, rc, err.toString())); //$NON-NLS-1$
                        ar = null;
                        return ar;
                    }
                    String ss[] = in.toString().split("[\\n\\r]+"); //$NON-NLS-1$
                    for (String s : ss)
                    {
                        // command,pid,proposed filename,description
                        String s2[] = s.split(",", 4); //$NON-NLS-1$
                        if (s2.length >= 4)
                        {
                            // Exclude the helper process
                            if (!s2[3].contains(getExecJar().getName()))
                            {
                                IBMVmInfo ifo = new IBMVmInfo();
                                ifo.setPid(s2[1]);
                                ifo.setProposedFileName(s2[2]);
                                ifo.setDescription(s2[3]);
                                IBMExecDumpProvider prov = this;
                                if (!s2[0].equals(prov.agentCommand()))
                                {
                                    prov = new IBMExecSystemDumpProvider();
                                    if (!s2[0].equals(prov.agentCommand()))
                                    {
                                        prov = new IBMExecHeapDumpProvider();
                                    }
                                }
                                ifo.setHeapDumpProvider(this);
                                ar.add(ifo);
                            }
                        }
                    }
                }
                finally
                {
                    es.close();
                }
            }
            finally
            {
                os.close();
            }
        }
        catch (IOException e)
        {
            getLogger().log(Level.WARNING, MessageFormat.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMs"), execPath), e); //$NON-NLS-1$
            ar = null;
        }
        catch (InterruptedException e)
        {
            getLogger().log(Level.WARNING, MessageFormat.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMs"), execPath), e); //$NON-NLS-1$
            ar = null;
        }
        return ar;
    }

    private Logger getLogger()
    {
        return Logger.getLogger("org.eclipse.mat"); //$NON-NLS-1$
    }

    private static File execJar;

    static synchronized File getExecJar() throws IOException
    {
        if (execJar == null || !execJar.canRead())
        {
            String jarname = "org.eclipse.mat.ibmexecdumps"; //$NON-NLS-1$
            String classesNames[] = {"org.eclipse.mat.ibmvm.acquire.IBMDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AgentLoader", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$FileComparator", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$NewFileFilter", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$StderrProgressListener", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.BaseProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMHeapDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMSystemDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMVmInfo", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.agent.DumpAgent" }; //$NON-NLS-1$
            Class<?> classes[] = { SnapshotException.class, IHeapDumpProvider.class, VmInfo.class,
                            IProgressListener.class, IProgressListener.OperationCanceledException.class,
                            Severity.class, Messages.class, };
            execJar = makeJar(jarname, "Main-Class: ", classesNames, classes); //$NON-NLS-1$
        }
        return execJar;
    }

    /**
     * Ask the user for a suitable Java home for an IBM VM to help attach to the
     * target VM.
     * 
     * @param oldHome
     * @return
     */
    public String chooseJavaHome(String oldHome)
    {
        Display d = Display.getCurrent();
        if (d == null)
            d = Display.getDefault();
        if (d == null)
            return oldHome;
        Shell shell = d.getActiveShell();
        if (shell == null)
            return oldHome;
        DirectoryDialog dialog = new DirectoryDialog(shell);

        dialog.setMessage(Messages.getString("IBMExecDumpProvider.ChooseIBMVMDirectory")); //$NON-NLS-1$

        dialog.setFilterPath(oldHome);

        String folder = dialog.open();

        return folder;
    }

    /**
     * Ask the user for a suitable Java home for an IBM VM to help attach to the
     * target VM.
     * 
     * @param oldHome
     * @return
     */
    public int retry()
    {
        Display d = Display.getCurrent();
        if (d == null)
            d = Display.getDefault();
        if (d == null)
            return SWT.IGNORE;
        Shell shell = d.getActiveShell();
        if (shell == null)
            return SWT.IGNORE;
        MessageBox mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.RETRY | SWT.ABORT | SWT.IGNORE);
        mb.setMessage(Messages.getString("IBMExecDumpProvider.ChooseAnotherVM")); //$NON-NLS-1$
        return mb.open();
    }

    protected String agentCommand()
    {
        return null;
    }

}
