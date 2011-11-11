/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial implementation
 *******************************************************************************/

package org.eclipse.mat.ui.accessibility;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

public class AccessibleToolbarAdapter extends AccessibleAdapter
{

	private ToolBar toolBar; // ToolBar with which this adapter is associated.

	public AccessibleToolbarAdapter(ToolBar toolBar)
	{
		super();
		this.toolBar = toolBar; // Store ref to associated toolbar
	}

	@Override
	public void getName(AccessibleEvent e)
	{
		if (e.childID != ACC.CHILDID_SELF)
		{ // Not self - ie probably a child
			ToolItem item = toolBar.getItem(e.childID); // Try to get child item
			if (item != null)
			{ // Found it
				String toolTip = item.getToolTipText(); // Get tool tip if any
				if (toolTip != null)
				{ // Got one
					e.result = toolTip; // Return to caller in AccessibleEvent
				}
			}
		}
	} // getName()

} // AccessibleToolbarAdapter