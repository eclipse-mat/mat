/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.rcp.actions;

import java.io.File;
import java.util.Properties;

import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.intro.IIntroSite;
import org.eclipse.ui.intro.config.IIntroAction;

public class OpenEditorAction extends Action implements IIntroAction
{

    public OpenEditorAction()
    {}

    public void run(IIntroSite site, Properties params)
    {

        try
        {
            if (params == null)
                return;

            String path = params.getProperty("param");//$NON-NLS-1$
            if (path == null)
                return;

            if (!new File(path).exists())
                return;

            String editorId = params.getProperty("editorId"); //$NON-NLS-1$
            if (editorId == null)
                editorId = MemoryAnalyserPlugin.EDITOR_ID;

            IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), new PathEditorInput(
                            new Path(path)), editorId, true);
            PlatformUI.getWorkbench().getIntroManager().closeIntro(
                            PlatformUI.getWorkbench().getIntroManager().getIntro());
        }
        catch (PartInitException e)
        {
            throw new RuntimeException(e);
        }
    }

}
