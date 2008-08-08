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
package org.eclipse.mat.ui.util;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.util.IProgressListener;


/**
 * Notes on tuning:
 * <p>
 * I tested the following alternatives:
 * <ul>
 * <li>a timer task checking every 2 seconds and setting isCanceled
 * <li>direct call to the delegate every time
 * <li>the query checking only every 1000 calls
 * <li>the job overwrites canceling() and sets isCanceled
 * <li>a check every 2 seconds based on currentTimeMillis()
 * </ul>
 * On cold caches no big differences, on warm caches the last alternative was
 * slightly faster.
 */
public class ProgressMonitorWrapper implements IProgressListener
{
    IProgressMonitor delegate;
    boolean isCancelled;
    long lastCheck;

    public ProgressMonitorWrapper(IProgressMonitor monitor)
    {
        this.delegate = monitor;
        this.isCancelled = false;
        this.lastCheck = System.currentTimeMillis();
    }

    public void beginTask(String name, int totalWork)
    {
        delegate.beginTask(name, totalWork);
    }

    public void done()
    {
        delegate.done();
    }

    public boolean isCanceled()
    {
        long now = System.currentTimeMillis();
        if (now > lastCheck)
        {
            lastCheck = now + 2000;
            return isCancelled = delegate.isCanceled();
        }
        else
        {
            return isCancelled;
        }
    }

    public void setCanceled(boolean value)
    {
        delegate.setCanceled(value);
    }

    public void subTask(String name)
    {
        delegate.subTask(name);
    }

    public void worked(int work)
    {
        delegate.worked(work);
    }

    public void sendUserMessage(Severity severity, String message, Throwable exception)
    {
        doSendUserMessage(severity, message, exception);
    }

    static void doSendUserMessage(Severity severity, String message, Throwable exception)
    {
        int eclipseSeverity = IStatus.OK;
        switch (severity)
        {
            case INFO:
                eclipseSeverity = IStatus.INFO;
                break;
            case WARNING:
                eclipseSeverity = IStatus.WARNING;
                break;
            case ERROR:
                eclipseSeverity = IStatus.ERROR;
                break;
        }
        MemoryAnalyserPlugin.log(new Status(eclipseSeverity, MemoryAnalyserPlugin.PLUGIN_ID, message, exception));
    }
}
