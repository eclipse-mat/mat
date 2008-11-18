/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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

    private static DirDeleter deleterThread;
    private static Map<String, ISnapshot> snapshots = new HashMap<String, ISnapshot>();

    static
    {
        deleterThread = new DirDeleter();
        Runtime.getRuntime().addShutdownHook(deleterThread);
    }

    public static ISnapshot getSnapshot(String name, boolean pristine)
    {
        try
        {
            testAssertionsEnabled();

            if (!pristine)
            {
                ISnapshot answer = snapshots.get(name);
                if (answer != null)
                    return answer;
            }

            File sourceHeapDump = getResourceFile(name);

            int index = name.lastIndexOf('.');
            String prefix = name.substring(0, index + 1);
            String addonsName = prefix + "addons";
            File sourceAddon = getResourceFile(addonsName);

            assert sourceHeapDump != null : "Unable to find snapshot resource: " + name;
            assert sourceHeapDump.exists();

            int p = name.lastIndexOf('/');

            File directory = TestSnapshots.createGeneratedName("junit", null);
            File snapshot = new File(directory, name.substring(p + 1));
            copyFile(sourceHeapDump, snapshot);

            if (sourceAddon != null && sourceAddon.exists())
            {
                File addon = new File(directory, addonsName.substring(p + 1));
                copyFile(sourceAddon, addon);
            }

            ISnapshot answer = SnapshotFactory.openSnapshot(snapshot, new VoidProgressListener());
            snapshots.put(name, answer);
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

                int count;
                byte data[] = new byte[BUFSIZE];
                FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFSIZE);
                while ((count = is.read(data, 0, BUFSIZE)) != -1)
                    bos.write(data, 0, count);

                bos.flush();
                bos.close();
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

                int count;
                byte data[] = new byte[BUFSIZE];
                FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFSIZE);
                while ((count = is.read(data, 0, BUFSIZE)) != -1)
                    bos.write(data, 0, count);

                bos.flush();
                bos.close();
                file.deleteOnExit();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        return file;
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

    private static File createGeneratedName(String prefix, File directory) throws IOException
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
        FileChannel sourceChannel = new FileInputStream(in).getChannel();
        FileChannel destinationChannel = new FileOutputStream(out).getChannel();
        sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
        sourceChannel.close();
        destinationChannel.close();
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
                        fileArray[i].delete();
                }
            }

            dir.delete();
        }
    }

}
