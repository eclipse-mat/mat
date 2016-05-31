/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.mat.util.VoidProgressListener;

/**
 * Create a dump provider for IBM VMs
 * Depending on the type of the current VM, either uses an Attach API method
 * or will use a helper VM.  
 * @author ajohnson
 *
 */
public class DumpFactory implements IExecutableExtensionFactory
{

    /**
     * Actually create the appropriate dump provider.
     * @return the new dump provider
     */
    public Object create() throws CoreException
    {
        IBMDumpProvider ret = new IBMDumpProvider();
        // Faster for the initial test
        ret.listAttach = false;
        if (ret.getAvailableVMs(new VoidProgressListener()) != null)
        {
            // Allow default listAttach
            return new IBMDumpProvider();
        }
        return new IBMExecDumpProvider();
    }

}
