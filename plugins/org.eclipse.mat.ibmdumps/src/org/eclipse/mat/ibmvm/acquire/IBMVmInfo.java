/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *    IBM Corporation/Andrew Johnson - hprof
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;

import org.eclipse.mat.ibmvm.agent.DumpAgent;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
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

    @Argument(isMandatory = false)
    public boolean live = false;

    @Argument
    public boolean compress = false;

    @Argument(isMandatory = false, advice = Advice.DIRECTORY)
    public File dumpdir;

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
     * @return the command to be executed e.g. by {@link IBMDumpProvider.AgentLoader#IBMDumpProvider.AgentLoader()}
     *  dump-type live filename
     */
    String agentCommand(File f)
    {
        String fn;
        if (f == null)
        {
            fn = getProposedFileName();
        }
        else
        {
            fn = f.getAbsolutePath();
        }
        if (type == DumpType.SYSTEM)
            return DumpAgent.SYSTEM+DumpAgent.INFO_SEPARATOR+Boolean.toString(live)+DumpAgent.INFO_SEPARATOR+fn;
        else if (type == DumpType.HEAP)
            return DumpAgent.HEAP+DumpAgent.SEPARATOR+DumpAgent.JAVA+DumpAgent.INFO_SEPARATOR+Boolean.toString(live)+DumpAgent.INFO_SEPARATOR+fn;
        else if (type == DumpType.JAVA)
            return DumpAgent.JAVA+DumpAgent.INFO_SEPARATOR+Boolean.toString(live)+DumpAgent.INFO_SEPARATOR+fn;
        else if (type == DumpType.HPROF)
            return DumpAgent.HPROF+DumpAgent.INFO_SEPARATOR+Boolean.toString(live)+DumpAgent.INFO_SEPARATOR+fn;
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
            else if (type == DumpType.HPROF)
                ret = provider.hprofDumpTemplate;
        }
        return ret;
    }
}
