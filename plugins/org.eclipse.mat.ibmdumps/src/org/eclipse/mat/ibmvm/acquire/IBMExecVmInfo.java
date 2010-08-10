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

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;

/**
 * Information about an IBM VM, for use when using a helper VM to create a dump.
 * @author ajohnson
 *
 */
public class IBMExecVmInfo extends IBMVmInfo
{
    @Argument
    public File javaexecutable;

    @Argument(isMandatory = false)
    public String vmoptions[];

    IBMExecVmInfo(String pid, String description, boolean heapDumpEnabled, String proposedFileName, IHeapDumpProvider heapDumpProvider)
    {
        super(pid, description, heapDumpEnabled, proposedFileName, heapDumpProvider);
    }
}
