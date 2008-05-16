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
    private static final String HISTORY_VIEW = MemoryAnalyserPlugin.PLUGIN_ID + ".views.SnapshotHistoryView";
    private static final String DETAILS_VIEW = MemoryAnalyserPlugin.PLUGIN_ID + ".views.SnapshotDetailsView";
    private static final String NOTES_VIEW = MemoryAnalyserPlugin.PLUGIN_ID + ".views.TextEditorView";
    private static final String INSPECTOR_VIEW = MemoryAnalyserPlugin.PLUGIN_ID + ".views.InspectorView";
    private static final String ERROR_VIEW = "org.eclipse.pde.runtime.LogView";

    public void createInitialLayout(IPageLayout layout)
    {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(true);

        IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.LEFT, (float) 0.26, editorArea);
        topLeft.addView(INSPECTOR_VIEW);
        topLeft.addPlaceholder(DETAILS_VIEW);

        IPlaceholderFolderLayout bottomLeft = layout.createPlaceholderFolder("bottomLeft", IPageLayout.BOTTOM,
                        (float) 0.60, DETAILS_VIEW);
        bottomLeft.addPlaceholder(HISTORY_VIEW);

        IFolderLayout bottomMiddle = layout.createFolder("bottomMiddle", IPageLayout.BOTTOM,
                        (float) 0.80, editorArea);        
        bottomMiddle.addView(NOTES_VIEW);
        bottomMiddle.addPlaceholder(IPageLayout.ID_PROGRESS_VIEW);
        
        layout.addShowViewShortcut(HISTORY_VIEW);
        layout.addShowViewShortcut(DETAILS_VIEW);
        layout.addShowViewShortcut(INSPECTOR_VIEW);
        layout.addShowViewShortcut(NOTES_VIEW);
        layout.addShowViewShortcut(ERROR_VIEW);        

        Bundle bundle = Platform.getBundle("org.eclipse.jdt.ui");
        if (bundle != null)
            layout.addActionSet("org.eclipse.jdt.ui.JavaActionSet");
    }
}
