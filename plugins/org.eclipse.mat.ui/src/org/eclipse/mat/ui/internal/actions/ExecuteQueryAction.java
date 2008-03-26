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
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;


public class ExecuteQueryAction extends Action
{
    private HeapEditor editor;
    private QueryDescriptor descriptor;
    private String commandLine;

    public ExecuteQueryAction(HeapEditor editor, QueryDescriptor descriptor)
    {
        this.editor = editor;
        this.descriptor = descriptor;

        setText(descriptor.getName());
        setToolTipText(descriptor.getShortDescription());
        setImageDescriptor(ImageHelper.getImageDescriptor(descriptor));
    }

    public ExecuteQueryAction(HeapEditor editor, String commandLine)
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
            setImageDescriptor(ImageHelper.getImageDescriptor(descriptor));
        }
    }

    @Override
    public void run()
    {
        try
        {
            if (commandLine != null)
                QueryExecution.execute(editor, commandLine);
            else
                QueryExecution.execute(editor, descriptor);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

}
