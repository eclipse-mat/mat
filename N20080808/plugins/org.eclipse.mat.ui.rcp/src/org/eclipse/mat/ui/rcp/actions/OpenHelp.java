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
package org.eclipse.mat.ui.rcp.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class OpenHelp extends Action implements IWorkbenchAction
{   
    private static final String HELP = "icons/help.gif";

    public OpenHelp()
    {     
        setId(ActionFactory.HELP_CONTENTS.getId());
        setText("&Help Contents");
        setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.mat.ui.rcp", HELP));

    } 

    public void run()
    {
        PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(
        "/org.eclipse.mat.ui.help/tasks/basictutorial.html");
    }


    public void dispose()
    {}

}
