/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

    public static final String INFO_SEPARATOR = File.pathSeparator; //$NON-NLS-1$

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
            dumpcls = Class.forName("com.ibm.jvm.Dump");
            if (live)
            {
                System.gc();
            }
        }
        catch (ClassNotFoundException e)
        {
            try
            {
                dumpcls = Class.forName("sun.management.HotSpotDiagnostic");
            }
            catch (ClassNotFoundException e2)
            {
                throw e;
            }
        }
        for (String a : args)
        {
            if (JAVA.equals(a))
            {
                //com.ibm.jvm.Dump.JavaDump();
                Method m = dumpcls.getMethod("JavaDump");
                m.invoke(null);
            }
            else if (HEAP.equals(a))
            {
                //com.ibm.jvm.Dump.HeapDump();
                Method m = dumpcls.getMethod("HeapDump");
                m.invoke(null);
            }
            else if (SYSTEM.equals(a))
            {
                //com.ibm.jvm.Dump.SystemDump();
                Method m = dumpcls.getMethod("SystemDump");
                m.invoke(null);
            }
            else if (HPROF.equals(a))
            {
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
                ObjectName objn = new ObjectName(HOTSPOT_BEAN_NAME);
                server.invoke(objn,  "dumpHeap",  new Object[] {filename,  Boolean.valueOf(live)}, 
                                new String[] {"java.lang.String", "boolean"});

            }
            else if ("hprof".equals(a))
            {
                Object hsd = dumpcls.newInstance();
                //sun.management.HotSpotDiagnostic.dumpHeap(String filename, boolean live);
                // fails with linkage error on dumpHeap0()
                Method m = dumpcls.getMethod("dumpHeap", String.class, Boolean.TYPE);
                m.invoke(hsd, filename, live);
            }
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
