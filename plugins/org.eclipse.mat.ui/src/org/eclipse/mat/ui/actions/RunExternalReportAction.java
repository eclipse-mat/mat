/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.actions;

import java.io.File;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.Converters;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.PlatformUI;

public class RunExternalReportAction extends Action
{
    private static final String LAST_DIRECTORY_KEY = RunExternalReportAction.class.getName() + ".lastDir";//$NON-NLS-1$

    private MultiPaneEditor editor;

    public RunExternalReportAction(MultiPaneEditor editor)
    {
        super(Messages.RunExternalReportAction_RunReport,
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPERT_SYSTEM));
        this.editor = editor;
    }

    @Override
    public void run()
    {
        FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), //
                        SWT.OPEN | SWT.MULTI);
        dialog.setText(Messages.RunExternalReportAction_OpenReportDefinition);
        dialog.setFilterExtensions(new String[] { "*.xml" });//$NON-NLS-1$
        dialog.setFilterNames(new String[] { Messages.RunExternalReportAction_ReportDefinitions });

        IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
        String lastDirectory = prefs.getString(LAST_DIRECTORY_KEY);
        if (lastDirectory != null && lastDirectory.length() > 0)
            dialog.setFilterPath(lastDirectory);

        dialog.open();
        String[] names = dialog.getFileNames();

        if (names != null)
        {
            prefs.setValue(LAST_DIRECTORY_KEY, dialog.getFilterPath());

            for (String name : names)
            {
                try
                {
                    String fileName = new File(dialog.getFilterPath(), name).getAbsolutePath();
                    fileName = Converters.convertAndEscape(fileName.getClass(), fileName);
                    QueryExecution.executeCommandLine(editor, null, "create_report "//$NON-NLS-1$
                                    + fileName);
                }
                catch (SnapshotException e)
                {
                    ErrorHelper.showErrorMessage(e);
                }
            }
        }
    }

}
