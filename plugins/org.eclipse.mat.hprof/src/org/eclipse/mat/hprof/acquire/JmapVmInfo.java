/*******************************************************************************
 * Copyright (c) 2009, 2019 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - GZIP compression
 *******************************************************************************/
package org.eclipse.mat.hprof.acquire;

import java.io.File;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;

@HelpUrl("/org.eclipse.mat.ui.help/tasks/acquiringheapdump.html#task_acquiringheapdump__1")
public class JmapVmInfo extends VmInfo
{
	@Argument(isMandatory = false, advice = Advice.DIRECTORY)
	public File jdkHome;

    @Argument
    public boolean compress;

	public JmapVmInfo(int pid, String description, boolean heapDumpEnabled, String proposedFileName, IHeapDumpProvider heapDumpProvider)
	{
		super(pid, description, heapDumpEnabled, proposedFileName, heapDumpProvider);
	}


    @Override
    public String getProposedFileName()
    {
        String ret = super.getProposedFileName();
        if (ret == null)
        {
            if (compress)
                ret = JMapHeapDumpProvider.FILE_GZ_PATTERN;
            else
                ret = JMapHeapDumpProvider.FILE_PATTERN;
        }
        return ret;
    }

    public void setProposedFileName(String proposedFileName)
    {
        super.setProposedFileName(proposedFileName);
    }
}
