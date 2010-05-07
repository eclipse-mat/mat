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
package org.eclipse.mat.ui.rcp;

import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.ide.IDE;

public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor
{

    public ApplicationWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer)
    {
        super(configurer);
    }

    public ActionBarAdvisor createActionBarAdvisor(IActionBarConfigurer configurer)
    {
        return new ApplicationActionBarAdvisor(configurer);
    }

    public void preWindowOpen()
    {
        IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
        configurer.setInitialSize(new Point(1024, 756));
        configurer.setShowCoolBar(false);
        configurer.setShowStatusLine(true);
        configurer.setShowProgressIndicator(true);
        configurer.setTitle(Messages.ApplicationWorkbenchWindowAdvisor_Eclipse_Memory_Analyzer);
    }

    @Override
    public void postWindowOpen()
    {
        super.postWindowOpen();

        try
        {
            String[] args = Platform.getApplicationArgs();
            if (args.length > 0)
            {
                Path path = new Path(args[0]);
                if (path.toFile().exists())
                {
                    IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
                    IEditorDescriptor descriptor = registry.getDefaultEditor(path.toOSString());
                    IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                                    new PathEditorInput(path), descriptor.getId(), true);
                }
            }
        }
        catch (PartInitException ignore)
        {
            // $JL-EXC$
        }
    }

    @Override
    public void openIntro()
    {
        boolean isStandby = PlatformUI.getWorkbench().getIntroManager().isIntroStandby(
                        PlatformUI.getWorkbench().getIntroManager().getIntro());
        PlatformUI.getWorkbench().getIntroManager().showIntro(getWindowConfigurer().getWindow(), isStandby);
    }
}
