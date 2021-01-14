/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;

/**
 * Information about an IBM VM, for use when using a helper VM to create a dump.
 * @author ajohnson
 *
 */
@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#task_acquiringheapdump__3")
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
