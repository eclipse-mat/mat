/*******************************************************************************
 * Copyright (c) 2009, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - message fixes, gzip
 *******************************************************************************/
package org.eclipse.mat.hprof.acquire;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.ChunkedGZIPRandomAccessFile;
import org.eclipse.mat.hprof.Messages;
import org.eclipse.mat.hprof.acquire.LocalJavaProcessesUtils.StreamCollector;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#task_acquiringheapdump__1")
public class JMapHeapDumpProvider implements IHeapDumpProvider
{

    private static final String PLUGIN_ID = "org.eclipse.mat.hprof"; //$NON-NLS-1$
    private static final String LAST_JDK_DIRECTORY_KEY = JMapHeapDumpProvider.class.getName() + ".lastJDKDir"; //$NON-NLS-1$
    private static final String LAST_JMAP_JDK_DIRECTORY_KEY = JMapHeapDumpProvider.class.getName() + ".lastJmapJDKDir"; //$NON-NLS-1$
    static final String FILE_PATTERN = "java_pid{1,number,0}.{2,number,0000}.hprof"; //$NON-NLS-1$
    static final String FILE_GZ_PATTERN = "java_pid{1,number,0}.{2,number,0000}.hprof.gz"; //$NON-NLS-1$
    /** Used for the progress monitor for listing processes */
    private int lastCount = 20;

    @Argument(isMandatory = false, advice = Advice.DIRECTORY)
    public File jdkHome;
    @Argument(isMandatory = false, advice = Advice.DIRECTORY)
    public List<File> jdkList;

    @Argument(isMandatory = false)
    public boolean defaultCompress;

    @Argument(isMandatory = false)
    public boolean defaultChunked;

    @Argument(isMandatory = false)
    public boolean defaultLive = true;

    public JMapHeapDumpProvider()
    {
        // initialize JDK from previously saved data
        jdkHome = readSavedLocation(LAST_JDK_DIRECTORY_KEY);

        // No user settings saved -> check if a JDT VM is a JDK
        if (jdkHome == null)
        {
            jdkHome = guessJDKFromJDT();
        }
        else
        {
            guessJDKFromJDT();
        }
        // No user settings saved -> check if current java.home is a JDK
        if (jdkHome == null)
        {
            jdkHome = guessJDK();
        }
        else
        {
            guessJDK();
        }
        if (jdkHome == null)
        {
            jdkHome = guessJDKFromPath();
        }
        else
        {
            guessJDKFromPath();
        }
    }

    public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener) throws SnapshotException
    {
        JmapVmInfo jmapProcessInfo;
        if (info instanceof JmapVmInfo)
            jmapProcessInfo = (JmapVmInfo) info;
        else
        {
            jmapProcessInfo = new JmapVmInfo(info.getPid(), info.getDescription(), info.isHeapDumpEnabled(), info.getProposedFileName(), info.getHeapDumpProvider());
        }
        listener.beginTask(Messages.JMapHeapDumpProvider_WaitForHeapDump, IProgressMonitor.UNKNOWN);
        // jcmd is preferred, but non-live (-all) is not supported on Java 8 
        final String modules1[] = {"jcmd", "jcmd.exe", "jmap", "jmap.exe"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        final String modules2[] = {"jmap", "jmap.exe", "jcmd", "jcmd.exe"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        final String modules[] = jmapProcessInfo.live ? modules1 : modules2;
        // just use jmap by default ...
        String jmap = "jmap"; //$NON-NLS-1$
        String cmd = jmap;
        if (jmapProcessInfo.jdkHome != null && jmapProcessInfo.jdkHome.exists())
        {
            for (String mod : modules)
            {
                File mod1 = new File(jmapProcessInfo.jdkHome.getAbsoluteFile(), "bin"); //$NON-NLS-1$
                mod1 = new File(mod1, mod);
                if (mod1.canExecute())
                {
                    jmap = mod1.getAbsolutePath();
                    cmd = mod;
                    // Found it, so remember the location
                    persistJDKLocation(LAST_JMAP_JDK_DIRECTORY_KEY, jmapProcessInfo.jdkHome.getAbsolutePath());
                    break;
                }
            }
        }
        // build the line to execute as a String[] because quotes in the name cause
        // problems on Linux - See bug 313636
        String[] execLine = cmd.startsWith("jcmd") ?  //$NON-NLS-1$
                        (jmapProcessInfo.live ?
                                        new String[] { jmap, // jcmd command
                                                        String.valueOf(info.getPid()),
                                                        "GC.heap_dump",  //$NON-NLS-1$
                                                        preferredLocation.getAbsolutePath()}
                        :
                            // -all option is not recognized by Java 8
                            new String[] { jmap, // jcmd command
                                            String.valueOf(info.getPid()),
                                            "GC.heap_dump",  //$NON-NLS-1$
                                            "-all", //$NON-NLS-1$
                                            preferredLocation.getAbsolutePath()}
                                        )
                        :
                            new String[] { jmap, // jmap command
                                            "-dump:" + (jmapProcessInfo.live ? "live," : "") + "format=b,file=" + preferredLocation.getAbsolutePath(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ 
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

        Logger.getLogger(getClass().getName()).info(logMessage.toString());
        listener.subTask(jmap);
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
                throw new SnapshotException(MessageUtil.format(Messages.JMapHeapDumpProvider_ErrorCreatingDump, exitCode, error.buf.toString(), jmap));
            }

            if (!preferredLocation.exists())
            {
                throw new SnapshotException(MessageUtil.format(Messages.JMapHeapDumpProvider_HeapDumpNotCreated, exitCode, output.buf.toString(), error.buf.toString(), jmap));
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
        if (jmapProcessInfo.compress)
        {
            try
            {
                preferredLocation = compressFile(preferredLocation, jmapProcessInfo.chunked, listener);
            }
            catch (IOException e)
            {
                throw new SnapshotException(Messages.JMapHeapDumpProvider_ErrorCreatingDump, e);
            }
        }

        listener.done();

        return preferredLocation;
    }

    File compressFile(File dump, boolean chunked, IProgressListener listener) throws IOException
    {
        listener.subTask(Messages.JMapHeapDumpProvider_CompressingDump);
        File dumpout = File.createTempFile(dump.getName(), null, dump.getParentFile());

        if (chunked)
        {
            ChunkedGZIPRandomAccessFile.compressFileChunked(dump, dumpout);
        }
        else
        {
            int bufsize = 64 * 1024;
            try (FileInputStream in = new FileInputStream(dump);
                            InputStream inb = new BufferedInputStream(in, bufsize);
                            FileOutputStream out = new FileOutputStream(dumpout);
                            GZIPOutputStream outb = new GZIPOutputStream(out, bufsize))
            {
                byte b[] = new byte[bufsize];
                for (;;)
                {
                    if (listener.isCanceled())
                        return null;
                    int r = in.read(b);
                    if (r <= 0)
                        break;
                    outb.write(b, 0, r);
                }
                outb.flush();
            }
        }
        if (dump.delete())
        {
            if (!dumpout.renameTo(dump))
            {
                throw new IOException(dump.getPath());
            }
        }
        else
        {
            if (!dumpout.delete())
            {
                throw new IOException(dumpout.getPath());
            }
            // Return uncompressed
        }
        return dump;
    }

    public List<JmapVmInfo> getAvailableVMs(IProgressListener listener) throws SnapshotException
    {
        listener.beginTask(Messages.JMapHeapDumpProvider_ListProcesses, lastCount);
        // was something injected from outside?
        if (jdkList == null || jdkList.isEmpty())
        {
            // Repopulate the list
            guessJDKFromJDT();
            guessJDK();
            guessJDKFromPath();
            if (jdkHome == null && jdkList != null && !jdkList.isEmpty())
                jdkHome = jdkList.get(0);
        }
        if (jdkHome != null && jdkHome.exists())
        {
            // If the jdk directory has changed, clear the jmap directory
            File old = readSavedLocation(LAST_JDK_DIRECTORY_KEY);
            if (!(old != null && jdkHome.getAbsoluteFile().equals(old)))
                persistJDKLocation(LAST_JMAP_JDK_DIRECTORY_KEY, null);
            persistJDKLocation(LAST_JDK_DIRECTORY_KEY, jdkHome.getAbsolutePath());
        }

        List<JmapVmInfo> result = new ArrayList<JmapVmInfo>();
        List<JmapVmInfo> jvms = LocalJavaProcessesUtils.getLocalVMsUsingJPS(jdkHome, listener);
        if (jvms != null)
        {
            lastCount = jvms.size();
            if (jdkHome == null)
                persistJDKLocation(LAST_JDK_DIRECTORY_KEY, null);
            // try to get jmap specific location for the JDK
            File jmapJdkHome = readSavedLocation(LAST_JMAP_JDK_DIRECTORY_KEY);
            if (jmapJdkHome == null) jmapJdkHome = this.jdkHome;

            for (JmapVmInfo vmInfo : jvms)
            {
                //vmInfo.setProposedFileName(defaultCompress ? FILE_GZ_PATTERN : FILE_PATTERN);
                vmInfo.setHeapDumpProvider(this);
                vmInfo.jdkHome = jmapJdkHome;
                vmInfo.compress = defaultCompress;
                vmInfo.chunked = defaultChunked;
                vmInfo.live = defaultLive;
                result.add(vmInfo);
            }
        }
        listener.done();
        return result;
    }

    private void persistJDKLocation(String key, String value)
    {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PLUGIN_ID);
        if (value == null)
            prefs.remove(key);
        else
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

    private File guessJDKFromJDT()
    {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.launching"); //$NON-NLS-1$
        if (prefs == null)
            return null;
        String s1 = prefs.get("org.eclipse.jdt.launching.PREF_VM_XML", null); //$NON-NLS-1$
        if (s1 == null)
            return null;
        try
        {
            List<File> paths = parseJDTvmSettings(s1);
            jdkList = paths;
            if (paths.size() >= 1)
                return paths.get(0);
            return null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static final String modules[] = {"jcmd", "jcmd.exe", "jps", "jps.exe"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private File guessJDK()
    {
        String javaHomeProperty = System.getProperty("java.home"); //$NON-NLS-1$
        File folders[] = new File[2];
        folders[0] = new File(javaHomeProperty);
        folders[1] = folders[0].getParentFile();
        for (File parentFolder : folders)
        {
            File binDir = new File(parentFolder, "bin"); //$NON-NLS-1$
            if (binDir.exists())
            {
                // See if jps is present
                for (String mod : modules)
                {
                    File dll = new File(binDir, mod);
                    if (dll.canExecute())
                    {
                        if (jdkList != null && !jdkList.contains(parentFolder))
                            jdkList.add(parentFolder);
                        return parentFolder;
                    }
                }
            }
        }
        return null;
    }

    private File guessJDKFromPath()
    {
        File jdkHome = null;
        String path = System.getenv("PATH"); //$NON-NLS-1$
        if (path != null)
        {
            for (String p : path.split(File.pathSeparator))
            {
                File dir = new File(p);
                File parentDir = dir.getParentFile();
                // See if jps is present
                for (String mod : modules)
                {
                    File dll = new File(dir, mod);
                    if (dll.canExecute())
                    {
                        if (parentDir != null && parentDir.getName().equals("bin")) //$NON-NLS-1$
                        {
                            File home = parentDir.getParentFile();
                            if (jdkHome == null)
                                jdkHome = home;
                            if (jdkList != null && !jdkList.contains(home))
                                jdkList.add(home);
                        }
                    }
                }
            }
        }
        return jdkHome;
    }

    // //////////////////////////////////////////////////////////////
    // XML reading
    // //////////////////////////////////////////////////////////////

    private static final List<File> parseJDTvmSettings(String input) throws IOException
    {
        try
        {
            JDTLaunchingSAXHandler handler = new JDTLaunchingSAXHandler();
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser = parserFactory.newSAXParser();
            XMLReader saxXmlReader =  parser.getXMLReader();
            saxXmlReader.setContentHandler(handler);
            saxXmlReader.setErrorHandler(handler);
            saxXmlReader.parse(new InputSource(new StringReader(input)));
            List<File>homes = new ArrayList<File>();
            for (String home : handler.getJavaHome())
            {
                File homedir = new File(home);
                File dir = new File(homedir, "bin"); //$NON-NLS-1$
                if (dir.exists())
                {
                    // See if jps is present
                    for (String mod : modules)
                    {
                        File jps = new File(dir, mod);
                        if (jps.canExecute())
                        {
                            homes.add(homedir);
                            break;
                        }
                    }
                }
            }
            return homes;
        }
        catch (SAXException e)
        {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
        catch (ParserConfigurationException e)
        {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
    }

    private static class JDTLaunchingSAXHandler extends DefaultHandler
    {
        List<String>javaHomes = new ArrayList<String>();
        private String defVM;
        private String type;
        private JDTLaunchingSAXHandler()
        {
        }
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if (name.equals("vmSettings")) //$NON-NLS-1$
            {
                // Find the default VM
                String settings = attributes.getValue("defaultVM"); //$NON-NLS-1$
                if (settings != null)
                {
                    String s[] = settings.split(","); //$NON-NLS-1$
                    if (s.length >= 3)
                        defVM = s[2];
                }
            }
            else if (name.equals("vmType")) //$NON-NLS-1$
            {
                // Used to check of the right type
                type = attributes.getValue("id"); //$NON-NLS-1$
            }
            else if (name.equals("vm") && "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType".equals(type)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                String id = attributes.getValue("id"); //$NON-NLS-1$
                String path = attributes.getValue("path"); //$NON-NLS-1$
                if (path != null)
                {
                    if (id != null && id.equals(defVM))
                    {
                        // Add the default to the front of the list
                        javaHomes.add(0, path);
                    }
                    else
                    {
                        javaHomes.add(path);
                    }
                }
            }
        }

        public List<String> getJavaHome() {
            return javaHomes;
        }
    }
}
