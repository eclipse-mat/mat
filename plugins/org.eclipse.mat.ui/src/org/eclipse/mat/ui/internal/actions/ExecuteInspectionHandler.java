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
package org.eclipse.mat.ui.internal.actions;

import java.text.MessageFormat;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class ExecuteInspectionHandler extends AbstractHandler
{
    
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        try
        {
            String commandName = event.getParameter("org.eclipse.mat.ui.actions.executeInspection.commandName"); //$NON-NLS-1$
            QueryDescriptor query = QueryRegistry.instance().getQuery(commandName);
            if (query == null)
                throw new ExecutionException(MessageFormat.format(Messages.ExecuteInspectionHandler_UnknownInspection, commandName));

            // pick active editor
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null)
                throw new ExecutionException(
                                Messages.ExecuteInspectionHandler_NoActiveWorkbenchWindow);

            IWorkbenchPage page = window.getActivePage();
            if (page == null)
                throw new ExecutionException(Messages.ExecuteInspectionHandler_NoActivePage);

            IEditorPart editor = page.getActiveEditor();
            if (editor == null)
                throw new ExecutionException(Messages.ExecuteInspectionHandler_NoActiveEditor);
            if (!(editor instanceof MultiPaneEditor))
                throw new ExecutionException(Messages.ExecuteInspectionHandler_NotHeapEditor);

            QueryExecution.executeQuery((MultiPaneEditor) editor, query);

            return null;
        }
        catch (SnapshotException e)
        {
            throw new ExecutionException(e.getMessage(), e);
        }
    }
}
