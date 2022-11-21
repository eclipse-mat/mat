/*******************************************************************************
 * Copyright (c) 2010, 2022 IBM Corporation
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
 *    IBM Corporation/Andrew Johnson - dumps for Oracle based VMs using HotSpot Beans
 *******************************************************************************/
package org.eclipse.mat.ibmvm.agent;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * Helper class which is loaded into the target VM to create a dump.
 * This class requires an IBM VM to run, but uses
 * reflection so that it can be compiled with any Java compiler.
 * @author Andrew Johnson
 *
 */
public class DumpAgent {
    public static final String SEPARATOR = "+"; //$NON-NLS-1$
    public static final String SYSTEM = "system"; //$NON-NLS-1$
    public static final String HEAP = "heap"; //$NON-NLS-1$
    public static final String JAVA = "java"; //$NON-NLS-1$
    public static final String HPROF = "hprof"; //$NON-NLS-1$

    public static final String INFO_SEPARATOR = File.pathSeparator;

    /**
     * Generate a dump on this machine
     *
     * @param arg
     *            E.g. "system", "heap+java", "java"
     *            true live objects only
     *            with a path separator separating possible file names
     *            
     * Throw a exception if there is a problem so that the other end receives an AgentInitializationException. 
     */
    public static void agentmain(String arg) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException,
        MalformedObjectNameException, InstanceNotFoundException, ReflectionException, MBeanException
    {
        String args0[] = arg.split(INFO_SEPARATOR, 3);
        String args[] = args0[0].split(Pattern.quote(SEPARATOR));
        boolean live = Boolean.parseBoolean(args0[1]);
        String filename = args0[2];
        Class<? extends Object>dumpcls;
        try
        {
            dumpcls = Class.forName("com.ibm.jvm.Dump"); //$NON-NLS-1$
        }
        catch (ClassNotFoundException e)
        {
            try
            {
                dumpcls = Class.forName("sun.management.HotSpotDiagnostic"); //$NON-NLS-1$
            }
            catch (ClassNotFoundException e2)
            {
                //throw e;
                // We don't need this any more for a HPROF dump
                dumpcls = null;
                args = new String[] {HPROF};
            }
        }
        for (String a : args)
        {
            if (JAVA.equals(a))
            {
                String javacorefn = filename;
                if (args.length > 1 && !javacorefn.endsWith(".txt")) //$NON-NLS-1$
                {
                    // Supplied file name will be used for another dump type,
                    // so generate a new one here.
                    javacorefn = javacorefn.replaceFirst("\\.[^.\\/" + File.pathSeparator + "]*$", "") + ".txt"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                }
                String req = "java:"; //$NON-NLS-1$
                req += live ? "request=exclusive+compact+prepwalk" : "request=exclusive+prepwalk"; //$NON-NLS-1$ //$NON-NLS-2$
                req += ",file=" + filename; //$NON-NLS-1$
                try
                {
                    // IBM Java 7.1 and later
                    // com.ibm.jvm.Dump.triggerDump(String request
                    Method m = dumpcls.getMethod("triggerDump", String.class); //$NON-NLS-1$
                    m.invoke(null, req);
                }
                catch (NoSuchMethodException e)
                {
                    live(live);
                    // com.ibm.jvm.Dump.JavaDump();
                    Method m = dumpcls.getMethod("JavaDump"); //$NON-NLS-1$
                    m.invoke(null);
                }
            }
            else if (HEAP.equals(a))
            {
                String req = "heap:"; //$NON-NLS-1$
                req += live ? "request=exclusive+compact+prepwalk" : "request=exclusive+prepwalk"; //$NON-NLS-1$ //$NON-NLS-2$
                req += ",file=" + filename; //$NON-NLS-1$
                try
                {
                    // IBM Java 7.1 and later
                    // com.ibm.jvm.Dump.triggerDump(String request
                    Method m = dumpcls.getMethod("triggerDump", String.class); //$NON-NLS-1$
                    m.invoke(null, req);
                }
                catch (NoSuchMethodException e)
                {
                    live(live);
                    //com.ibm.jvm.Dump.HeapDump();
                    Method m = dumpcls.getMethod("HeapDump"); //$NON-NLS-1$
                    m.invoke(null);
                }
            }
            else if (SYSTEM.equals(a))
            {
                String req = "system:"; //$NON-NLS-1$
                req += live ? "request=exclusive+compact+prepwalk" : "request=exclusive+prepwalk"; //$NON-NLS-1$ //$NON-NLS-2$
                req += ",file=" + filename; //$NON-NLS-1$
                try
                {
                    // IBM Java 7.1 and later
                    // com.ibm.jvm.Dump.triggerDump(String request
                    Method m = dumpcls.getMethod("triggerDump", String.class); //$NON-NLS-1$
                    m.invoke(null, req);
                }
                catch (NoSuchMethodException e)
                {
                    live(live);
                    //com.ibm.jvm.Dump.SystemDump();
                    Method m = dumpcls.getMethod("SystemDump"); //$NON-NLS-1$
                    m.invoke(null);
                }
            }
            else if (HPROF.equals(a))
            {
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic"; //$NON-NLS-1$
                ObjectName objn = new ObjectName(HOTSPOT_BEAN_NAME);
                
                /*
                 * Fix up filename as JVM won't dump if extension is not .hprof
                 */
                String hprofExt = ".hprof"; //$NON-NLS-1$
                File file = new File(filename);
                String name = file.getName();
                if (!name.endsWith(hprofExt))
                {
                    int i = name.lastIndexOf(hprofExt);
                    if (i >= 0)
                    {
                        name = name.substring(0, i + hprofExt.length());
                    }
                    else
                    {
                        name = name + hprofExt;
                    }
                    filename = (new File(file.getParentFile(), name)).getPath();
                }
                server.invoke(objn,  "dumpHeap",  new Object[] {filename,  Boolean.valueOf(live)},  //$NON-NLS-1$
                                new String[] {"java.lang.String", "boolean"}); //$NON-NLS-1$ //$NON-NLS-2$

            }
            else if ("hprof".equals(a)) //$NON-NLS-1$
            {
                Object hsd = dumpcls.getConstructor().newInstance();
                //sun.management.HotSpotDiagnostic.dumpHeap(String filename, boolean live);
                // fails with linkage error on dumpHeap0()
                Method m = dumpcls.getMethod("dumpHeap", String.class, Boolean.TYPE); //$NON-NLS-1$
                m.invoke(hsd, filename, live);
            }
        }
    }

    private static void live(boolean live)
    {
        if (live)
        {
            System.gc();
        }
    }

    public static void main(String args[]) throws ClassNotFoundException, NoSuchMethodException, 
        InvocationTargetException, IllegalAccessException, InstantiationException,
        MalformedObjectNameException, InstanceNotFoundException, ReflectionException, MBeanException
    {
        System.out.println("Test generating dumps"); //$NON-NLS-1$
        agentmain(args.length > 0 ? args[0] : ""); //$NON-NLS-1$
    }
}
