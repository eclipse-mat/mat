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
package org.eclipse.mat.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class FileUtils
{
    private static DirDeleter deleterThread;

    static
    {
        deleterThread = new DirDeleter();
        Runtime.getRuntime().addShutdownHook(deleterThread);
    }

    private FileUtils()
    {}

    public final static void copy(InputStream in, OutputStream out) throws IOException
    {
        byte[] b = new byte[256];
        int i = 0;

        while (true)
        {
            i = in.read(b);
            if (i == -1)
                break;
            out.write(b, 0, i);
        }

    }
    
    public static File createTempDirectory(String prefix, File parent) throws IOException
    {
        File tempFile = File.createTempFile(prefix, "", parent);
        if (!tempFile.delete())
            throw new IOException();
        if (!tempFile.mkdir())
            throw new IOException();
        deleterThread.add(tempFile);
        return tempFile;
    }

    public static String toFilename(String name, String extension)
    {
        StringBuilder buf = new StringBuilder(name.length() + extension.length() + 1);
        
        for (int ii = 0; ii < name.length(); ii++)
        {
            char c = name.charAt(ii);
            if (Character.isLetterOrDigit(c))
                buf.append(c);
            else
                buf.append("_");
        }
        
        buf.append(".").append(extension);
        
        return buf.toString();
    }
    
    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private static class DirDeleter extends Thread
    {
        private List<File> dirList = new ArrayList<File>();

        public synchronized void add(File dir)
        {
            dirList.add(dir);
        }

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
            if (!dir.exists())
                return;

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
