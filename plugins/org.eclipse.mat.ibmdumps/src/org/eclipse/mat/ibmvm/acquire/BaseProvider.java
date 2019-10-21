/*******************************************************************************
 * Copyright (c) 2010, 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *    IBM Corporation/Andrew Johnson - hprof
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

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;

/**
 * The base dump provider class.
 * Providers helper method to copy class files and create a jar to run with another VM.
 * @author ajohnson
 *
 */
public abstract class BaseProvider implements IHeapDumpProvider
{
    @Argument
    public DumpType defaultType = DumpType.SYSTEM;

    @Argument
    public boolean defaultLive = false;

    @Argument
    public boolean defaultCompress = false;

    @Argument
    public boolean listAttach = true;
    
    @Argument
    public String systemDumpTemplate = "core.{0,date,yyyyMMdd.HHmmss}.{1,number,0}.{2,number,0000}.dmp"; //$NON-NLS-1$;

    @Argument
    public String systemDumpZipTemplate = "core.{0,date,yyyyMMdd.HHmmss}.{1,number,0}.{2,number,0000}.dmp.zip"; //$NON-NLS-1$;

    @Argument
    public String heapDumpTemplate = "heapdump.{0,date,yyyyMMdd.HHmmss}.{1,number,0}.{2,number,0000}.phd";//$NON-NLS-1$;

    @Argument
    public String heapDumpZipTemplate = "heapdump.{0,date,yyyyMMdd.HHmmss}.{1,number,0}.{2,number,0000}.phd.gz"; //$NON-NLS-1$;

    @Argument
    public String javaDumpTemplate = "javacore.{0,date,yyyyMMdd.HHmmss}.{1,number,0}.{2,number,0000}.txt"; //$NON-NLS-1$;

    @Argument
    public String hprofDumpTemplate = "java_pid{1,number,0000}.{2,number,0000}.hprof"; //$NON-NLS-1$;

    @Argument
    public String hprofDumpZipTemplate = "java_pid{1,number,0000}.{2,number,0000}.hprof.gz"; //$NON-NLS-1$;

    static final int SLEEP_TIMEOUT = 500; // milliseconds
    static final int GROW_COUNT = 5 * 60 * 1000 / SLEEP_TIMEOUT;
    static final int FINISHED_COUNT = 5 * 1000 / SLEEP_TIMEOUT;
    static final int CREATE_COUNT = 30 * 1000 / SLEEP_TIMEOUT;
    static final int GROWING_COUNT = (CREATE_COUNT + GROW_COUNT) * 2; // progress = 67% file length, 33% waiting time
    static final int TOTAL_WORK = CREATE_COUNT + GROWING_COUNT + GROW_COUNT;
    static final String INFO_SEPARATOR = File.pathSeparator; //$NON-NLS-1$

    /**
     * Make a temporary jar containing the requested files
     * @param jarname the output jar name
     * @param metaEntry the meta file tag to prepend to the name of the first class file
     * @param classesNames names of classes/files
     * @param classes list of classes
     * @return the jar file
     * @throws IOException
     * @throws FileNotFoundException
     */
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

    /**
     * Add a single class to a jar
     * @param zo where to write the class
     * @param cls example class in the same package
     * @param agent name of the class (without .class)
     * @throws IOException
     */
    private static void addClassToJar(ZipOutputStream zo, Class<?> cls, String agent) throws IOException
    {
        ZipEntry ze;
        String agentFile = agent.replace('.', '/') + ".class"; //$NON-NLS-1$
        ze = new ZipEntry(agentFile);
        zo.putNextEntry(ze);
        InputStream is = cls.getResourceAsStream("/" + agentFile); //$NON-NLS-1$
        if (is == null)
            throw new FileNotFoundException(agentFile);
        BufferedInputStream s = new BufferedInputStream(is);
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

    /**
     * Add the messages to the jar
     * @param zo where to write the message file
     * @param rb where to retrieve the message file
     * @param agent the name of the message file
     * @throws IOException
     */
    private static void addMessagesToJar(ZipOutputStream zo, ResourceBundle rb, String agent) throws IOException
    {
        ZipEntry ze;
        String agentFile = agent.replace('.', '/') + ".properties"; //$NON-NLS-1$
        ze = new ZipEntry(agentFile);
        zo.putNextEntry(ze);
        Properties p = new Properties();
        for (Enumeration<String> e = rb.getKeys(); e.hasMoreElements();)
        {
            String k = e.nextElement();
            p.put(k, rb.getString(k));
        }
        p.store(zo, agent);
        zo.closeEntry();
    }

}
