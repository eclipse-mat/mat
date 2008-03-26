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
import org.eclipse.mat.ui.editor.HeapEditor;


public class OpenPaneAction extends Action
{

    private HeapEditor editor;
    private String paneId;

    public OpenPaneAction(HeapEditor editor, String paneId)
    {
        super();
        this.editor = editor;
        this.paneId = paneId;        
    }

    @Override
    public void run()
    {
        ((HeapEditor) editor).addNewPage(paneId, null, true);
    }

}
