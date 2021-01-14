/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;

/**
 * 
 * @noextend This class is not intended to be subclassed by clients. May still
 *           be subject of change
 * 
 */
class ThreadStackImpl implements IThreadStack
{
	private int threadId;
	private IStackFrame[] stackFrames;

	public ThreadStackImpl(int threadId, StackFrameImpl[] stackFrames)
	{
		this.threadId = threadId;
		this.stackFrames = stackFrames;
	}

	public IStackFrame[] getStackFrames()
	{
		return stackFrames;
	}

	public int getThreadId()
	{
		return threadId;
	}

}
