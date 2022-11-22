/*******************************************************************************
 * Copyright (c) 2022, 2022 IBM Corporation and others.
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
package org.eclipse.mat.ui.internal.diagnostics;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PlatformUI;

/**
 * Creates the diagnostics wizard
 */
public class DiagnosticsWizardAction extends Action implements IWorkbenchWindowActionDelegate
{
    public void run()
    {
        final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        DiagnosticsWizard wizard = new DiagnosticsWizard();
        WizardDialog dialog = new WizardDialog(shell, wizard);
        dialog.addPageChangedListener(wizard);
        dialog.open();
    }

    public static class DiagnosticsWizard extends Wizard implements IWorkbenchWizard, IPageChangedListener
    {
        private DiagnosticsSelectionWizardPage selectionPage;
        private DiagnosticsExecutionWizardPage executionPage;
        private boolean executionSucceeded;

        public DiagnosticsWizard()
        {
            setWindowTitle(Messages.DiagnosticsWizard_DialogName);
            setNeedsProgressMonitor(true);
        }

        public void addPages()
        {
            selectionPage = new DiagnosticsSelectionWizardPage();
            executionPage = new DiagnosticsExecutionWizardPage();

            addPage(selectionPage);
            addPage(executionPage);
        }

        public boolean performFinish()
        {
            if (executionSucceeded)
            {
                // The execution succeeded and the user had the opportunity
                // to review the diagnostic output, so then if they click
                // Finish again, we can simply close the wizard
                return true;
            }

            // Make sure we're on the last page which shows execution results
            if (!getContainer().getCurrentPage().equals(executionPage))
            {
                getContainer().showPage(executionPage);
            }

            // Tell the execution page to start executing
            if (executionPage.performFinish(selectionPage.getSelectedAction()))
            {
                // If there's a success, the user will presumably
                // want to review the output on the execution page, but
                // we set a flag so that if they click Finish again,
                // we will close the wizard.
                executionSucceeded = true;
            }

            return false;
        }

        @Override
        public boolean canFinish()
        {
            return selectionPage.isPageComplete() && executionPage.isPageComplete();
        }

        @Override
        public IWizardPage getStartingPage()
        {
            return selectionPage;
        }

        public void init(IWorkbench workbench, IStructuredSelection selection)
        {}

        @Override
        public void pageChanged(PageChangedEvent event)
        {
            if (event.getSelectedPage().equals(selectionPage))
            {
                executionSucceeded = false;
                executionPage.clearText();
            }
            else if (event.getSelectedPage().equals(executionPage))
            {
                executionPage.reset();
            }
        }
    }

    public void dispose()
    {}

    public void init(IWorkbenchWindow window)
    {}

    public void run(IAction action)
    {
        run();
    }

    public void selectionChanged(IAction action, ISelection selection)
    {}

    public static class Handler extends AbstractHandler
    {

        public Handler()
        {}

        public Object execute(ExecutionEvent executionEvent)
        {
            new DiagnosticsWizardAction().run();
            return null;
        }
    }
}
