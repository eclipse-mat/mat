/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import org.eclipse.mat.snapshot.model.IStackFrame2;

/**
 * 
 * @noextend This class is not intended to be subclassed by clients. May still
 *           be subject to change
 * 
 */
class StackFrameImpl implements IStackFrame2
{
	private String text;

	private int[] localObjectIds;
	
	private int[] blockedOnIds;

	public StackFrameImpl(String text, int[] localObjectIds, int[] blockedOnIds)
	{
		this.text = text;
		this.localObjectIds = localObjectIds;
		this.blockedOnIds = blockedOnIds;
	}

	public int[] getLocalObjectsIds()
	{
		return localObjectIds == null ? new int[0] : localObjectIds;
	}

	public String getText()
	{
		return text;
	}

    public int[] getBlockedOnIds()
    {
        return blockedOnIds == null ? new int[0] : blockedOnIds;
    }

}
