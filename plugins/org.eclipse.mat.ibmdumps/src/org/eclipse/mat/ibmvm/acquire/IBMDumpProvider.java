/*******************************************************************************
 * Copyright (c) 2010, 2021 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *    IBM Corporation/Andrew Johnson - Updates to use reflection for non-standard classes
 *    IBM Corporation/Andrew Johnson - Improved exception handling and hprof support
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;

//import com.ibm.tools.attach.AgentInitializationException;
//import com.ibm.tools.attach.AgentLoadException;
//import com.ibm.tools.attach.AgentNotSupportedException;
//import com.ibm.tools.attach.AttachOperationFailedException;
//import com.ibm.tools.attach.VirtualMachine;
//import com.ibm.tools.attach.VirtualMachineDescriptor;
//import com.ibm.tools.attach.spi.AttachProvider;

/**
 * Base class for generating dumps on IBM VMs.
 * This class uses reflection to call the com.ibm or com.sun classes.
 * Be sure to update IBMExecDumpProvider.getExecJar() when any classes are added here.
 * @author ajohnson
 *
 */
@Name("IBM Dump (using attach API)")
@Help("help for IBM Dump (using attach API)")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#task_acquiringheapdump__2")
public class IBMDumpProvider extends BaseProvider
{
    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class AgentLoadException extends Exception
    {
        private static final long serialVersionUID = 1L;

        /**
         * Construct the exception from the
         * com.ibm.tools.attach.AgentLoadException or
         * com.sun.tools.attach.AgentLoadException object.
         *
         * @param e
         */
        AgentLoadException(Throwable e)
        {
            super(e.getMessage());
            initCause(e);
        }
    }

    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class AgentInitializationException extends Exception
    {
        private static final long serialVersionUID = 1L;

        /*
         * Construct the exception from the
         * com.ibm.tools.attach.AgentInitializationException or
         * com.sun.tools.attach.AgentInitializationException object.
         */
        AgentInitializationException(Throwable e)
        {
            super(e.getMessage()+" returnValue="+VirtualMachine.call(e, "returnValue"));
            initCause(e);
        }

        int returnValue()
        {
            return (Integer)VirtualMachine.call(getCause(), "returnValue");
        }
    }

    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class AttachNotSupportedException extends Exception
    {
        private static final long serialVersionUID = 1L;

        /**
         * Construct the exception from the
         * com.ibm.tools.attach.AttachNotSupportedException or
         * com.sun.tools.attach.AttachNotSupportedException object.
         *
         * @param e
         */
        AttachNotSupportedException(Throwable e)
        {
            super(e.getMessage());
            initCause(e);
        }
    }

    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class AttachOperationFailedException extends IOException
    {
    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
        private static final long serialVersionUID = 1L;

        /**
         * Construct the exception from the
         * com.ibm.tools.attach.AttachNotSupportedException or
         * com.sun.tools.attach.AttachNotSupportedException object.
         *
         * @param e
         */
        AttachOperationFailedException (Throwable e)
        {
            super(e.getMessage());
            initCause(e);
        }
    }

    static class VirtualMachineDescriptor
    {
        String id;
        String displayName;
        /** A wrapper version of the provider */
        AttachProvider pr;
        /** The wrapped object */
        Object vmd;

        /**
         * Construct the exception from the
         * com.ibm.tools.attach.VirtualMachineDescriptor or
         * com.sun.tools.attach.VirtualMachineDescriptor object.
         *
         * @param vmd
         *            The object to wrap.
         */
        VirtualMachineDescriptor(Object vmd)
        {
            this.vmd = vmd;
            this.pr = new AttachProvider(VirtualMachine.call(vmd, "provider"));
            this.id = (String) VirtualMachine.call(vmd, "id");
            this.displayName = (String) (String) VirtualMachine.call(vmd, "displayName");
        }

        String displayName()
        {
            return displayName;
        }

        String id()
        {
            return id;
        }

        AttachProvider provider()
        {
            return pr;
        }
    }

    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class AttachProvider
    {
        /** The wrapper object */
        Object ap;

        /**
         * Construct a wrapper instance from from the com.ibm or com.sun
         * version: com.ibm.tools.attach.AttachProvider or
         * com.sun.tools.attach.AttachProvider
         *
         * @param o
         *            the object to wrap
         */
        AttachProvider(Object o)
        {
            ap = o;
        }

        /**
         * Attach to the virtual machine
         *
         * @param vmd
         *            A wrapped version of the descriptor
         * @return a wrapped version of the VirtualMachine
         * @throws IOException
         */
        VirtualMachine attachVirtualMachine(VirtualMachineDescriptor vmd)
                        throws IOException, AttachNotSupportedException
        {
            Object o;
            try
            {
                o = VirtualMachine.call(ap, "attachVirtualMachine", vmd.vmd);
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                // Change the type
                if (VirtualMachine.isSubclassOf(t, "AttachNotSupportedException"))
                {
                    throw new AttachNotSupportedException(t);
                }
                if (VirtualMachine.isSubclassOf(t, "AttachOperationFailedException"))
                {
                    throw new AttachOperationFailedException(t);
                }
                if (t instanceof IOException)
                {
                    // OpenJDK or OpenJ9 exceptions
                    if (t.getMessage().contains("not attach to current VM") || t.getMessage().contains("jdk.attach.allowAttachSelf"))
                    {
                        // Java 9/10 throws IOException instead of more useful AttachNotSupportedException
                        throw new AttachNotSupportedException(t);
                    }
                    throw (IOException)t;
                }
                throw e;
            }
            return new VirtualMachine(o);
        }

        String name()
        {
            return (String) VirtualMachine.call(ap, "name");
        }

        String type()
        {
            return (String) VirtualMachine.call(ap, "type");
        }

        private static Class<?>attCls;
        private static Class<?> getStaticClass() throws LinkageError
        {
            if (attCls == null)
            {
                attCls = VirtualMachine.getClass("com.ibm.tools.attach.spi.AttachProvider", "com.sun.tools.attach.spi.AttachProvider");
            }
            return attCls;
        }

        static List<AttachProvider>providers()
        {
            Class<?>apc = getStaticClass();
            List<?> l = (List<?>)VirtualMachine.call(apc, "providers");
            List<AttachProvider>ret = new ArrayList<AttachProvider>(l.size());
            for (Object o : l)
            {
                AttachProvider ap = new AttachProvider(o);
                ret.add(ap);
            }
            return ret;
        }

        List<VirtualMachineDescriptor>listVirtualMachines()
        {
            List<?> l = (List<?>)VirtualMachine.call(ap, "listVirtualMachines");
            List<VirtualMachineDescriptor>ret = new ArrayList<VirtualMachineDescriptor>(l.size());
            for (Object o : l)
            {
                VirtualMachineDescriptor vmd = new VirtualMachineDescriptor(o);
                ret.add(vmd);
            }
            return ret;
        }

        public String toString()
        {
            return name()+ " " + type();
        }

    }

    /**
     * Wrapper class for com.ibm.tools.attach/com.sun.tools.attach version.
     */
    static class VirtualMachine
    {
        /**
         * Helper for converting exceptions.
         *
         * @param e
         *            The exception
         * @param classname
         *            Detect if e or a superclass class has this name.
         * @return
         */
        static boolean isSubclassOf(Throwable e, String classname)
        {
            for (Class<?> o = e.getClass(); o != null; o = o.getSuperclass())
            {
                if (o.getSimpleName().equals(classname)) { return true; }
            }
            return false;
        }

        /**
         * Helper to call a method via reflection.
         *
         * @param o
         *            The object or null for static methods.
         * @param method
         *            The method name
         * @param args
         *            The arguments
         * @return
         */
        static Object call(Object o, String method, Object... args)
        {
            // Find the argument types.
            Class<?> types[] = new Class[args.length];
            for (int i = 0; i < args.length; ++i)
            {
                types[i] = args[i] != null ? args[i].getClass() : null;
            }
            Method m = null;
            Method ms[];
            Class<? extends Object> cls;
            // Presume a class object is for a static method
            if (o instanceof Class)
            {
                cls = ((Class<?>) o);
            }
            else
            {
                cls = o.getClass();
            }
            // Find a public class we can call methods from.
            // The ibm. is to exclude IBM Java 9 classes which are public but not accessible.
            // The sun. is to exclude Oracle Java 9 classes which are public but not accessible.
            // The com.ibm.tools.attach.attacher. is to exclude OpenJ9 Java 10 classes which are public but not accessible.
            // The org. is to exclude possible future OpenJ9 Java 10 classes which are public but not accessible.
            while (!Modifier.isPublic(cls.getModifiers()) || cls.getPackage().getName().startsWith("ibm.") || cls.getPackage().getName().startsWith("sun.")
                   || cls.getPackage().getName().startsWith("com.ibm.tools.attach.attacher") || cls.getPackage().getName().startsWith("org."))
            {
                cls = cls.getSuperclass();
            }
            ms = cls.getMethods();
            // Don't worry about interfaces for the moment
            l: for (Method m1 : ms)
            {
                int mods = m1.getModifiers();
                if (m1.getName().equals(method) && m1.getParameterTypes().length == types.length
                                && Modifier.isPublic(mods) && Modifier.isPublic(m1.getDeclaringClass().getModifiers()))
                {
                    // Match the parameters
                    for (int i = 0; i < types.length; ++i)
                    {
                        Class<?> t = m1.getParameterTypes()[i];
                        if (types[i] != null && !t.isAssignableFrom(types[i]))
                        {
                            continue l;
                        }
                    }
                    if (m == null)
                    {
                        m = m1;
                    }
                    else
                    {
                        // duplicate, so uncertain
                        m = null;
                        break;
                    }
                }
            }
            // Not found or duplicate, so do direct search and generate error if
            // needed
            if (m == null)
            {
                try
                {
                    if (o instanceof Class)
                    {
                        m = ((Class<?>) o).getMethod(method, types);
                    }
                    else
                    {
                        // This might not work if the argument is a superclass,
                        // or the parameter is an interface
                        m = o.getClass().getMethod(method, types);
                    }
                }
                catch (NoSuchMethodException e)
                {
                    // Fix up the error
                    LinkageError l = new LinkageError();
                    l.initCause(e);
                    throw l;
                }
            }
            Object ret;
            try
            {
                ret = m.invoke(o, args);
            }
            catch (IllegalAccessException e)
            {
                IllegalArgumentException e2 = new IllegalArgumentException("Object:" + o + " method:" + m + " exception:" + e);
                e2.initCause(e);
                throw e2;
            }
            catch (InvocationTargetException e)
            {
                if (e.getCause() instanceof RuntimeException)
                {
                    throw (RuntimeException)e.getCause();
                }
                else if (e.getCause() instanceof Error)
                {
                    throw (Error)e.getCause();
                }
                else
                {
                    throw new UndeclaredThrowableException(e.getCause());
                }
            }
            return ret;
        }

        /** The object which has been wrapped */
        Object vm;

        /**
         * Wrap a com.ibm.tools.attach.VirtualMachine or
         * com.sun.tools.attach.VirtualMachine object
         *
         * @param o
         */
        VirtualMachine(Object o)
        {
            vm = o;
        }

        /**
         * List all the VMs
         *
         * @return a list of wrapped versions of the VMs
         */
        static List<VirtualMachineDescriptor> list()
        {
            Class<?> c1 = getStaticClass();
            List<?> l = (List<?>) call(c1, "list");
            List<VirtualMachineDescriptor> ret = new ArrayList<VirtualMachineDescriptor>();
            for (Object o : l)
            {
                VirtualMachineDescriptor vmd1 = new VirtualMachineDescriptor(o);
                ret.add(vmd1);
            }
            return ret;
        }

        /**
         * Load an agent into the VM
         *
         * @param jar
         * @param command
         * @throws IOException
         * @throws AgentLoadException
         * @throws AgentInitializationException
         */
        public void loadAgent(String jar, String command)
                        throws IOException, AgentLoadException, AgentInitializationException
        {
            try
            {
                call(vm, "loadAgent", jar, command);
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                // Change the type
                if (isSubclassOf(t, "AgentLoadException")) { throw new AgentLoadException(t); }
                if (isSubclassOf(t, "AgentInitializationException")) { throw new AgentInitializationException(t); }
                if (t instanceof IOException) { throw (IOException)t; }
                // Rethrow
                throw e;
            }
        }

        /**
         * Load an agent into the VM
         *
         * @param lib executable library
         * @param command to pass to the library
         * @throws IOException if there is a problem with communication
         * @throws AgentLoadException1 if the library cannot be loaded
         * @throws AgentInitializationException1 if the command does not run properly
         */
        public void loadAgentLibrary(String lib, String command)
                        throws IOException, AgentLoadException, AgentInitializationException
        {
            try
            {
                call(vm, "loadAgentLibrary", lib, command);
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                // Change the type
                if (isSubclassOf(t, "AgentLoadException")) { throw new AgentLoadException(t); }
                if (isSubclassOf(t, "AgentInitializationException")) { throw new AgentInitializationException(t); }
                if (t instanceof IOException) { throw (IOException)t; }
                // Rethrow
                throw e;
            }
        }

        static VirtualMachine attach(String nm) throws IOException, AttachNotSupportedException
        {
            Class<?> c1 = getStaticClass();
            Object o;
            try
            {
                o = call(c1, "attach", nm);
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                if (isSubclassOf(t, "AttachNotSupportedException"))
                {
                    throw new AttachNotSupportedException(t);
                }
                if (t instanceof IOException)
                {
                    // OpenJDK or OpenJ9 exception
                    if (t.getMessage().contains("not attach to current VM") || t.getMessage().contains("jdk.attach.allowAttachSelf"))
                    {
                        // Java 9/10 throws IOException instead of more useful AttachNotSupportedException
                        throw new AttachNotSupportedException(t);
                    }
                    throw (IOException)t;
                }
                throw e;
            }
            return new VirtualMachine(o);
        }

        private static Class<?>clsVM;
        /**
         * Find out which version of the real class we are using.
         *
         * @return
         * @throws LinkageError
         */
        private static Class<?> getStaticClass() throws LinkageError
        {
            if (clsVM == null)
            {
                clsVM = getClass("com.ibm.tools.attach.VirtualMachine", "com.sun.tools.attach.VirtualMachine");
            }
            return clsVM;
        }

        static URLClassLoader urlcl;
        static Class<?> getClass(String cn1, String cn2) throws LinkageError
            {
            Class<?> c1;
            try
            {
                c1 = Class.forName(cn1);
            }
            catch (ClassNotFoundException e)
            {
                try
                {
                    c1 = Class.forName(cn2);
                }
                catch (ClassNotFoundException e2)
                {
                    /**
                     * Oracle-based VMs don't have com.sun.tools.attach classes on the
                     * standard class path, even for JDKs.
                     * We try looking for the classes here.
                     */
                    File f = new File(System.getProperty("java.home"));
                    f = f.getParentFile();
                    if (f != null)
                    {
                        f = new File(f, "lib");
                        f = new File(f, "tools.jar");
                        if (f.canRead())
                        {
                            try
                            {
                                if (urlcl == null)
                                {
                                    urlcl = new URLClassLoader(new URL[] {f.toURI().toURL()});
                                }
                                try
                                {
                                    return urlcl.loadClass(cn2);
                                }
                                catch (ClassNotFoundException e1)
                                {
                                }
                            }
                            catch (MalformedURLException e1)
                            {
                            }
                        }
                    }
                    LinkageError l = new LinkageError();
                    l.initCause(e2);
                    throw l;
                }
            }
            return c1;
        }

        Properties getAgentProperties() throws IOException
        {
            try
            {
                return (Properties) call(vm, "getAgentProperties");
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                if (t instanceof IOException) throw (IOException)t;
                throw e;
            }
        }

        Properties getSystemProperties() throws IOException
        {
            try
            {
                return (Properties) call(vm, "getSystemProperties");
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                if (t instanceof IOException) throw (IOException)t;
                throw e;
            }
        }

        void detach() throws IOException
        {
            try
            {
                call(vm, "detach");
            }
            catch (UndeclaredThrowableException e)
            {
                Throwable t = e.getCause();
                if (t instanceof IOException) throw (IOException)t;
                throw e;
            }
        }
    }

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

    public IBMDumpProvider()
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
     * @param preferredDump where the final dump should be put
     * @param compress Whether to compress/zip the dump
     * @param dumps The dump files
     * @param udir The directory where the dump files were generated
     * @param javahome The Java home directory of the process which produced the dump
     * @param listener to show progress
     * @return the result of post-processing the dump
     * @throws IOException
     * @throws InterruptedException
     * @throws SnapshotException
     */
    File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File javahome, IProgressListener listener)
                    throws IOException, InterruptedException, SnapshotException
    {
        File original = dumps.get(0);
        if (original.renameTo(preferredDump))
        {
            return preferredDump;
        }
        else
        {
            // @TODO consider java.nio.file.Files.move when we move to Java 1.7
            return original;
        }
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
        IBMVmInfo vminfo = info instanceof IBMVmInfo ? (IBMVmInfo)info : new IBMVmInfo(String.valueOf(info.getPid()), info.getDescription(), info.isHeapDumpEnabled(), info.getProposedFileName(), this);
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
                // %IBM_HEAPDUMPDIR%
                // %IBM_JAVACOREDIR%
                // %IBM_COREDIR%
                // pwd
                // TMPDIR
                // /tmp
                // /temp
                // user.dir
                // %LOCALAPPDATA%/VirtualStore/Program Files (x86)
                File udir;
                if (vminfo.dumpdir == null)
                {
                    String userdir = guessDumpLocation(props);
                    if (userdir != null)
                    {
                        udir = new File(userdir);
                    }
                    else
                    {
                        // If we do an HPROF dump we can put it directly where it is needed
                        // Also IBM dumps >= 1.7.1
                        udir = preferredLocation.getParentFile();
                    }
                    // Set the directory, so even it is fails the user could adjust it
                    vminfo.dumpdir = udir;
                    //System.err.println("Setting dumpdir "+udir);
                }
                else
                {
                    udir = vminfo.dumpdir;
                }

                File f1[] = udir.listFiles();
                if (f1 == null)
                {
                    throw new FileNotFoundException(udir.getPath());
                }
                Collection<File> previous = new HashSet<File>(Arrays.asList(f1));

                long avg = averageFileSize(previous);
                //System.err.println("Average = " + avg);

                final String jar = getAgentJar().getAbsolutePath();

                listener.subTask(Messages.getString("IBMDumpProvider.StartingAgent")); //$NON-NLS-1$

                AgentLoader2 t = new AgentLoader(jar, vm, vminfo.agentCommand(new File(udir, preferredLocation.getName())));
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
                if (newFiles.isEmpty())
                {
                    String msg = MessageFormat.format(Messages.getString("IBMDumpProvider.UnableToFindDump"), udir.getAbsoluteFile());
                    throw new FileNotFoundException(msg);
                }
                // Consider moving to after the detach
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
        catch (SnapshotException e)
        {
            throw e;
        }
        catch (AttachNotSupportedException e)
        {
            info.setHeapDumpEnabled(false);
            String msg = MessageFormat.format(Messages.getString("IBMDumpProvider.UnsuitableVM"), info.toString());
            listener.sendUserMessage(Severity.WARNING, msg, e); //$NON-NLS-1$
            throw new SnapshotException(msg, e); //$NON-NLS-1$
        }

    }

    /**
     * Update the progress bar for created files
     * @param udir directory where files are created
     * @param previous
     * @param nfiles
     * @param loader Thread which has loaded the agent jar
     * @throws FileNotFoundException
     */
    private List<File> progress(File udir, Collection<File> previous, int nfiles, long avg, AgentLoader2 loader, IProgressListener listener)
                    throws InterruptedException, FileNotFoundException
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
     * @throws FileNotFoundException
     */
    List<File> files(File udir, final Collection<File> previousFiles, List<File> newFiles) throws FileNotFoundException
    {
        File f2[] = udir.listFiles(new NewFileFilter(previousFiles));
        if (f2 == null)
        {
            throw new FileNotFoundException(udir.getPath());
        }
        List<File> new2 = Arrays.asList(f2);
        // Sort the new files in order of modification
        Collections.sort(new2, new FileComparator());
        previousFiles.addAll(new2);
        newFiles.addAll(new2);
        return newFiles;
    }

    long fileLengths(File udir, Collection<File> previous, List<File> newFiles, int maxFiles) throws FileNotFoundException
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
            return getAvailableVMs1(listener);
        }
        catch (LinkageError e)
        {
            return null;
        }
    }

    /**
     * List available VMs.
     * work done calculated as below:
     * T = 10
     * N = 100
     * X->1..100
     *
     * Step p = x*T/N - (x-1)*T/N
     *
     * @param listener
     * @return a list of VMs
     */
    private List<IBMVmInfo> getAvailableVMs1(IProgressListener listener)
    {
        int totalwork = 24;
        int provwork = 4;
        listener.beginTask(Messages.getString("IBMDumpProvider.ListingIBMVMs"), totalwork); //$NON-NLS-1$
        int y = 0;
        int vmcount = VirtualMachine.list().size();
        int x = 0;
        List<IBMVmInfo> jvms = new ArrayList<IBMVmInfo>();
        List<AttachProvider> provs = AttachProvider.providers();
        int provcount = provs.size();
        for (AttachProvider prov : provs)
        {
            listener.subTask(MessageFormat.format(Messages.getString("IBMDumpProvider.ListingFirst"), prov.name())); //$NON-NLS-1$
            List<VirtualMachineDescriptor> list = prov.listVirtualMachines();
            y++;
            int workp = y * provwork / provcount - (y - 1) * provwork / provcount;
            listener.worked(workp);
            listener.subTask(MessageFormat.format(Messages.getString("IBMDumpProvider.ListingDetails"), prov.name())); //$NON-NLS-1$
            for (VirtualMachineDescriptor vmd : list)
            {
                IBMVmInfo ifo = getVmInfo(vmd);
                jvms.add(ifo);
                ++x;
                int workv = x * (totalwork - provwork) / vmcount - (x - 1) * (totalwork - provwork) / vmcount;
                listener.worked(workv);
                if (listener.isCanceled())
                {
                    // If the user cancelled then perhaps the attach is hanging
                    listAttach = false;
                    break;
                }
            }
        }
        listener.done();
        return jvms;
    }

    private IBMVmInfo getVmInfo(VirtualMachineDescriptor vmd)
    {
        boolean usable = true;
        String unusableCause = "";
        String dir = null;
        // See if the VM is usable to get dumps
        String displayName = vmd.displayName();
        if ((vmd.id().equals(displayName) || "".equals(displayName)) && listAttach)
        {
            // Insufficient details of running VM, so attach for more information
            try
            {
                // Hope that this is not too intrusive to the target
                VirtualMachine vm = vmd.provider().attachVirtualMachine(vmd);
                try
                {
                    Properties p = vm.getSystemProperties();
                    dir = p.getProperty("user.dir");
                    // Get something which might identify the running VM to
                    // the user
                    displayName = p.getProperty("java.class.path");
                    if (displayName == null || displayName.equals(""))
                    {
                        displayName = dir;
                    }
                    dir = guessDumpLocation(p);
                }
                finally
                {
                    try
                    {
                        vm.detach();
                    }
                    catch (NullPointerException e)
                    {
                        // Ignore from IBM Java 9
                    }
                }
                // See if loading an agent would fail
                // Java 5 SR10 and SR11 don't have a loadAgent method, so find
                // out now
                try
                {
                    vm.loadAgent((String)null, (String)null);
                }
                catch (AgentLoadException e)
                {
                }
                catch (AgentInitializationException e)
                {
                }
                catch (LinkageError e)
                {
                    usable = false;
                    unusableCause = e.getLocalizedMessage();
                }
                catch (NullPointerException e)
                {
                    // Ignore
                }
                catch (IOException e)
                {
                    // Ignore, expect an IOException if the method exists as the VM is detached
                }
            }
            catch (IOException e)
            {
                usable = false;
                unusableCause = e.getLocalizedMessage();
            }
            catch (AttachNotSupportedException e)
            {
                usable = false;
                unusableCause = e.getLocalizedMessage();
            }
        }

        // Create VMinfo to generate heap dumps

        String desc = MessageFormat.format(Messages.getString("IBMDumpProvider.VMDescription"), vmd.provider().name(), vmd.provider().type(), displayName); //$NON-NLS-1$
        if (!usable)
        {
            desc = unusableCause + " : " + desc;
        }
        IBMVmInfo ifo = new IBMVmInfo(vmd.id(), desc, usable, null, this);
        if (vmd.provider().name().equals("sun"))
        {
            ifo.type = DumpType.HPROF;
            // No need for a dump directory - HPROF can dump directly to the required location
            dir = null;
        }
        else
        {
            ifo.type = defaultType;
        }
        ifo.live = defaultLive;
        ifo.compress = defaultCompress;
        if (dir != null)
            ifo.dumpdir = new File(dir);
        ifo.setHeapDumpEnabled(usable);
        return ifo;
    }

    /**
     * Guess the location the dump will go to, if the dump location cannot be set.
     * @param props
     * @return Guess for the dump location, or null it will be where we ask it to be.
     */
    private String guessDumpLocation(Properties props)
    {
        // Now need to guess version for versions IBM 1.7.0 and earlier
        // IBM 1.7.1 and later (=java version 1.7.0, vm.version 2.7) can dump at a location
        String vendor = props.getProperty("java.vm.vendor"); //$NON-NLS-1$
        if (!"IBM Corporation".equals(vendor))
            return null;
        String version = props.getProperty("java.version", "0"); //$NON-NLS-1$
        String vmversion = props.getProperty("java.vm.version"); //$NON-NLS-1$
        int comp170 = version.compareTo("1.7.0");
        if (comp170 > 0)
        {
            // 1.8 or later (9, 10, 11, etc.)
            return null;
        }
        if (comp170 == 0 && "2.7".equals(vmversion)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            // IBM 1.7.1
            return null;
        }
        String dir = props.getProperty("user.dir", System.getProperty("user.dir")); //$NON-NLS-1$ //$NON-NLS-2$
        // If there is a system trace file perhaps the dumps also do there
        String tracefilename = props.getProperty("system.trace.file"); //$NON-NLS-1$
        if (tracefilename != null)
        {
            File tracefile = (new File(tracefilename));
            File tdir = tracefile.getParentFile();
            if (tdir != null)
            {
                File tdira = tdir.getAbsoluteFile();
                if (tdir.equals(tdira))
                {
                    // Must be an absolute path, because otherwise could be different
                    // when examined from this process
                    dir = tdir.getPath();
                }
            }
        }
        return dir;
    }

    /**
     * Lists VMs or acquires a dump.
     * Used when attach API not usable from the MAT process.
     *
     * @param s <ul><li>[0] dump type (HEAP=heap+java,SYSTEM=system,JAVA=java)</li>
     *        <li>[1] VM id = PID</li>
     *        <li>[2] true/false live objects only in dump</li>
     *        <li>[3] true/false compress dump</li>
     *        <li>[4] dump name</li>
     *        <li>[5] dump directory (optional)</li>
     *        </ul>
     *        List VMs
     *        <ul>
     *        <li>true - attach to VM to get more details</li>
     *        </ul>
     * Output<ul>
     * <li>dump filename</li>
     * <li>or list of all processes (if argument list is empty)
     * <pre>PID;proposed file name;directory;enable dump;description</pre></li>
     * </ul>
     */
    public static void main(String s[]) throws Exception
    {
        IBMDumpProvider prov = new IBMDumpProvider();
        if (s.length < 5 && s.length > 0)
        {
            prov.listAttach = Boolean.parseBoolean(s[0]);
        }
        IProgressListener ii = new StderrProgressListener();
        List<IBMVmInfo> vms = prov.getAvailableVMs1(ii);
        for (VmInfo info : vms)
        {
            IBMVmInfo vminfo = (IBMVmInfo)info;
            String vm = vminfo.getPidName();
            String dir = vminfo.dumpdir != null ? vminfo.dumpdir.getAbsolutePath() : "";
            String proposedFile = info.getProposedFileName();
            // Let the file be determined later
            proposedFile = "";
            String vm2 = vm + INFO_SEPARATOR + info.isHeapDumpEnabled() + INFO_SEPARATOR + vminfo.type + INFO_SEPARATOR + proposedFile + INFO_SEPARATOR + dir + INFO_SEPARATOR  + info.getDescription();
            if (s.length < 5)
            {
                System.out.println(vm2);
            }
            else
            {
                if (vm.equals(s[1]))
                {
                    DumpType tp = DumpType.valueOf(s[0]);
                    vminfo.type = tp;
                    vminfo.live = Boolean.parseBoolean(s[2]);
                    vminfo.compress = Boolean.parseBoolean(s[3]);
                    if (s.length > 5)
                    {
                        vminfo.dumpdir = new File(s[5]);
                    }
                    File f2 = vminfo.getHeapDumpProvider().acquireDump(info, new File(s[4]), ii);
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
        else if (info.type == DumpType.HPROF)
            return new HprofDumpProvider();
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
