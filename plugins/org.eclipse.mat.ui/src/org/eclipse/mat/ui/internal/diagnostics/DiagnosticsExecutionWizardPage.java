/*******************************************************************************
 * Copyright (c) 2022, 2022 IBM Corporation.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * Handles executing the diagnostics on MAT
 */
public class DiagnosticsExecutionWizardPage extends WizardPage implements DiagnosticsProgress
{
    private boolean isExecuting;
    private Label introLabel;
    private Text status;

    public DiagnosticsExecutionWizardPage()
    {
        super("diagnostics_execution");
    }

    public void createControl(Composite parent)
    {
        setTitle(Messages.DiagnosticsExecutionWizardPage_DialogName);
        setDescription(Messages.DiagnosticsExecutionWizardPage_DialogDescription);

        final Composite top = new Composite(parent, SWT.NONE);
        top.setLayout(new GridLayout(1, false));

        introLabel = new Label(top, SWT.NONE);
        introLabel.setText(Messages.DiagnosticsExecutionWizardPage_IntroIdle);
        introLabel.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

        status = new Text(top, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        status.setLayoutData(new GridData(GridData.FILL_BOTH));

        setControl(top);
    }

    public void reset()
    {
        introLabel.setText(Messages.DiagnosticsExecutionWizardPage_IntroIdle);
        clearText();
    }

    public boolean performFinish(DiagnosticsAction action)
    {
        isExecuting = true;
        try
        {
            introLabel.setText(Messages.DiagnosticsExecutionWizardPage_IntroExecuting);
            clearText();

            DiagnosticExecutionRunnable operation = new DiagnosticExecutionRunnable(action, this, getContainer());
            if (operation.run().isOK())
            {
                introLabel.setText(Messages.DiagnosticsExecutionWizardPage_SucceededExecuting);
                setTextboxColor();
                return true;
            }
            else
            {
                introLabel.setText(Messages.DiagnosticsExecutionWizardPage_IntroIdle);
                setTextboxColor();
            }
        }
        finally
        {
            isExecuting = false;
        }
        return false;
    }

    private void setTextboxColor()
    {
        // Default works better with dark themes
        //status.setForeground(getControl().getDisplay().getSystemColor(SWT.COLOR_BLACK));
    }

    @Override
    public boolean isPageComplete()
    {
        return !isExecuting;
    }

    @Override
    public void appendText(final String text)
    {
        getControl().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                if (status.isDisposed())
                    return;
                String newText = status.getText();
                if (newText == null || newText.length() == 0)
                {
                    newText = text;
                }
                else
                {
                    newText += Text.DELIMITER + text;
                }
                status.setText(newText);
            }
        });
    }

    @Override
    public void handleException(Throwable t)
    {
        if (t != null)
        {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            appendText(sw.toString().replaceAll("\n", Text.DELIMITER)); //$NON-NLS-1$
        }
    }

    @Override
    public void clearText()
    {
        getControl().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                if (status.isDisposed())
                    return;
                status.setText(""); //$NON-NLS-1$
            }
        });
    }

    private static class DiagnosticExecutionRunnable implements IRunnableWithProgress
    {
        private IStatus status;
        private IRunnableContext context;
        private DiagnosticsAction action;
        private DiagnosticsProgress progress;

        public DiagnosticExecutionRunnable(DiagnosticsAction action, DiagnosticsProgress progress,
                        IRunnableContext context)
        {
            this.action = action;
            this.context = context;
            this.progress = progress;
        }

        public final IStatus run()
        {
            try
            {
                context.run(true, true, this);
            }
            catch (Exception e)
            {
                status = ErrorHelper.createErrorStatus(Messages.AcquireSnapshotAction_UnexpectedException, e);
            }

            // report error if any occurred
            if (!status.isOK() && status != Status.CANCEL_STATUS)
            {
                String error = MessageUtil.format(Messages.DiagnosticsExecutionWizardPage_ErrorExecuting,
                                status.getException().getLocalizedMessage());
                Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.SEVERE, error, status.getException());
                progress.appendText(error);
                progress.handleException(status.getException());
            }

            return status;
        }

        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
        {
            status = doOperation(monitor);
        }

        private IStatus doOperation(IProgressMonitor monitor)
        {
            IProgressListener listener = new ProgressMonitorWrapper(monitor);
            try
            {
                action.run(progress);

                if (listener.isCanceled())
                    return Status.CANCEL_STATUS;
            }
            catch (Exception e)
            {
                return ErrorHelper.createErrorStatus(e);
            }

            return Status.OK_STATUS;
        }
    }
}
