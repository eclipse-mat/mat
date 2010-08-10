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

import org.eclipse.mat.ibmvm.agent.DumpAgent;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;

/**
 * Stores information about the target VM.
 * @author ajohnson
 *
 */
public class IBMVmInfo extends VmInfo
{
    @Argument
    public DumpType type = DumpType.SYSTEM;

    @Argument
    public boolean compress = false;

    private String pid;
    
    IBMVmInfo(String pid, String description, boolean heapDumpEnabled, String proposedFileName, IHeapDumpProvider heapDumpProvider)
    {
        super(0, description, heapDumpEnabled, proposedFileName, heapDumpProvider);
        setPid(pid);
    }
    
    void setPid(String s)
    {
        pid = s;
        try
        {
            int i = Integer.parseInt(s.split("\\.")[0]); //$NON-NLS-1$
            setPid(i);
        }
        catch (NumberFormatException e)
        {
            setPid(-1);
        }
    }
    
    String getPidName()
    {
        return pid;
    }
    
    /**
     * Command to pass to the agent to generate dumps of this type
     * @return
     */
    String agentCommand()
    {
        if (type == DumpType.SYSTEM)
            return DumpAgent.SYSTEM;
        else if (type == DumpType.HEAP)
            return DumpAgent.HEAP+DumpAgent.SEPARATOR+DumpAgent.JAVA;
        else if (type == DumpType.JAVA)
            return DumpAgent.JAVA;
        return null;
    }
    
    @Override
    public String getProposedFileName()
    {
        String ret = super.getProposedFileName();
        if (ret == null)
        {
            BaseProvider provider = (BaseProvider)getHeapDumpProvider();
            if (type == DumpType.SYSTEM)
                if (compress)
                    ret = provider.systemDumpZipTemplate;
                else
                    ret = provider.systemDumpTemplate;
            else if (type == DumpType.HEAP)
                if (compress)
                    ret = provider.heapDumpZipTemplate;
                else
                    ret = provider.heapDumpTemplate;
            else if (type == DumpType.JAVA)
                ret = provider.javaDumpTemplate;
        }
        return ret;
    }
}
