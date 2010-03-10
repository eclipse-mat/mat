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

import java.io.File;

import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.IProgressListener;

public class VMListDumpProvider extends IBMDumpProvider {
    public VMListDumpProvider() {
    }
    public File acquireDump(VmInfo info, File preferredLocation, IProgressListener listener)
    {
        throw new IllegalStateException("Unsuitable"); //$NON-NLS-1$
    }

    String agentCommand() {
        return ""; //$NON-NLS-1$
    }
    int files() {
		return 0;
	}
    
    String dumpName()
    {
        return null;
    }
    
}