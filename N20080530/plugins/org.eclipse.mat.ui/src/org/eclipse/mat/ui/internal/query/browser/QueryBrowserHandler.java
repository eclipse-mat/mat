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
package org.eclipse.mat.ui.internal.query.browser;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;


public class QueryBrowserHandler extends AbstractHandler
{

    public QueryBrowserHandler()
    {}

    public Object execute(ExecutionEvent executionEvent)
    {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        
        IWorkbenchPage page = window.getActivePage();
        if (page == null)
            return null;
        
        IEditorPart activeEditor = page.getActiveEditor();
        if (!(activeEditor instanceof HeapEditor))
            return null;

        new QueryBrowserPopup((HeapEditor)activeEditor).open();
        
        return null;
    }
}
