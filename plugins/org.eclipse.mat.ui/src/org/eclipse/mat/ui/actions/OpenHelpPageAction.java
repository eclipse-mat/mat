/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.ui.Messages;
import org.eclipse.ui.PlatformUI;

public class OpenHelpPageAction extends Action
{
    private String helpUrl;

    public OpenHelpPageAction(String helpUrl)
    {
        super(Messages.OpenHelpPageAction_Help, JFaceResources.getImageRegistry().getDescriptor(Dialog.DLG_IMG_HELP));
        this.helpUrl = helpUrl;
    }

    @Override
    public void run()
    {
        PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(helpUrl);
    }
}
