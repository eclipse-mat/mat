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

import org.eclipse.jface.action.Action;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;


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
        try
        {
            if (commandLine != null)
                QueryExecution.executeCommandLine(editor, null, commandLine);
            else
                QueryExecution.executeQuery(editor, descriptor);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

}
