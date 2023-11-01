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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import org.eclipse.mat.report.internal.Messages;

/**
 * Class used as progress listener for the console. You can obtain one instance
 * via the {@link org.eclipse.mat.query.IQuery#execute} method if the query is run from
 * Memory Analyzer run in batch mode.
 */
public class ConsoleProgressListener implements IProgressListener
{
    private PrintWriter out;
    private boolean isDone = false;
    private int workPerDot;
    private int workAccumulated;
    private int dotsPrinted;

    public ConsoleProgressListener(OutputStream out)
    {
        this(new PrintWriter(new OutputStreamWriter(out, Charset.defaultCharset())));
    }

    public ConsoleProgressListener(PrintWriter out)
    {
        this.out = out;
    }

    public void beginTask(String name, int totalWork)
    {
        out.write(Messages.ConsoleProgressListener_Label_Task + " " + name + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.write("["); //$NON-NLS-1$
        workPerDot = totalWork > 80 ? (totalWork / 80) : 1;
        workAccumulated = 0;
        dotsPrinted = 0;
        out.flush();
    }

    public void done()
    {
        if (!isDone)
        {
            out.write("]\n"); //$NON-NLS-1$
            out.flush();
            isDone = true;
        }

    }

    public boolean isCanceled()
    {
        return false;
    }

    public void setCanceled(boolean value)
    {
        throw new UnsupportedOperationException();
    }

    public void subTask(String name)
    {
        out.write("\n" + Messages.ConsoleProgressListener_Label_Subtask + " " + name + "\n["); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int ii = 0; ii < dotsPrinted; ii++)
            out.write("."); //$NON-NLS-1$
        out.flush();
    }

    public void worked(int work)
    {
        workAccumulated += work;

        int dotsToPrint = workAccumulated / workPerDot;
        if (dotsToPrint > 0)
        {
            dotsPrinted += dotsToPrint;
            for (int ii = 0; ii < dotsToPrint; ii++)
                out.write("."); //$NON-NLS-1$
            workAccumulated -= (dotsToPrint * workPerDot);
            out.flush();
        }
    }

    public void sendUserMessage(Severity severity, String message, Throwable exception)
    {
        out.write("\n"); //$NON-NLS-1$

        switch (severity)
        {
            case INFO:
                out.write("[INFO] "); //$NON-NLS-1$
                break;
            case WARNING:
                out.write("[WARNING] "); //$NON-NLS-1$
                break;
            case ERROR:
                out.write("[ERROR] "); //$NON-NLS-1$
                break;
            default:
                out.write("[UNKNOWN] "); //$NON-NLS-1$
        }

        out.write(message);

        if (exception != null)
        {
            out.write("\n"); //$NON-NLS-1$
            exception.printStackTrace(out);
        }

        out.write("\n["); //$NON-NLS-1$
        for (int ii = 0; ii < dotsPrinted; ii++)
            out.write("."); //$NON-NLS-1$
    }

}
