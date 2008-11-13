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

import java.text.MessageFormat;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.ui.PlatformUI;


public class ErrorHelper
{
    public static void logThrowable(Throwable throwable)
    {
        IStatus status = new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, 1, "Internal Error", throwable);
        MemoryAnalyserPlugin.getDefault().getLog().log(status);
    }

    public static void logThrowableAndShowMessage(Throwable throwable, final String message)
    {
        final IStatus status = createErrorStatus(null, throwable);
        MemoryAnalyserPlugin.getDefault().getLog().log(status);

        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                "Error", message, status);
            }
        });

    }

    public static void logThrowableAndShowMessage(Throwable throwable)
    {
        logThrowableAndShowMessage(throwable, throwable.getMessage());
    }

    public static void showErrorMessage(Throwable throwable)
    {
        showErrorMessage(createErrorStatus(throwable));
    }

    public static void showErrorMessage(String message)
    {
        showErrorMessage(createErrorStatus(message));
    }

    private static void showErrorMessage(final IStatus status)
    {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", null,
                                status);
            }
        });
    }

    public static void showInfoMessage(final String message)
    {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                "Information", message);
            }
        });
    }

    public static IStatus createErrorStatus(String message)
    {
        return new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, message, null);
    }

    public static IStatus createErrorStatus(Throwable throwable)
    {
        return createErrorStatus(null, throwable);
    }

    public static IStatus createErrorStatus(String message, Throwable throwable)
    {
        if (message == null)
            message = enrichErrorMessage(throwable.getMessage(), throwable.getClass().getName());

        if (throwable == null)
            return createErrorStatus(message);

        if (throwable.getCause() == null)
        {
            return new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, message, throwable);
        }
        else
        {
            MultiStatus result = new MultiStatus(MemoryAnalyserPlugin.PLUGIN_ID, 0, message, null);

            while (throwable != null)
            {
                message = enrichErrorMessage(throwable.getMessage(), throwable.getClass().getName());
                result.add(new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, 0, message, throwable));

                if (throwable instanceof CoreException)
                    throwable = ((CoreException) throwable).getStatus().getException();
                else
                    throwable = throwable.getCause();
            }

            return result;
        }
    }

    /** exceptionType is String because of FailureObject */
    public static String enrichErrorMessage(String message, String exceptionType)
    {

        if ("java.lang.RuntimeException".equals(exceptionType))
            return MessageFormat.format("Internal Runtime Error ({0})", new Object[] { message });
        else if ("java.lang.ClassNotFoundException".equals(exceptionType))
            return MessageFormat.format("Class {0} not found", new Object[] { message });
        else if ("java.lang.NoClassDefFoundError".equals(exceptionType))
            return MessageFormat.format("Definition of class {0} not found (NoClassDefFoundError)",
                            new Object[] { message });
        else if ("java.lang.NoSuchMethodError".equals(exceptionType))
            return MessageFormat.format("No such method: {0}", new Object[] { message });
        else if (message == null)
            return MessageFormat.format("Exception of type {0}", new Object[] { exceptionType });
        else
            return message;

    }
}
