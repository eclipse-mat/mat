/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - clean up snapshots better
 *******************************************************************************/
package org.eclipse.mat.tests;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;

public class TestSnapshots
{
    public static final String SUN_JDK5_64BIT = "dumps/sun_jdk5_64bit.hprof";
    public static final String SUN_JDK6_32BIT = "dumps/sun_jdk6_32bit.hprof";
    public static final String SUN_JDK6_18_32BIT = "dumps/sun_jdk6_18_x32.hprof";
    public static final String SUN_JDK6_18_64BIT = "dumps/sun_jdk6_18_x64.hprof";
    public static final String SUN_JDK6_30_64BIT_COMPRESSED_OOPS = "dumps/sun_jdk6_30_x64_compressedOops.hprof";
    public static final String SUN_JDK6_30_64BIT_NOCOMPRESSED_OOPS = "dumps/sun_jdk6_30_x64_nocompressedOops.hprof";
    public static final String SUN_JDK6_31_64BIT_HPROFAGENT_NOCOMPRESSED_OOPS = "dumps/sun_jdk6_31_hprofagent_nocompressedOops.hprof";
    public static final String SUN_JDK6_31_64BIT_HPROFAGENT_COMPRESSED_OOPS = "dumps/sun_jdk6_31_hprofagent_compressedOops.hprof";
    public static final String SUN_JDK5_13_32BIT = "dumps/sun_jdk5_13_x32.hprof";
    public static final String ORACLE_JDK7_21_64BIT_HPROFAGENT = "dumps/oracle_jdk7_21_hprofagent.hprof";
    public static final String ORACLE_JDK7_21_64BIT = "dumps/oracle_jdk7_21_x64.hprof";
    public static final String ORACLE_JDK7_75_64BIT_DIRECT_MEMORY = "dumps/oracle_jdk7_75_x64_directmemory.hprof";
    public static final String ORACLE_JDK8_05_64BIT = "dumps/oracle_jdk8_05_x64.hprof";
    public static final String HISTOGRAM_SUN_JDK6_18_32BIT = "dumps/histogram_sun_jdk6_18_x32.txt";
    public static final String HISTOGRAM_SUN_JDK5_13_32BIT = "dumps/histogram_sun_jdk5_13_x32.txt";
    public static final String HISTOGRAM_SUN_JDK6_18_64BIT = "dumps/histogram_sun_jdk6_18_x64.txt";
    public static final String HISTOGRAM_SUN_JDK6_30_64BIT_COMPRESSED_OOPS = "dumps/sun_jdk6_30_x64_compressedOops.notes.txt";
    public static final String HISTOGRAM_SUN_JDK6_30_64BIT_NOCOMPRESSED_OOPS = "dumps/sun_jdk6_30_x64_nocompressedOops.notes.txt";
    public static final String HISTOGRAM_SUN_JDK6_31_64BIT_HPROFAGENT_NOCOMPRESSED_OOPS = "dumps/sun_jdk6_31_hprofagent_nocompressedOops.txt";
    public static final String HISTOGRAM_SUN_JDK6_31_64BIT_HPROFAGENT_COMPRESSED_OOPS = "dumps/sun_jdk6_31_hprofagent_compressedOops.txt";
    public static final String IBM_JDK6_32BIT_SYSTEM = "dumps/core.20100112.141124.11580.0001.dmp.zip";
    public static final String IBM_JDK6_32BIT_HEAP = "dumps/heapdump.20100112.141124.11580.0002.phd";
    public static final String IBM_JDK6_32BIT_JAVA = "dumps/javacore.20100112.141124.11580.0003.txt";
    public static final String IBM_JDK7_64BIT_SYSTEM = "dumps/core.20130429.083124.14315.0001.dmp.zip";
    public static final String IBM_JDK7_64BIT_HEAP = "dumps/heapdump.20130429.083110.14261.0001.phd";
    public static final String IBM_JDK7_64BIT_JAVA = "dumps/javacore.20130429.083110.14261.0002.txt";
    public static final String IBM_JDK8_64BIT_SYSTEM = "core.20140428.143134.2056.0001.dmp.zip";
    /**
     * javacore provides extra thread and class loader information to a heap
     * dump
     */
    public static final String IBM_JDK6_32BIT_HEAP_AND_JAVA = IBM_JDK6_32BIT_HEAP + ";" + IBM_JDK6_32BIT_JAVA;
    public static final String IBM_JDK7_64BIT_HEAP_AND_JAVA = IBM_JDK7_64BIT_HEAP + ";" + IBM_JDK7_64BIT_JAVA;
    public static final String IBM_JDK142_32BIT_SYSTEM = "dumps/core.20100209.165717.4484.txt.sdff";
    public static final String IBM_JDK142_32BIT_HEAP = "dumps/heapdump.20100209.165721.4484.phd";
    public static final String IBM_JDK142_32BIT_JAVA = "dumps/javacore.20100209.165721.4484.txt";
    public static final String IBM_JDK142_32BIT_HEAP_AND_JAVA = IBM_JDK142_32BIT_HEAP + ";" + IBM_JDK142_32BIT_JAVA;

    private static DirDeleter deleterThread;
    private static Map<String, ISnapshot> snapshots = new HashMap<String, ISnapshot>();
    private static List<ISnapshot> pristineSnapshots = new ArrayList<ISnapshot>();

    static
    {
        deleterThread = new DirDeleter();
        Runtime.getRuntime().addShutdownHook(deleterThread);
    }

    public static ISnapshot getSnapshot(String name, boolean pristine)
    {
        return getSnapshot(name, new HashMap<String, String>(), pristine);
    }

    /**
     * Get a snapshot from a dump
     * 
     * @param name
     *            Name or names of dump, separated by semicolon
     * @param options
     * @param pristine
     *            If true, return a brand new snapshot, not a cached version
     * @return
     */
    public static ISnapshot getSnapshot(String dumpname, Map<String, String> options, boolean pristine)
    {
        try
        {
            testAssertionsEnabled();

            if (!pristine)
            {
                ISnapshot answer = snapshots.get(dumpname);
                if (answer != null)
                    return answer;
            }

            String names[] = dumpname.split(";");
            String name = names[0];
            File sourceHeapDump = getResourceFile(name);

            int index = name.lastIndexOf('.');
            String prefix = name.substring(0, index + 1);
            String addonsName = prefix + "addons";
            File sourceAddon = getResourceFile(addonsName);

            assert sourceHeapDump != null : "Unable to find snapshot resource: " + name;
            assert sourceHeapDump.exists();

            // Extract the file name, should work with slash and backslash separators
            String nm = new File(name).getName();

            File directory = TestSnapshots.createGeneratedName("junit", null);
            File snapshot = new File(directory, nm);
            copyFile(sourceHeapDump, snapshot);

            if (sourceAddon != null && sourceAddon.exists())
            {
                File addon = new File(directory, sourceAddon.getName());
                copyFile(sourceAddon, addon);
            }

            // Extra dump files
            for (int i = 1; i < names.length; ++i)
            {
                // Identifier used to create a unique dump
                if (names[i].startsWith("#"))
                    continue;
                File extraDump = getResourceFile(names[i]);
                assert extraDump != null : "Unable to find snapshot resource: " + names[i];
                assert extraDump.exists();
                nm = new File(names[i]).getName();
                File extraSnapshot = new File(directory, nm);
                copyFile(extraDump, extraSnapshot);
            }

            ISnapshot answer = SnapshotFactory.openSnapshot(snapshot, options, new VoidProgressListener());
            if (pristine)
            {
                pristineSnapshots.add(answer);
            }
            else
            {
                snapshots.put(dumpname, answer);
            }
            return answer;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static File getResourceFile(String name)
    {
        final int BUFSIZE = 2048;
        URL url = TestSnapshots.class.getClassLoader().getResource(name);
        File file = null;
        if (url == null)
        {
            file = getResourceFromWorkspace(name);
        }
        else if ("file".equals(url.getProtocol()))
        {
            file = new File(url.getFile());
        }
        else if ("jar".equals(url.getProtocol()))
        {
            try
            {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                JarFile jarFile = conn.getJarFile();
                JarEntry jarEntry = conn.getJarEntry();
                InputStream is = jarFile.getInputStream(jarEntry);
                String entryName = conn.getEntryName();
                String tmpDirName = System.getProperty("java.io.tmpdir");
                File tmpDir = new File(tmpDirName, "jdtd");
                file = new File(tmpDir, entryName);
                File parent = file.getParentFile();
                parent.mkdirs();

                copyStreamToFile(is, file, BUFSIZE);
                file.deleteOnExit();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else if ("bundleresource".equals(url.getProtocol()))
        {
            try
            {
                URLConnection connection = url.openConnection();
                InputStream is = connection.getInputStream();

                String tmpDirName = System.getProperty("java.io.tmpdir");
                File tmpDir = new File(tmpDirName, "jdtd");
                file = new File(tmpDir, name);
                File parent = file.getParentFile();
                parent.mkdirs();

                copyStreamToFile(is, file, BUFSIZE);
                file.deleteOnExit();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        return file;
    }

    private static void copyStreamToFile(InputStream is, File file, final int BUFSIZE) throws IOException
    {
        int count;
        byte data[] = new byte[BUFSIZE];
        FileOutputStream fos = new FileOutputStream(file);
        try
        {
            BufferedOutputStream bos = new BufferedOutputStream(fos, BUFSIZE);
            try
            {
                while ((count = is.read(data, 0, BUFSIZE)) != -1)
                    bos.write(data, 0, count);
            }
            finally
            {
                bos.close();
            }
        }
        catch (IOException e)
        {
            if (!file.delete())
            {
                System.out.println("Unable to delete " + file);
            }
            throw e;
        }
    }

    /* test if assertions are enabled */
    public static void testAssertionsEnabled()
    {
        boolean assertsEnabled = false;
        assert assertsEnabled = true; // Intentional side effect!!!
        if (!assertsEnabled)
            throw new RuntimeException(
                            "Assertions are switched off at runtime (add VM parameter -ea to enable assertions)!");
    }

    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////

    private static File getResourceFromWorkspace(String name)
    {
        File file = new File(name);
        if (!file.exists())
        {
            file = null;
        }
        return file;
    }

    public static File createGeneratedName(String prefix, File directory) throws IOException
    {
        File tempFile = File.createTempFile(prefix, "", directory);
        if (!tempFile.delete())
            throw new IOException();
        if (!tempFile.mkdir())
            throw new IOException();
        deleterThread.add(tempFile);
        return tempFile;
    }

    private static void copyFile(File in, File out) throws IOException
    {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(in);
            FileChannel sourceChannel = fis.getChannel();
            try
            {
                FileOutputStream fos = null;
                try
                {
                    fos = new FileOutputStream(out);
                    FileChannel destinationChannel = fos.getChannel();
                    try
                    {
                        sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
                    }
                    finally
                    {
                        destinationChannel.close();
                    }
                }
                finally
                {
                    if (fos != null)
                        fos.close();
                }
            }
            catch (IOException e)
            {
                if (!out.delete())
                {
                    System.err.println("Unable to delete " + out);
                }
                throw e;
            }
            finally
            {
                sourceChannel.close();
            }
        }
        finally
        {
            if (fis != null)
                fis.close();
        }
    }

    private static class DirDeleter extends Thread
    {
        private final List<File> dirList = new ArrayList<File>();

        public synchronized void add(File dir)
        {
            dirList.add(dir);
        }

        @Override
        public void run()
        {
            synchronized (this)
            {
                for (ISnapshot sn : snapshots.values())
                {
                    SnapshotFactory.dispose(sn);
                }
                for (ISnapshot sn : pristineSnapshots)
                {
                    SnapshotFactory.dispose(sn);
                }
                for (File dir : dirList)
                    deleteDirectory(dir);
            }
        }

        private void deleteDirectory(File dir)
        {
            File[] fileArray = dir.listFiles();

            if (fileArray != null)
            {
                for (int i = 0; i < fileArray.length; i++)
                {
                    if (fileArray[i].isDirectory())
                        deleteDirectory(fileArray[i]);
                    else
                    {
                        if (!fileArray[i].delete())
                        {
                            System.err.println("Unable to delete " + fileArray[i]);
                        }
                    }
                }
            }

            if (!dir.delete())
            {
                System.err.println("Unable to delete " + dir);
            }
        }
    }

}
