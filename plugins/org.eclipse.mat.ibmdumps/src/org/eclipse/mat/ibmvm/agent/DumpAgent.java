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
 *******************************************************************************/
package org.eclipse.mat.ibmvm.agent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Helper class which is loaded into the target VM to create a dump.
 * This class requires an IBM VM to compile.
 * A precompiled version of this class exists in the classes folder.
 * @author ajohnson
 *
 */
public class DumpAgent {
    public static final String SEPARATOR = "+"; //$NON-NLS-1$
    public static final String SYSTEM = "system"; //$NON-NLS-1$
    public static final String HEAP = "heap"; //$NON-NLS-1$
    public static final String JAVA = "java"; //$NON-NLS-1$

    /**
     * Generate a dump on this machine
     * 
     * @param arg
     *            E.g. "system", "heap+java"
     */
    public static void agentmain(String arg) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        String args[] = arg.split(Pattern.quote(SEPARATOR));
        Class<? extends Object>dumpcls = Class.forName("com.ibm.jvm.Dump");
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
        }
    }

    public static void main(String args[]) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        System.out.println("Test generating dumps"); //$NON-NLS-1$
        agentmain(args.length > 0 ? args[0] : ""); //$NON-NLS-1$
    }
}
