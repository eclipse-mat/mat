/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;

public class ErrorLogHandler extends Handler
{

    @Override
    public void close() throws SecurityException
    {}

    @Override
    public void flush()
    {}

    @Override
    public void publish(LogRecord record)
    {
        int severity = IStatus.OK;

        if (record.getLevel().intValue() >= Level.SEVERE.intValue())
            severity = IStatus.ERROR;
        else if (record.getLevel().intValue() >= Level.WARNING.intValue())
            severity = IStatus.WARNING;
        else if (record.getLevel().intValue() >= Level.INFO.intValue())
            severity = IStatus.INFO;

        Status status = new Status(severity, MemoryAnalyserPlugin.PLUGIN_ID, record.getMessage(), record.getThrown());
        MemoryAnalyserPlugin.log(status);
    }
}
