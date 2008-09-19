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
package org.eclipse.mat.ui.internal.query.arguments;

import java.text.MessageFormat;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
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
            String commandName = event.getParameter("org.eclipse.mat.ui.actions.executeInspection.commandName");
            QueryDescriptor query = QueryRegistry.instance().getQuery(commandName);
            if (query == null)
                throw new ExecutionException(MessageFormat.format("Unknown inspection: {0}", commandName));

            // pick active editor
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null)
                throw new ExecutionException(
                                "No active workbench window found. Run command with an heap dump window active.");

            IWorkbenchPage page = window.getActivePage();
            if (page == null)
                throw new ExecutionException("No active page found. Run command with an heap dump window active.");

            IEditorPart editor = page.getActiveEditor();
            if (editor == null)
                throw new ExecutionException("No active editor found. Run command with an heap dump window active.");
            if (!(editor instanceof MultiPaneEditor))
                throw new ExecutionException("Not a heap editor. Run command with an heap dump window active.");

            QueryExecution.executeQuery((MultiPaneEditor) editor, query);

            return null;
        }
        catch (SnapshotException e)
        {
            throw new ExecutionException(e.getMessage(), e);
        }
    }
}
