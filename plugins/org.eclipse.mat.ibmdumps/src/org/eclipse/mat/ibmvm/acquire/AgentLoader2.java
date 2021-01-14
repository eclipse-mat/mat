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

import java.io.IOException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Helper interface for monitoring running of an agent.
 * Allows loading of the agents to be cancelled.
 * @author ajohnson
 *
 */
interface AgentLoader2 extends Runnable
{
    public abstract void start();
    
    public abstract void interrupt();
    
    public abstract void join() throws InterruptedException;

    public abstract void run();

    public abstract boolean failed();

    public abstract void throwFailed(IProgressListener listener) throws SnapshotException, IOException;

}
