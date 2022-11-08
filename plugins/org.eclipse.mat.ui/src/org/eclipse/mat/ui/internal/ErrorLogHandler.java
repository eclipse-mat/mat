/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.mat.util.MessageUtil;

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

        String message = record.getMessage();
        if (record.getParameters() != null)
        {
            // Substitute parameters if required
            message = MessageUtil.format(message, record.getParameters());
        }
        String name = record.getLoggerName();
        if (name != null)
        {
            // Guess the plugin
            if (name.startsWith("org.eclipse.mat.ui.")) //$NON-NLS-1$
                name = "org.eclipse.mat.ui"; //$NON-NLS-1$
            else if (name.startsWith("org.eclipse.mat.parser.")) //$NON-NLS-1$
                name = "org.eclipse.mat.parser"; //$NON-NLS-1$
            else if (name.startsWith("org.eclipse.mat.report.") || //$NON-NLS-1$
                     name.startsWith("org.eclipse.mat.query.")) //$NON-NLS-1$
                name = "org.eclipse.mat.report"; //$NON-NLS-1$
            else if (name.startsWith("org.eclipse.mat.inspections.") || //$NON-NLS-1$
                     name.startsWith("org.eclipse.mat.internal.") || //$NON-NLS-1$
                     name.startsWith("org.eclipse.mat.snapshot.")) //$NON-NLS-1$
                name = "org.eclipse.mat.api"; //$NON-NLS-1$
            else
                name = MemoryAnalyserPlugin.PLUGIN_ID;
        }
        else
        {
            name = MemoryAnalyserPlugin.PLUGIN_ID;
        }
        Status status = new Status(severity, name, message, record.getThrown());
        MemoryAnalyserPlugin.log(status);
    }
}
