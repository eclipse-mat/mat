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
package org.eclipse.mat.ui.internal.query.arguments;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.internal.query.browser.QueryContextHelp;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;


public class ArgumentsWizard extends Wizard
{
    @Override
    public IDialogSettings getDialogSettings()
    {
        IDialogSettings workbenchDialogSettings = MemoryAnalyserPlugin.getDefault().getDialogSettings();
        IDialogSettings result = workbenchDialogSettings.getSection(ArgumentsWizard.class.getName());
        if (result == null)
        {
            result = workbenchDialogSettings.addNewSection(ArgumentsWizard.class.getName());
        }       
        return result;
    }

    ArgumentSet argumentSet;

    public ArgumentsWizard(ArgumentSet argumentSet)
    {
        this.argumentSet = argumentSet;

        setWindowTitle(argumentSet.getQueryDescriptor().getName());
        setForcePreviousAndNextButtons(false);
        setDefaultPageImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.ARGUMENTS_WIZARD));          
   }

    @Override
    public void addPages()
    {
        addPage(new ArgumentsWizardPage(argumentSet));

         if (argumentSet.getQueryDescriptor().getHelp() != null)
        {
            getShell().getDisplay().timerExec(100, new Runnable()
            {
                public void run()
                {
                    if (getShell() != null && !getShell().isDisposed())
                    {
                        Rectangle myBounds = getShell().getBounds();
                        Rectangle helpBounds = new Rectangle(myBounds.x, myBounds.y + myBounds.height, myBounds.width,
                                        SWT.DEFAULT);
                        new QueryContextHelp(getShell(), ArgumentsWizard.this.argumentSet.getQueryDescriptor(),
                                        helpBounds).open();
                    }
                }
            });
        }
    }

    @Override
    public boolean performFinish()
    {
        return true;
    }
    
   
}
