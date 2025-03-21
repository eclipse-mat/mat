/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG.
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
package org.eclipse.mat.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Wraps another underlying IProgressListener and also writes listener entries
 * to a log file.
 * 
 * @since 1.17
 */
public class WrappedLoggingProgressListener implements IProgressListener
{
    protected IProgressListener wrappedListener;
    protected File file;
    protected StringBuilder buffer = new StringBuilder();
    private static boolean wroteIOError = false;

    public WrappedLoggingProgressListener(IProgressListener wrappedListener)
    {
        this.wrappedListener = wrappedListener;
    }

    public void beginTask(String name, int totalWork)
    {
        wrappedListener.beginTask(name, totalWork);
    }

    public void done()
    {
        wrappedListener.done();
    }

    public boolean isCanceled()
    {
        return wrappedListener.isCanceled();
    }

    public void setCanceled(boolean value)
    {
        wrappedListener.setCanceled(value);
    }

    public void subTask(String name)
    {
        wrappedListener.subTask(name);
    }

    public void worked(int work)
    {
        wrappedListener.worked(work);
    }

    public void setFile(File file)
    {
        this.file = file;
    }

    public void sendUserMessage(Severity severity, String message, Throwable exception)
    {
        wrappedListener.sendUserMessage(severity, message, exception);

        String finalMessage = "[" + DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now()) + "] "; //$NON-NLS-1$ //$NON-NLS-2$
        switch (severity)
        {
            case INFO:
                finalMessage += "[INFO] "; //$NON-NLS-1$
                break;
            case WARNING:
                finalMessage += "[WARNING] "; //$NON-NLS-1$
                break;
            case ERROR:
                finalMessage += "[ERROR] "; //$NON-NLS-1$
                break;
            default:
                finalMessage += "[UNKNOWN] "; //$NON-NLS-1$
        }
        
        finalMessage += message;
        
        if (exception != null) {
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));
            String exceptionDetails = sw.toString();
            finalMessage += System.lineSeparator() + exceptionDetails;
        }
        
        if (file == null)
        {
            buffer.append(finalMessage);
            buffer.append(System.lineSeparator());
        }
        else
        {
            try
            {
                if (buffer.length() > 0)
                {
                    FileUtils.writeToFile(file, buffer.toString(), true, false);
                    buffer.setLength(0);
                }
                FileUtils.writeToFile(file, finalMessage, true, true);
            }
            catch (IOException e)
            {
                // Could not write to the log file for some reason but
                // this isn't considered fatal. If this is the first time
                // then print it out.
                if (!wroteIOError)
                {
                    e.printStackTrace();
                    wroteIOError = true;
                }
            }
        }
    }
}
