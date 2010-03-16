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

import org.eclipse.mat.query.annotations.Name;

@Name("IBM System Dump (using helper VM)")
public class IBMExecSystemDumpProvider extends IBMExecDumpProvider
{
    @Override
    protected String agentCommand()
    {
        return "system"; //$NON-NLS-1$
    }
}
