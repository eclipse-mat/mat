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

import org.eclipse.mat.snapshot.acquire.VmInfo;

public class IBMVmInfo extends VmInfo
{
    private String pid;
    
    public IBMVmInfo()
    {
    }
    
    public void setPid(String s)
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
    
    public String getPidName()
    {
        return pid;
    }
}
