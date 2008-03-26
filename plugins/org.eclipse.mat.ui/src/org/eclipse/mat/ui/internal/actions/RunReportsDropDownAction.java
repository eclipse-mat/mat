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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.impl.test.SpecFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;


public class RunReportsDropDownAction extends EasyToolBarDropDown
{
    private HeapEditor editor;
    private Action importReportAction;

    public RunReportsDropDownAction(HeapEditor editor)
    {
        super("Run Expert System Test", MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPERT_SYSTEM), editor);

        this.editor = editor;
        importReportAction = new ImportReportAction(editor);
        importReportAction.setText("Import Report...");
    }

    @Override
    public void contribute(PopupMenu menu)
    {
        List<Action> reportActions = new ArrayList<Action>();
        for (SpecFactory.Report report : SpecFactory.instance().delegates())
        {
            Action action = new ExecuteQueryAction(editor, "default_report " + report.getExtensionIdentifier());
            action.setText(report.getName());
            reportActions.add(action);
        }

        Collections.sort(reportActions, new Comparator<Action>()
        {
            public int compare(Action o1, Action o2)
            {
                return o1.getText().compareTo(o2.getText());
            }
        });

        for (Action action : reportActions)
            menu.add(action);

        menu.addSeparator();
        menu.add(importReportAction);
    }
}
