/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - submenus/hidden reports
 *******************************************************************************/
package org.eclipse.mat.ui.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.report.SpecFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.actions.ExecuteQueryAction;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;

public class RunReportsDropDownAction extends EasyToolBarDropDown
{
    private MultiPaneEditor editor;
    private Action importReportAction;
    private Action runExternalReportAction;

    public RunReportsDropDownAction(MultiPaneEditor editor)
    {
        super(Messages.RunReportsDropDownAction_RunExpertSystemTest, MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPERT_SYSTEM), editor);

        this.editor = editor;
        this.importReportAction = new ImportReportAction(editor);
        this.runExternalReportAction = new RunExternalReportAction(editor);
    }

    @Override
    public void contribute(PopupMenu menu)
    {
        List<Action> reportActions = new ArrayList<Action>();
        for (SpecFactory.Report report : SpecFactory.instance().delegates())
        {
            Action action = new ExecuteQueryAction(editor, "default_report " + report.getExtensionIdentifier());//$NON-NLS-1$
            action.setText(report.getName());
            action.setDescription(report.getDescription());
            action.setToolTipText(report.getDescription());
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
        {
            String name[] = action.getText().split("/"); //$NON-NLS-1$
            for (int i = 0; i < name.length; ++i)
            {
                // Strip off ordering e.g. 1|Name
                name[i] = name[i].replaceFirst("^\\d\\|", ""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            // Create sub-menus
            PopupMenu m1 = menu;
            int i;
            for (i = 0; i < name.length - 1; ++i)
            {
                if (name[i].equals(Category.HIDDEN))
                    break;
                PopupMenu m2 = m1.getChildMenu(name[i]);
                if (m2 == null)
                {
                    m2 = new PopupMenu(name[i]);
                    m1.add(m2);
                }
                m1 = m2;
            }
            if (i >= name.length - 1)
            {
                // Finally, add the action
                action.setText(name[name.length - 1]);
                m1.add(action);
            }
        }

        menu.addSeparator();
        menu.add(importReportAction);
        menu.add(runExternalReportAction);
    }
}
