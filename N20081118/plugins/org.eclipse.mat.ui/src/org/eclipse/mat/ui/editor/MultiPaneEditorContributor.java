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
package org.eclipse.mat.ui.editor;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.EditorActionBarContributor;

public class MultiPaneEditorContributor extends EditorActionBarContributor
{
    private Clipboard clipboard;
    protected AbstractEditorPane activeEditorPart;

    public MultiPaneEditorContributor()
    {
        makeActions();
    }

    @Override
    public void dispose()
    {
        super.dispose();

        if (clipboard != null)
        {
            clipboard.clearContents();
            clipboard.dispose();
        }

        activeEditorPart = null;
    }

    protected void makeActions()
    {}

    public void setActiveEditor(IEditorPart part)
    {
        AbstractEditorPane activeNestedEditor = null;
        if (part instanceof MultiPaneEditor)
        {
            activeNestedEditor = ((MultiPaneEditor) part).getActiveEditor();
        }
        setActivePage(activeNestedEditor);
    }

    public void setActivePage(AbstractEditorPane part)
    {
        if (activeEditorPart == part)
            return;
        if (part == null)
            return;
        activeEditorPart = part;

        IActionBars actionBars = getActionBars();
        if (actionBars != null)
        {
            contributeToToolBar(actionBars.getToolBarManager());
            actionBars.updateActionBars();
        }
    }

    public void contributeToToolBar(IToolBarManager toolBarManager)
    {
        toolBarManager.add(new Separator(org.eclipse.ui.console.IConsoleConstants.OUTPUT_GROUP));
    }

    public IEditorPart getActivePage()
    {
        return activeEditorPart;
    }

}
