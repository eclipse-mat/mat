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

public class DumpAgent {
	public static void agentmain(String arg)
	{
		String args[] = arg.split(","); //$NON-NLS-1$
		for (String a: args)
		{
			if ("java".equals(a)) { //$NON-NLS-1$
				com.ibm.jvm.Dump.JavaDump();
			} else if ("heap".equals(a)) { //$NON-NLS-1$
				com.ibm.jvm.Dump.HeapDump();
			} else if ("system".equals(a)) { //$NON-NLS-1$
				com.ibm.jvm.Dump.SystemDump();
			}
		}
	}
	public static void main(String args[]) {
		System.out.println("Test generating dumps"); //$NON-NLS-1$
		agentmain(args.length > 0 ? args[0] : ""); //$NON-NLS-1$
	}
}
