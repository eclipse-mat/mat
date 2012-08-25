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
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;

import com.ibm.tools.attach.AgentInitializationException;
import com.ibm.tools.attach.AgentLoadException;
import com.ibm.tools.attach.AttachNotSupportedException;
import com.ibm.tools.attach.VirtualMachine;
import com.ibm.tools.attach.VirtualMachineDescriptor;

/**
 * Base class for generating dumps on IBM VMs.
 * This class requires an IBM VM to compile.
 * A precompiled version of this class exists in the classes folder.
 * @author ajohnson
 *
 */
@Name("IBM Dump (using attach API)")
@Help("help for IBM Dump (using attach API)")
public class IBMDumpProvider extends BaseProvider
{
    /**
     * Helper class to load an agent (blocking call)
     * allowing the main thread to monitor its progress
     * @author ajohnson
     *
     */
    private static final class AgentLoader extends Thread implements AgentLoader2
    {
        private final String jar;
        private final VirtualMachine vm;
        private final String command;
        private AgentLoadException e1;
        private AgentInitializationException e2;
        private IOException e3;
        private boolean fail;

        private AgentLoader(String jar, VirtualMachine vm, String command)
        {
            this.jar = jar;
            this.vm = vm;
            this.command = command;
        }

        /* (non-Javadoc)
         * @see org.eclipse.mat.ibmvm.acquire.AgentLoader2#run()
         */
        public void run() {
            try
            {
                vm.loadAgent(jar, command);
            }
            catch (AgentLoadException e2)
            {
                this.e1 = e2;
                setFailed();
            }
            catch (AgentInitializationException e)
            {
                this.e2 = e;
                setFailed();
            }
            catch (IOException e3)
            {
                this.e3 = e3;
                setFailed();
            }
        }
        
        /* (non-Javadoc)
         * @see org.eclipse.mat.ibmvm.acquire.AgentLoader2#failed()
         */
        public synchronized boolean failed()
        {
            return fail;
        }
        
        private synchronized void setFailed()
        {
            fail = true;
        }

        /* (non-Javadoc)
         * @see org.eclipse.mat.ibmvm.acquire.AgentLoader2#throwFailed(org.eclipse.mat.util.IProgressListener)
         */
        public void throwFailed(IProgressListener listener) throws SnapshotException, IOException
        {
            if (e1 != null)
            {
                listener.sendUserMessage(Severity.WARNING, Messages.getString("IBMDumpProvider.AgentLoad"), e1); //$NON-NLS-1$
                throw new SnapshotException(Messages.getString("IBMDumpProvider.AgentLoad"), e1); //$NON-NLS-1$
            }
            if (e2 != null)
            {
                listener.sendUserMessage(Severity.WARNING, Messages.getString("IBMDumpProvider.AgentInitialization"), e2); //$NON-NLS-1$
                throw new SnapshotException(Messages.getString("IBMDumpProvider.AgentInitialization"), e2); //$NON-NLS-1$
            }
            if (e3 != null)
            {
                throw e3;
            }
        }
    }

    /**
     * Find new files not ones we know about
     */
    private static final class NewFileFilter implements FileFilter
    {
        private final Collection<File> previousFiles;

        private NewFileFilter(Collection<File> previousFiles)
        {
            this.previousFiles = previousFiles;
        }

        public boolean accept(File f)
        {
            return !previousFiles.contains(f);
        }
    }

    /**
     * Indicate progress back to starting process
     * @author ajohnson
     *
     */
    private static final class StderrProgressListener implements IProgressListener
    {
        public void beginTask(String name, int totalWork)
        {}

        public void done()
        {}

        public boolean isCanceled()
        {
            return false;
        }

        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {}

        public void setCanceled(boolean value)
        {}

        public void subTask(String name)
        {}

        public void worked(int work)
        {
            for (int i = 0; i < work; ++i)
            {
                System.err.print('.');
            }
        }
    }

    /**
     * sorter for files by date modified
     */
    private static final class FileComparator implements Comparator<File>, Serializable
    {
        /**
         * 
         */
        private static final long serialVersionUID = -3725792252276130382L;

        public int compare(File f1, File f2)
        {
            return Long.valueOf(f1.lastModified()).compareTo(Long.valueOf(f2.lastModified()));
        }
    }

    IBMDumpProvider()
    {
    }

    /**
     * Suggested name for dumps of this type
     * @return example dump name
     */
    String dumpName()
    {
        return new File("ibmdump.dmp").getAbsolutePath(); //$NON-NLS-1$
    }

    private static File agentJar;

    /**
     * Number of files generated by this dump type
     * @return the number of files, often just 1 or 2 (e.g. javacore + phd).
     */
    int files()
    {
        return 1;
    }

    /**
     * Post process a generated dump
     * @param preferredDump
     * @param dump
     * @param udir
     * @param javahome
     * @param listener
     * @return the result of post-processing the dump
     * @throws IOException
     * @throws InterruptedException
     * @throws SnapshotException
     */
    File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File javahome, IProgressListener listener)
                    throws IOException, InterruptedException, SnapshotException
    {
        return dumps.get(0);
    }

    /**
     * Average file length for a group of files.
     * @param files
     * @return
     */
    long averageFileSize(Collection<File> files)
    {
        long l = 0;
        int i = 0;
        for (File f : files)
        {
            if (f.isFile())
            {
                l += f.length();
                ++i;
            }
        }
        return l / i;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.snapshot.acquire.IHeapDumpProvider#acquireDump(org.eclipse.mat.snapshot.acquire.VmInfo, java.io.File, org.eclipse.mat.util.IProgressListener)
     */
    public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException
    {
        IBMVmInfo vminfo = (IBMVmInfo)info;
        IBMDumpProvider helper = getDumpProvider(vminfo);
        // Delegate to the appropriate helper
        if (helper != this) return helper.acquireDump(info, preferredLocation, listener);

        listener.beginTask(Messages.getString("IBMDumpProvider.GeneratingDump"), TOTAL_WORK); //$NON-NLS-1$
        try
        {
            listener.subTask(MessageFormat.format(Messages.getString("IBMDumpProvider.AttachingToVM"), vminfo.getPidName())); //$NON-NLS-1$
            final VirtualMachine vm = VirtualMachine.attach(vminfo.getPidName());
            try
            {
                Properties props = vm.getSystemProperties();
                String javah = props.getProperty("java.home", System.getProperty("java.home")); //$NON-NLS-1$ //$NON-NLS-2$
                File javahome = new File(javah);

                // Where the dumps end up
                // IBM_HEAPDUMPDIR
                // IBM_JAVACOREDIR
                // pwd
                // TMPDIR
                // /tmp
                // user.dir
                // %LOCALAPPDATA%/VirtualStore/Program Files (x86)
                File udir;
                if (vminfo.dumpdir == null)
                {
                    String userdir = props.getProperty("user.dir", System.getProperty("user.dir")); //$NON-NLS-1$ //$NON-NLS-2$
                    udir = new File(userdir);
                }
                else
                {
                    udir = vminfo.dumpdir;
                }

                File f1[] = udir.listFiles();
                Collection<File> previous = new HashSet<File>(Arrays.asList(f1));

                long avg = averageFileSize(previous);
                //System.err.println("Average = " + avg);

                final String jar = getAgentJar().getAbsolutePath();

                listener.subTask(Messages.getString("IBMDumpProvider.StartingAgent")); //$NON-NLS-1$

                AgentLoader2 t = new AgentLoader(jar, vm, vminfo.agentCommand());
                t.start();

                List<File> newFiles = progress(udir, previous, files(), avg, t, listener);
                if (listener.isCanceled())
                {
                    t.interrupt();
                    return null;
                }
                if (t.failed())
                {
                    t.throwFailed(listener);
                }

                listener.done();
                return jextract(preferredLocation, vminfo.compress, newFiles, udir, javahome, listener);
            }
            catch (InterruptedException e)
            {
                listener.sendUserMessage(Severity.WARNING, Messages.getString("IBMDumpProvider.Interrupted"), e); //$NON-NLS-1$
                throw new SnapshotException(Messages.getString("IBMDumpProvider.Interrupted"), e); //$NON-NLS-1$
            }
            finally {
                vm.detach();
            }
        }
        catch (IOException e)
        {
            listener.sendUserMessage(Severity.WARNING, Messages.getString("IBMDumpProvider.UnableToGenerateDump"), e); //$NON-NLS-1$
            throw new SnapshotException(Messages.getString("IBMDumpProvider.UnableToGenerateDump"), e); //$NON-NLS-1$
        }
        // Catching AttachNotSupportedException stops the whole class loading if attach API is not present
        // so do a generic catch and pass through the others
        catch (SnapshotException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (/*AttachNotSupported*/Exception e)
        {
            info.setHeapDumpEnabled(false);
            listener.sendUserMessage(Severity.WARNING, Messages.getString("IBMDumpProvider.UnsuitableVM"), e); //$NON-NLS-1$
            throw new SnapshotException(Messages.getString("IBMDumpProvider.UnsuitableVM"), e); //$NON-NLS-1$
        }

    }

    /**
     * Update the progress bar for created files
     * @param loader Thread which has loaded the agent jar
     */
    private List<File> progress(File udir, Collection<File> previous, int nfiles, long avg, AgentLoader2 loader, IProgressListener listener)
                    throws InterruptedException
    {
        listener.subTask(Messages.getString("IBMDumpProvider.WaitingForDumpFiles")); //$NON-NLS-1$
        List<File> newFiles = new ArrayList<File>();
        // Wait up to 30 seconds for a file to be created and written to
        long l = 0;
        int worked = 0;
        long start = System.currentTimeMillis(), t;
        for (int i = 0; (l = fileLengths(udir, previous, newFiles, nfiles)) == 0 
            && i < CREATE_COUNT && (t = System.currentTimeMillis()) < start + CREATE_COUNT*SLEEP_TIMEOUT; ++i)
        {
            Thread.sleep(SLEEP_TIMEOUT);
            if (listener.isCanceled() || loader.failed()) 
                return null;
            int towork = (int)Math.min(((t - start) / SLEEP_TIMEOUT), CREATE_COUNT);
            listener.worked(towork - worked);
            worked = towork;
        }

        listener.worked(CREATE_COUNT - worked);
        worked = CREATE_COUNT;

        // Wait for FINISHED_TIMEOUT seconds after file length stops changing
        long l0 = l - 1;
        int iFile = 0;
        start = System.currentTimeMillis();
        for (int i = 0, j = 0; ((l = fileLengths(udir, previous, newFiles, nfiles)) != l0
                        || j++ < FINISHED_COUNT || newFiles.size() > iFile)
                        && i < GROW_COUNT
                        && (t = System.currentTimeMillis()) < start + GROW_COUNT*SLEEP_TIMEOUT; ++i)
        {
            while (iFile < newFiles.size())
            {
                listener.subTask(MessageFormat.format(Messages.getString("IBMDumpProvider.WritingFile"), newFiles.get(iFile++))); //$NON-NLS-1$
            }
            if (l0 != l)
            {
                j = 0;
                int towork = (int) (l * GROWING_COUNT / avg);
                listener.worked(towork - worked);
                worked = towork;
                l0 = l;
            }
            Thread.sleep(SLEEP_TIMEOUT);
            if (listener.isCanceled() || loader.failed())
                return null;
            listener.worked(1);
        }
        // Remove files which no longer exist
        for (Iterator<File>it = newFiles.iterator(); it.hasNext();)
        {
            File f = it.next();
            if (!f.exists())
                it.remove();
        }
        return newFiles;
    }

    private static synchronized File getAgentJar() throws IOException
    {
        if (agentJar == null || !agentJar.canRead())
        {
            agentJar = makeAgentJar();
        }
        return agentJar;
    }

    private static File makeAgentJar() throws IOException, FileNotFoundException
    {
        String jarname = "org.eclipse.mat.ibmdumps"; //$NON-NLS-1$
        String agents[] = { "org.eclipse.mat.ibmvm.agent.DumpAgent" }; //$NON-NLS-1$
        Class<?> cls[] = new Class<?>[0];
        return makeJar(jarname, "Agent-class: ", agents, cls); //$NON-NLS-1$
    }

    /**
     * Find the new files in a directory
     * 
     * @param udir
     *            The directory
     * @param previousFiles
     *            File that we already know exist in the directory
     * @param newFiles
     *            newly discovered files, in discovery/modification order
     * @return a list of new files in the directory
     */
    List<File> files(File udir, final Collection<File> previousFiles, List<File> newFiles)
    {
        File f2[] = udir.listFiles(new NewFileFilter(previousFiles));
        List<File> new2 = Arrays.asList(f2);
        // Sort the new files in order of modification
        Collections.sort(new2, new FileComparator());
        previousFiles.addAll(new2);
        newFiles.addAll(new2);
        return newFiles;
    }

    long fileLengths(File udir, Collection<File> previous, List<File> newFiles, int maxFiles)
    {
        Collection<File> nw = files(udir, previous, newFiles);
        long l = 0;
        int i = 0;
        for (File f : nw)
        {
            if (!f.exists())
            {
                // File has disappeared (e.g. on Linux renamed from core to core.????)
                // Just skip it and don't include in the count
                continue;
            }
            if (++i > maxFiles)
                break;
            l += f.length();
        }
        return l;
    }

    /**
     * @see org.eclipse.mat.snapshot.acquire.IHeapDumpProvider#getAvailableVMs(org.eclipse.mat.util.IProgressListener)
     */
    public List<IBMVmInfo> getAvailableVMs(IProgressListener listener)
    {
        try
        {
            return getAvailableVMs1();
        }
        catch (LinkageError e)
        {
            return null;
        }
    }
    
    private List<IBMVmInfo> getAvailableVMs1()
    {
        List<VirtualMachineDescriptor> list = VirtualMachine.list();
        List<IBMVmInfo> jvms = new ArrayList<IBMVmInfo>();
        for (VirtualMachineDescriptor vmd : list)
        {
            boolean usable = true;
            String dir = null;
            // See if the VM is usable to get dumps
            if (false)
            {
                try
                {
                    // Hope that this is not too intrusive to the target
                    VirtualMachine vm = vmd.provider().attachVirtualMachine(vmd);
                    try
                    {
                        Properties p = vm.getSystemProperties();
                        dir = p.getProperty("user.dir");
                    }
                    finally
                    {
                        vm.detach();
                    }
                }
                catch (AttachNotSupportedException e)
                {
                    usable = false;
                }
                catch (IOException e)
                {
                    usable = false;
                }
            }
            // See if loading an agent would fail
            try
            {
                // Java 5 SR10 and SR11 don't have a loadAgent method, so find
                // out now
                VirtualMachine.class.getMethod("loadAgent", String.class, String.class); //$NON-NLS-1$
            }
            catch (NoSuchMethodException e)
            {
                return null;
            }

            // Create VMinfo to generate heap dumps
            
            String desc = MessageFormat.format(Messages.getString("IBMDumpProvider.VMDescription"), vmd.provider().name(), vmd.provider().type(), vmd.displayName()); //$NON-NLS-1$
            IBMVmInfo ifo = new IBMVmInfo(vmd.id(), desc, usable, null, this);
            ifo.type = defaultType;
            ifo.compress = defaultCompress;
            if (dir != null)
                ifo.dumpdir = new File(dir);
            jvms.add(ifo);
            ifo.setHeapDumpEnabled(usable);
        }
        return jvms;
    }

    /**
     * Lists VMs or acquires a dump.
     * Used when attach API not usable from the MAT process.
     * 
     * @param s <ul><li>[0] dump type (Heap=heap+java,System=system)</li>
     *        <li>[1] VM id = PID</li>
     *        <li>[2] true/false compress dump</li>
     *        <li>[3] dump name</li>
     *        <li>[4] dump directory (optional)</li>
     *        </ul>
     * Output<ul>
     * <li>dump filename</li>
     * <li>or list of all processes (if argument list is empty)
     * <samp>PID;proposed file name;directory;description</samp></li>
     * </ul>
     */
    public static void main(String s[]) throws Exception
    {
        IBMDumpProvider prov = new IBMDumpProvider();
        List<IBMVmInfo> vms = prov.getAvailableVMs1();
        IProgressListener ii = new StderrProgressListener();
        for (VmInfo info : vms)
        {
            IBMVmInfo vminfo = (IBMVmInfo)info;
            String vm = vminfo.getPidName();
            String dir = vminfo.dumpdir != null ? vminfo.dumpdir.getAbsolutePath() : ""; 
            String vm2 = vm + INFO_SEPARATOR + info.getProposedFileName() + INFO_SEPARATOR + dir + INFO_SEPARATOR + info.getDescription();
            if (s.length < 4)
            {
                System.out.println(vm2);
            }
            else
            {
                if (vm.equals(s[1]))
                {
                    DumpType tp = DumpType.valueOf(s[0]);
                    vminfo.type = tp;
                    vminfo.compress = Boolean.parseBoolean(s[2]);
                    if (s.length > 4)
                    {
                        vminfo.dumpdir = new File(s[4]);
                    }
                    File f2 = info.getHeapDumpProvider().acquireDump(info, new File(s[3]), ii);
                    System.out.println(f2.getAbsolutePath());
                    return;
                }
            }
        }
        if (s.length > 1)
        {
            throw new IllegalArgumentException(MessageFormat.format(Messages.getString("IBMDumpProvider.NoVMFound"), s[1])); //$NON-NLS-1$
        }
    }
    
    IBMDumpProvider getDumpProvider(IBMVmInfo info)
    {
        if (getClass() != IBMDumpProvider.class)
           return this;
        else if (info.type == DumpType.SYSTEM)
            return new IBMSystemDumpProvider();
        else if (info.type == DumpType.HEAP)
            return new IBMHeapDumpProvider();
        else if (info.type == DumpType.JAVA)
            return new IBMJavaDumpProvider();
        return this;
    }

    /**
     * Update date and time stamps in suggested file name from actual file
     * @param preferredDump
     * @param actual
     * @return
     */
    File mergeFileNames(File preferredDump, File actual)
    {
        String fn1 = preferredDump.getName();
        String fn1a = fn1.replaceAll("\\d", "#"); //$NON-NLS-1$//$NON-NLS-2$
        String fn2 = actual.getName();
        String fn2a = fn2.replaceAll("\\d", "#"); //$NON-NLS-1$//$NON-NLS-2$
        fn2a = fn2a.substring(0, fn2a.lastIndexOf('#') + 1);
        int fi = fn1a.indexOf(fn2a);
        File ret;
        if (fi >= 0)
        {
            String newfn = fn1.substring(0, fi) + fn2.substring(0, fn2a.length()) + fn1.substring(fi + fn2a.length());
            ret = new File(preferredDump.getParentFile(), newfn);
        }
        else
        {
            ret = preferredDump;
        }
        return ret;
    }
}
