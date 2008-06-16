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
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;


public class OpenNotesAction extends Action
{
    private static final String NOTES_VIEW_ID = MemoryAnalyserPlugin.PLUGIN_ID + ".views.TextEditorView";
    public OpenNotesAction()
    {
        super("Open Notes", MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.NOTEPAD));
    }

    @Override
    public void run()
    {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        
        try
        {
            if(page.findView(NOTES_VIEW_ID) == null)
                 page.showView(NOTES_VIEW_ID);
           

        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

}
