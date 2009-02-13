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
package org.eclipse.mat.ui.editor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mat.util.IProgressListener;

public abstract class AbstractPaneJob extends Job
{
    private AbstractEditorPane pane;

    public AbstractPaneJob(String name, AbstractEditorPane pane)
    {
        super(name);
        this.pane = pane;
    }

    @Override
    protected final IStatus run(IProgressMonitor monitor)
    {
        try
        {
            return doRun(monitor);
        }
        catch (IProgressListener.OperationCanceledException e)
        {
            // $JL-EXC$
            onCancel();
            return Status.CANCEL_STATUS;
        }
    }

    protected void onCancel()
    {}

    protected abstract IStatus doRun(IProgressMonitor monitor);

    @Override
    public boolean belongsTo(Object family)
    {
        return this.pane == family;
    }

    protected AbstractEditorPane getPane()
    {
        return this.pane;
    }
}
