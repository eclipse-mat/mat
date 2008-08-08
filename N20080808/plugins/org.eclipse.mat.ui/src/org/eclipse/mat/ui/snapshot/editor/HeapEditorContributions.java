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
package org.eclipse.mat.ui.snapshot.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.actions.QueryDropDownMenuAction;
import org.eclipse.mat.ui.actions.RunReportsDropDownAction;
import org.eclipse.mat.ui.editor.IMultiPaneEditorContributor;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.actions.ExecuteQueryAction;
import org.eclipse.mat.ui.internal.actions.OpenPaneAction;
import org.eclipse.mat.ui.snapshot.actions.OpenOQLStudioAction;
import org.eclipse.mat.ui.snapshot.actions.OpenObjectByIdAction;


public class HeapEditorContributions implements IMultiPaneEditorContributor
{
    HeapEditor editor;
    Action openOverview;
    Action openHistogram;
    Action openDominatorTree;
    Action openOQLPane;
  
    Action runExpertTest;
    Action openQueries;

    Action openObjectById;

    public void contributeToToolbar(IToolBarManager manager)
    {
        manager.add(openOverview);
        manager.add(openHistogram);
        manager.add(openDominatorTree);
        manager.add(openOQLPane);

        manager.add(new Separator());

        manager.add(runExpertTest);
        manager.add(openQueries);
        manager.add(new Separator());

        manager.add(openObjectById);
    }

    public void dispose()
    {}

    public void init(MultiPaneEditor editor)
    {
        this.editor = (HeapEditor) editor;
        openOverview = new OpenPaneAction((HeapEditor) editor, "OverviewPane");
        openOverview.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.INFO));
        openOverview.setToolTipText("Open Overview Pane");
        openHistogram = new ExecuteQueryAction((HeapEditor) editor, "histogram");
        openDominatorTree = new ExecuteQueryAction((HeapEditor) editor, "dominator_tree");
        openOQLPane = new OpenOQLStudioAction();

        runExpertTest = new RunReportsDropDownAction((HeapEditor) editor);
        openQueries = new QueryDropDownMenuAction((HeapEditor) editor);

        openObjectById = new OpenObjectByIdAction();
    }

}
