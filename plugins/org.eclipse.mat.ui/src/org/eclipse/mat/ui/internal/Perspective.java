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
package org.eclipse.mat.ui.internal;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IPlaceholderFolderLayout;
import org.osgi.framework.Bundle;

public class Perspective implements IPerspectiveFactory
{
    public enum Views
    {
        HISTORY_VIEW(MemoryAnalyserPlugin.PLUGIN_ID + ".views.SnapshotHistoryView"), //$NON-NLS-1$
        DETAILS_VIEW(MemoryAnalyserPlugin.PLUGIN_ID + ".views.SnapshotDetailsView"), //$NON-NLS-1$
        NOTES_VIEW(MemoryAnalyserPlugin.PLUGIN_ID + ".views.TextEditorView"), //$NON-NLS-1$
        INSPECTOR_VIEW(MemoryAnalyserPlugin.PLUGIN_ID + ".views.InspectorView"), //$NON-NLS-1$
        NAVIGATOR_VIEW(MemoryAnalyserPlugin.PLUGIN_ID + ".views.NavigatorView"), //$NON-NLS-1$
        ERROR_VIEW("org.eclipse.pde.runtime.LogView");//$NON-NLS-1$

        private final String id;

        private Views(String id)
        {
            this.id = id;
        }

        public String getId()
        {
            return this.id;
        }
    }

    public void createInitialLayout(IPageLayout layout)
    {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(true);

        IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.LEFT, (float) 0.26, editorArea);//$NON-NLS-1$
        topLeft.addView(Views.INSPECTOR_VIEW.getId());
        topLeft.addPlaceholder(Views.DETAILS_VIEW.getId());

        IPlaceholderFolderLayout bottomLeft = layout.createPlaceholderFolder("bottomLeft", IPageLayout.BOTTOM,//$NON-NLS-1$
                        (float) 0.60, Views.DETAILS_VIEW.getId());
        bottomLeft.addPlaceholder(Views.HISTORY_VIEW.getId());

        IFolderLayout bottomMiddle = layout.createFolder("bottomMiddle", IPageLayout.BOTTOM, (float) 0.80, editorArea);//$NON-NLS-1$
        bottomMiddle.addView(Views.NOTES_VIEW.getId());
        bottomMiddle.addPlaceholder(IPageLayout.ID_PROGRESS_VIEW);
        bottomMiddle.addView(Views.NAVIGATOR_VIEW.getId());

        layout.addShowViewShortcut(Views.HISTORY_VIEW.getId());
        layout.addShowViewShortcut(Views.DETAILS_VIEW.getId());
        layout.addShowViewShortcut(Views.INSPECTOR_VIEW.getId());
        layout.addShowViewShortcut(Views.NOTES_VIEW.getId());
        layout.addShowViewShortcut(Views.ERROR_VIEW.getId());
        layout.addShowViewShortcut(Views.NAVIGATOR_VIEW.getId());

        Bundle bundle = Platform.getBundle("org.eclipse.jdt.ui");//$NON-NLS-1$
        if (bundle != null)
            layout.addActionSet("org.eclipse.jdt.ui.JavaActionSet");//$NON-NLS-1$
    }
}
