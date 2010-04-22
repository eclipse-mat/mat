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
import org.eclipse.mat.snapshot.acquire.VmInfo;

/**
 * Stores information about the target VM.
 * @author ajohnson
 *
 */
public class IBMVmInfo extends VmInfo
{
    /**
     * Used for selecting the appropriate dump type
     * @author ajohnson
     *
     */
    public enum DumpType
    {
        HEAP("Heap"), //$NON-NLS-1$
        SYSTEM("System"); //$NON-NLS-1$
        String type;
        private DumpType(String s) {
            type = s;
        }
    }
    
    @Argument
    public DumpType type = DumpType.SYSTEM;
    
    private String pid;
    
    IBMVmInfo()
    {
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
        return null;
    }
}
