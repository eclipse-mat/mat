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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;

public abstract class BaseProvider implements IHeapDumpProvider
{

    /**
     * Some PIDs from the IBM VMs come as 4321.1 e.g. if one PID is left over
     * from a previous boot
     * 
     * @param id
     * @return
     */
    static int getPid(String id)
    {
        int p = 0;
        while (p < id.length() && id.charAt(p) >= '0' && id.charAt(p) <= '9')
            ++p;
        if (p == 0)
            return -1;
        try
        {
            return Integer.parseInt(id.substring(0, p));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    static File makeJar(String jarname, String metaEntry, String classesNames[], Class<?>[] classes) throws IOException,
                    FileNotFoundException
    {
        File jarfile = File.createTempFile(jarname, ".jar"); //$NON-NLS-1$
        jarfile.deleteOnExit();
        FileOutputStream fo = new FileOutputStream(jarfile);
        try
        {
            ZipOutputStream zo = new ZipOutputStream(fo);
            ZipEntry ze = new ZipEntry("META-INF/MANIFEST.MF"); //$NON-NLS-1$
            zo.putNextEntry(ze);
            zo.write((metaEntry + classesNames[0] + "\n").getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            zo.closeEntry();
            for (String agent : classesNames)
            {
                addClassToJar(zo, IBMDumpProvider.class, agent);
            }
            for (Class<?> cls : classes)
            {
                String agent = cls.getName();
                addClassToJar(zo, cls, agent);
                if (cls == Messages.class)
                {
                    addMessagesToJar(zo, Messages.RESOURCE_BUNDLE, Messages.class.getPackage().getName()+".messages");//$NON-NLS-1$
                }
            }
            zo.close();
        }
        finally
        {
            fo.close();
        }
        return jarfile;
    }

    private static void addClassToJar(ZipOutputStream zo, Class<?> cls, String agent) throws IOException
    {
        ZipEntry ze;
        String agentFile = agent.replace('.', '/') + ".class"; //$NON-NLS-1$
        ze = new ZipEntry(agentFile);
        zo.putNextEntry(ze);
        InputStream is = cls.getResourceAsStream("/" + agentFile); //$NON-NLS-1$
        BufferedInputStream s = new BufferedInputStream(is);
        if (is == null)
            throw new FileNotFoundException(agentFile);
        try
        {
            byte b[] = new byte[10000];
            for (int r; (r = s.read(b)) >= 0;)
            {
                zo.write(b, 0, r);
            }
        }
        finally
        {
            s.close();
        }
        zo.closeEntry();
    }

    private static void addMessagesToJar(ZipOutputStream zo, ResourceBundle rb, String agent) throws IOException
    {
        ZipEntry ze;
        String agentFile = agent.replace('.', '/') + ".properties"; //$NON-NLS-1$
        ze = new ZipEntry(agentFile);
        zo.putNextEntry(ze);
        Properties p = new Properties();
        for (Enumeration<String> e = rb.getKeys(); e.hasMoreElements(); ) {
            String k = e.nextElement();
            p.put(k, rb.getString(k));
        }
        p.store(zo, agent);
        zo.closeEntry();
    }
    
}
