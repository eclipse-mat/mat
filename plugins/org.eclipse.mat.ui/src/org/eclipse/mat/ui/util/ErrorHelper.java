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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.ui.PlatformUI;

public class ErrorHelper
{
    public static void logThrowable(Throwable throwable)
    {
        IStatus status = new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, 1,
                        Messages.ErrorHelper_InternalError, throwable);
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
                                Messages.ErrorHelper_Error, message, status);
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
                ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                Messages.ErrorHelper_Error, null, status);
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
                                Messages.ErrorHelper_Information, message);
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

    /**
     * Create an Eclipse Status message with details about the exceptions
     * including text from all the exception causes.
     * This method is aware of Eclipse CoreExceptions which have child status
     * objects.
     * The details view of the ErrorDialog can then show text from all the
     * exception causes which wouldn't happen without this routine.
     * @param message
     * @param throwable
     * @return
     */
    public static IStatus createErrorStatus(String message, Throwable throwable)
    {
        if (message == null)
            message = enrichErrorMessage(throwable.getLocalizedMessage(), throwable);

        // Simple message?
        if (throwable == null)
            return createErrorStatus(message);

        if (throwable.getCause() == null)
        {
            // No causes of the exception
            if (throwable instanceof CoreException)
            {
                // But the exception already has a status
                CoreException ce = (CoreException)throwable;
                return createErrorStatus(ce.getStatus());
            }
            else
            {
                // Create a simple status message
                return new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, message, throwable);
            }
        }
        else
        {
            // We have a complex status message to build
            MultiStatus result = new MultiStatus(MemoryAnalyserPlugin.PLUGIN_ID, 0, message, throwable);
            // The root exception has already been mentioned in the multistatus, so only include
            // the exception causes
            return addExceptions(throwable.getCause(), result);
        }
    }

    private static IStatus addExceptions(Throwable throwable, MultiStatus result)
    {
        String message;
        while (throwable != null)
        {
            if (throwable instanceof CoreException)
            {
                // Add the status to the result
                CoreException ce = (CoreException)throwable;
                result.add(createErrorStatus(ce.getStatus()));
            }
            else
            {
                // Add this particular exception
                message = enrichErrorMessage(throwable.getMessage(), throwable);
                result.add(new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, 0, message, throwable));
            }
            throwable = throwable.getCause();
        }

        return result;
    }
    
    private static IStatus createErrorStatus(IStatus s)
    {
        Throwable t = s.getException();
        boolean detailT = t instanceof CoreException || t != null && t.getCause() != null;
        // No complex information to add, so just return the supplied status
        if (!detailT && !s.isMultiStatus()) return s;
        MultiStatus result = new MultiStatus(s.getPlugin(), s.getCode(), enrichErrorMessage(s.getMessage(), t), t);
        if (detailT)
            addExceptions(t.getCause(), result);
        for (IStatus sub : s.getChildren())
        {
            result.add(createErrorStatus(sub));
        }
        return result;
    }

    /** exceptionType is String because of FailureObject */
    public static String enrichErrorMessage(String message, String exceptionType)
    {

        if ("java.lang.RuntimeException".equals(exceptionType))//$NON-NLS-1$
            return MessageUtil.format(Messages.ErrorHelper_InternalRuntimeError, new Object[] { message });
        else if ("java.lang.ClassNotFoundException".equals(exceptionType))//$NON-NLS-1$
            return MessageUtil.format(Messages.ErrorHelper_ClassNotFound, new Object[] { message });
        else if ("java.lang.NoClassDefFoundError".equals(exceptionType))//$NON-NLS-1$
            return MessageUtil.format(Messages.ErrorHelper_DefinitionNotFound, new Object[] { message });
        else if ("java.lang.NoSuchMethodError".equals(exceptionType))//$NON-NLS-1$
            return MessageUtil.format(Messages.ErrorHelper_NoSuchMethod, new Object[] { message });
        else if (message == null)
            return MessageUtil.format(Messages.ErrorHelper_Exception, new Object[] { exceptionType });
        else
            return message;

    }

    private static String enrichErrorMessage(String message, Throwable exceptionType)
    {

        if (exceptionType != null && exceptionType.getClass() == java.lang.RuntimeException.class)
            return MessageUtil.format(Messages.ErrorHelper_InternalRuntimeError, new Object[] { message });
        else if (exceptionType instanceof java.lang.ClassNotFoundException)
            return MessageUtil.format(Messages.ErrorHelper_ClassNotFound, new Object[] { message });
        else if (exceptionType instanceof java.lang.NoClassDefFoundError)
            return MessageUtil.format(Messages.ErrorHelper_DefinitionNotFound, new Object[] { message });
        else if (exceptionType instanceof java.lang.NoSuchMethodError)//$NON-NLS-1$
            return MessageUtil.format(Messages.ErrorHelper_NoSuchMethod, new Object[] { message });
        else if (exceptionType instanceof SnapshotException)
            return message;
        else if (exceptionType != null && message == null)
            return MessageUtil.format(Messages.ErrorHelper_Exception, new Object[] { exceptionType.getClass().getName() });
        else if (exceptionType != null && message != null)
            return MessageUtil.format(Messages.ErrorHelper_ExceptionWithMessage, message, exceptionType.getClass().getName());
        else
            return message;

    }
}
