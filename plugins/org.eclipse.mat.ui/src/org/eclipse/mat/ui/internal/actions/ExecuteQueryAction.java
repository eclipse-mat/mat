/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - run using dialog
 *******************************************************************************/
package org.eclipse.mat.ui.internal.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;

public class ExecuteQueryAction extends Action
{
    private MultiPaneEditor editor;
    private QueryDescriptor descriptor;
    private String commandLine;

    public ExecuteQueryAction(MultiPaneEditor editor, QueryDescriptor descriptor)
    {
        this.editor = editor;
        this.descriptor = descriptor;

        setText(descriptor.getName());
        setToolTipText(descriptor.getShortDescription());
        setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(descriptor));
    }

    public ExecuteQueryAction(MultiPaneEditor editor, String commandLine)
    {
        this.editor = editor;
        this.commandLine = commandLine;

        int p = commandLine.indexOf(' ');
        String name = p < 0 ? commandLine : commandLine.substring(0, p);
        descriptor = QueryRegistry.instance().getQuery(name);

        setText(commandLine);

        if (descriptor != null)
        {
            setToolTipText(descriptor.getShortDescription());
            setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(descriptor));
        }
    }

    @Override
    public void run()
    {
        run(false);
    }

    @Override
    public void runWithEvent(Event e)
    {
        // Allow the prompt to be forced even if the query is ready to execute
        boolean forcePrompt = (e.stateMask & SWT.MOD2) == SWT.MOD2 || (e.stateMask & SWT.BUTTON3) == SWT.BUTTON3;
        run(forcePrompt);
    }

    public String getHelpUrl()
    {
        if (descriptor != null)
            return descriptor.getHelpUrl();
        else
            return null;
    }

    private void run(boolean forcePrompt)
    {
        try
        {
            if (commandLine != null)
                QueryExecution.executeCommandLine(editor, null, commandLine, forcePrompt);
            else
                QueryExecution.executeQuery(editor, descriptor);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

}
