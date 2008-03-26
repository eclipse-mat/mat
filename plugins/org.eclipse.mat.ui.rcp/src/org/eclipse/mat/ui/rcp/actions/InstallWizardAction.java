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
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

public class InstallWizardAction extends Action implements IWorkbenchWindowActionDelegate
{

    private IWorkbenchWindow window;

    public InstallWizardAction()
    {
    // do nothing
    }

    public void run()
    {
        openInstaller(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
    }

    public void run(IAction action)
    {
        openInstaller(window);
    }

    private void openInstaller(final IWorkbenchWindow window)
    {
        /*
        BusyIndicator.showWhile(window.getShell().getDisplay(), new Runnable()
        {
            public void run()
            {
                UpdateManagerUI.openInstaller(window.getShell());
            }
        });
        */
    }

    public void selectionChanged(IAction action, ISelection selection)
    {
    // do nothing
    }

    public void dispose()
    {
    // do nothing
    }

    public void init(IWorkbenchWindow window)
    {
        this.window = window;
    }
}
