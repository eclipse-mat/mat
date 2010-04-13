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
package org.eclipse.mat.ibmvm.agent;

import java.util.regex.Pattern;

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
    public static void agentmain(String arg)
    {
        String args[] = arg.split(Pattern.quote(SEPARATOR));
        for (String a : args)
        {
            if (JAVA.equals(a))
            {
                com.ibm.jvm.Dump.JavaDump();
            }
            else if (HEAP.equals(a))
            {
                com.ibm.jvm.Dump.HeapDump();
            }
            else if (SYSTEM.equals(a))
            {
                com.ibm.jvm.Dump.SystemDump();
            }
        }
    }

    public static void main(String args[])
    {
        System.out.println("Test generating dumps"); //$NON-NLS-1$
        agentmain(args.length > 0 ? args[0] : ""); //$NON-NLS-1$
    }
}
