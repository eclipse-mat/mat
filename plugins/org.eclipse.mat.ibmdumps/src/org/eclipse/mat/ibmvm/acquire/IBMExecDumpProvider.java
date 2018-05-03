/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *    IBM Corporation/Andrew Johnson - updates for calling com.ibm/com.sun classes via reflection
 *    IBM Corporation/Andrew Johnson - hprof and allow option for GC before dump
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Enables the creation of dumps from IBM VMs when a non-IBM VM
 * or old IBM VM is used to run Memory Analyzer. A new IBM VM is
 * used as a helper VM.
 * @author ajohnson
 *
 */
@Name("IBM Dump (using helper VM)")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#3")
public class IBMExecDumpProvider extends BaseProvider
{
    private static final String PLUGIN_ID = "org.eclipse.mat.ibmdump"; //$NON-NLS-1$
    private static final String JAVA_EXEC = "java"; //$NON-NLS-1$
    private static boolean abort = false;
    private int lastCount = 20;

    @Argument(isMandatory = false)
    public File javaexecutable;

    @Argument(isMandatory = false)
    public String vmoptions[] = {}; //$NON-NLS-1$

    public IBMExecDumpProvider()
    {
        // See if an IBM VM or an Oracle VM
        try
        {
            Class.forName("com.ibm.jvm.Dump");
        }
        catch (ClassNotFoundException e)
        {
            // Looks like no System dump is available
            defaultType = DumpType.HPROF;
        }
    }

    public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException
    {
        listener.beginTask(Messages.getString("IBMExecDumpProvider.GeneratingDump"), TOTAL_WORK); //$NON-NLS-1$
        String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        String encodingOpt = "-Dfile.encoding="+encoding; //$NON-NLS-1$
        ProcessBuilder pb = new ProcessBuilder();
        Process p = null;
        final IBMExecVmInfo info2 = (IBMExecVmInfo) info;
        String vm = info2.getPidName();
        try
        {
            String jar = getExecJar().getAbsolutePath();
            final String execPath = info2.javaexecutable.getPath();
            List<String> args = new ArrayList<String>(9);
            args.add(execPath);
            args.add(encodingOpt);
            if (info2.vmoptions != null)
            {
                args.addAll(Arrays.asList(info2.vmoptions));
            }
            args.add("-jar"); //$NON-NLS-1$
            args.add(jar);
            args.add(info2.type.toString());
            args.add(vm);
            args.add(Boolean.toString(info2.live));
            args.add(Boolean.toString(info2.compress));
            args.add(preferredLocation.getAbsolutePath());
            if (info2.dumpdir != null)
                args.add(info2.dumpdir.getAbsolutePath());
            pb.command(args);
            p = pb.start();
            StringBuffer err = new StringBuffer();
            StringBuffer in = new StringBuffer();
            InputStreamReader os = new InputStreamReader(p.getInputStream(), encoding);
            try
            {
                InputStreamReader es = new InputStreamReader(p.getErrorStream(), encoding);
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
                            try
                            {
                                Thread.sleep(SLEEP_TIMEOUT);
                            }
                            catch (InterruptedException e1)
                            {
                                listener.setCanceled(true);
                            }
                        }
                        if (listener.isCanceled())
                        {
                            return null;
                        }
                    }
                    while (true);
                    if (rc != 0)
                    {
                        throw new IOException(MessageUtil.format(Messages
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
                try
                {
                    p.exitValue();
                }
                catch (IllegalThreadStateException e)
                {
                    p.destroy();
                }
                os.close();
            }
        }
        catch (FileNotFoundException e)
        {
            throw new SnapshotException(e);
        }
        catch (IOException e)
        {
            throw new SnapshotException(e);
        }
    }

    private File javaExec(String dir)
    {
        return jvmExec(dir, JAVA_EXEC);
    }

    static File jvmExec(String javaDir, String exec)
    {
        File javaExec;
        if (javaDir != null)
        {
            javaExec = new File(javaDir, exec);
        }
        else
        {
            javaExec = new File(exec);
        }
        return javaExec;
    }

    public List<VmInfo> getAvailableVMs(IProgressListener listener)
    {
        List<VmInfo> ret;
        if (abort)
            return Collections.<VmInfo>emptyList();
        /*
         * 1.Try previous/no directory 2.Query directory - based on
         * previous/Java.home
         */

        File javaExec = javaexecutable;
        String javaDir;

        if (javaExec != null)
        {
            javaDir = javaExec.getParent();
        }
        else
        {
            javaDir = lastJavaDir();

            if (javaDir == null)
                javaDir = defaultJavaDir();

            javaExec = javaExec(javaDir);
        }

        ret = execGetVMs(javaExec, listener);
        if (ret != null)
        {
            setJavaDir(javaDir);
            this.javaexecutable = javaExec;
        }
        return ret;
    }

    /**
     * Guess a suitable VM to suggest to the user
     *
     * @return
     */
    private String defaultJavaDir()
    {
        String ibmmodules[] = {"dgcollector.dll", "dgcollector.so", "jdmpview.exe", "jdmpview"}; //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
        String path = System.getenv("PATH"); //$NON-NLS-1$
        if (path != null)
        {
            for (String p : path.split(File.pathSeparator))
            {
                File dir = new File(p);
                File parentDir = dir.getParentFile();
                // Recent IBM VMs have diagnostics collector and late attach
                for (String mod : ibmmodules)
                {
                    File dll = new File(dir, mod);
                    if (dll.canRead())
                    {
                        return dir.getPath();
                    }
                }
                // Perhaps we were given the sdk/bin directory, so look for the
                // sdk/jre/bin
                dir = new File(parentDir, "jre"); //$NON-NLS-1$
                dir = new File(dir, "bin"); //$NON-NLS-1$
                for (String mod : ibmmodules)
                {
                    File dll = new File(dir, mod);
                    if (dll.canRead())
                    {
                        return dir.getPath();
                    }
                }
            }
        }
        String home = System.getProperty("java.home"); //$NON-NLS-1$
        return new File(home, "bin").getPath(); //$NON-NLS-1$
    }

    private static final String last_directory_key = IBMExecDumpProvider.class.getName() + ".lastDir"; //$NON-NLS-1$
    private static String savedJavaDir;

    private static synchronized String lastJavaDir()
    {
        String home = Platform.getPreferencesService().getString(PLUGIN_ID, last_directory_key, savedJavaDir, null);
        return home;
    }

    private static synchronized void setJavaDir(String home)
    {
        if (home != null && !home.equals(savedJavaDir))
        {
            IEclipsePreferences prefs = new InstanceScope().getNode(PLUGIN_ID);
            prefs.put(last_directory_key, home);
            try
            {
                prefs.flush();
            }
            catch (BackingStoreException e)
            {
            }
            savedJavaDir = home;
        }
    }

    /**
     * Get list of VMs
     * @param javaExec
     * @param listener
     * @return List of VMs - might be empty if there is an error
     */
    private List<VmInfo> execGetVMs(File javaExec, IProgressListener listener)
    {
        ArrayList<VmInfo> ar = new ArrayList<VmInfo>();
        listener.beginTask(Messages.getString("IBMExecDumpProvider.ListingIBMVMs"), lastCount);
        int count = 0;
        String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        String encodingOpt = "-Dfile.encoding="+encoding; //$NON-NLS-1$
        ProcessBuilder pb = new ProcessBuilder();
        Process p = null;
        final String execPath = javaExec.getPath();
        try
        {
            String jar = getExecJar().getAbsolutePath();
            List<String> args = new ArrayList<String>(4);
            args.add(execPath);
            args.add(encodingOpt);
            if (vmoptions != null)
            {
                args.addAll(Arrays.asList(vmoptions));
            }
            args.add("-jar"); //$NON-NLS-1$
            args.add(jar);
            // Verbose listing?
            args.add(Boolean.toString(listAttach));
            pb.command(args);
            p = pb.start();
            StringBuffer err = new StringBuffer();
            StringBuffer in = new StringBuffer();
            InputStreamReader os = new InputStreamReader(p.getInputStream(), encoding);
            try
            {
                InputStreamReader es = new InputStreamReader(p.getErrorStream(), encoding);
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
                            char read = (char) es.read();
                            err.append(read);
                            if (read == '.')
                            {
                                // IBMDumpProvider prints a dot for each thing worked
                                listener.worked(1);
                            }
                            ++count;
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
                            // User cancelled, so perhaps attaching for details was a bad idea
                            listAttach = false;
                            break;
                        }
                    }
                    while (true);
                    if (rc != 0)
                    {
                        listener.sendUserMessage(Severity.WARNING,
                                        MessageUtil.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMsRC"), execPath, rc, err.toString()), null); //$NON-NLS-1$
                        return ar;
                    }
                    String ss[] = in.toString().split("[\\n\\r]+"); //$NON-NLS-1$
                    for (String s : ss)
                    {
                        // pid;dump enabled;dump type;proposed filename;possible directory;description
                        String s2[] = s.split(INFO_SEPARATOR, 6);
                        if (s2.length >= 5)
                        {
                            // Exclude the helper process
                            if (!s2[5].contains(getExecJar().getName()))
                            {
                                boolean enableDump = Boolean.parseBoolean(s2[1]);
                                IBMExecVmInfo ifo = new IBMExecVmInfo(s2[0], s2[5], enableDump, null, this);
                                ifo.javaexecutable = javaExec;
                                ifo.vmoptions = vmoptions;
                                /*
                                 * Get the suggested dump type from the exec program.
                                 * If it was an IBM VM, and this is one too, retain our suggested type,
                                 * otherwise use the type suggested by the exec program.
                                 */
                                DumpType t = DumpType.valueOf(s2[2]);
                                if (isIBMDumpType(t) && isIBMDumpType(defaultType))
                                    ifo.type = defaultType;
                                else
                                    ifo.type = t;
                                ifo.live = defaultLive;
                                ifo.compress = defaultCompress;
                                if (s2[4].length() > 0)
                                    ifo.dumpdir = new File(s2[4]);
                                if (s2[3].length() > 0 && !s2[3].equals(ifo.getProposedFileName()))
                                {
                                    // Only set the name if the automatic naming is not applied 
                                    ifo.setProposedFileName(s2[3]);
                                }
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
            listener.sendUserMessage(Severity.WARNING, MessageUtil.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMs"), execPath), e); //$NON-NLS-1$
        }
        catch (InterruptedException e)
        {
            listener.sendUserMessage(Severity.WARNING, MessageUtil.format(Messages.getString("IBMExecDumpProvider.ProblemListingVMs"), execPath), e); //$NON-NLS-1$
        }
        listener.done();
        // Remember the count of progress as an estimate for next time
        if (count > 0)
        {
            lastCount = count;
        }
        return ar;
    }

    private boolean isIBMDumpType(DumpType t)
    {
        return t == DumpType.SYSTEM || t == DumpType.HEAP || t == DumpType.JAVA;
    }

    private static File execJar;

    static synchronized File getExecJar() throws IOException
    {
        if (execJar == null || !execJar.canRead())
        {
            String jarname = "org.eclipse.mat.ibmexecdumps"; //$NON-NLS-1$
            // Must add all classes in IBMDumpProvider.java
            String classesNames[] = {"org.eclipse.mat.ibmvm.acquire.IBMDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AgentInitializationException", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AgentLoadException", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AttachNotSupportedException", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AttachOperationFailedException", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AttachProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$VirtualMachine", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$VirtualMachineDescriptor", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$AgentLoader", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$FileComparator", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$NewFileFilter", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMDumpProvider$StderrProgressListener", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.BaseProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.HprofDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMHeapDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMSystemDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMJavaDumpProvider", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.IBMVmInfo", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.AgentLoader2", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.acquire.DumpType", //$NON-NLS-1$
                            "org.eclipse.mat.ibmvm.agent.DumpAgent" }; //$NON-NLS-1$
            Class<?> classes[] = { SnapshotException.class, IHeapDumpProvider.class, VmInfo.class,
                            IProgressListener.class, IProgressListener.OperationCanceledException.class,
                            Severity.class, Messages.class, };
            execJar = makeJar(jarname, "Main-Class: ", classesNames, classes); //$NON-NLS-1$
        }
        return execJar;
    }

}
