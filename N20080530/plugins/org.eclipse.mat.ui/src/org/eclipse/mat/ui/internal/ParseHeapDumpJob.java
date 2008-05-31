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
package org.eclipse.mat.ui.internal;

import java.text.MessageFormat;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;


public abstract class ParseHeapDumpJob extends Job
{
    private final IPath path;

    public ParseHeapDumpJob(IPath path)
    {
        super(MessageFormat.format("Parsing heap dump from ''{0}''", path.toOSString()));
        this.path = path;
        this.setUser(true);

        this.setRule(new ParseRule(path));
    }

    protected IStatus run(IProgressMonitor monitor)
    {
        this.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.TRUE);

        try
        {
            SnapshotHistoryService.getInstance().addVisitedPath(path);

            final ISnapshot snapshot = SnapshotFactory.openSnapshot(path.toFile(), new ProgressMonitorWrapper(monitor));

            if (snapshot == null)
            {
                return Status.CANCEL_STATUS;
            }
            else
            {
                SnapshotHistoryService.getInstance().addVisitedPath(path, snapshot);

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        ParseHeapDumpJob.this.finished(snapshot);
                    }
                });

                return Status.OK_STATUS;
            }
        }
        catch (SnapshotException e)
        {
            return ErrorHelper.createErrorStatus(e);
        }
    }

    public boolean isModal()
    {
        Boolean isModal = (Boolean) this.getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
        if (isModal == null)
            return false;
        return isModal.booleanValue();
    }

    protected abstract void finished(ISnapshot snapshot);

    // //////////////////////////////////////////////////////////////
    // internal classes
    // //////////////////////////////////////////////////////////////

    class ParseRule implements ISchedulingRule
    {
        IPath path;

        public ParseRule(IPath filename)
        {
            this.path = filename;
        }

        public boolean contains(ISchedulingRule rule)
        {
            return rule instanceof ParseRule && ((ParseRule) rule).path.equals(path);
        }

        public boolean isConflicting(ISchedulingRule rule)
        {
            return contains(rule);
        }

    }

}
