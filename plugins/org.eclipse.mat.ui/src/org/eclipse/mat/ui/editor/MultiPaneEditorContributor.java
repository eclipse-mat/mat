/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.EditorActionBarContributor;

public class MultiPaneEditorContributor extends EditorActionBarContributor
{
    private AbstractEditorPane activeEditorPart;

    public MultiPaneEditorContributor()
    {
        makeActions();
    }

    @Override
    public void dispose()
    {
        super.dispose();
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
    }

    public IEditorPart getActivePage()
    {
        return activeEditorPart;
    }

}
