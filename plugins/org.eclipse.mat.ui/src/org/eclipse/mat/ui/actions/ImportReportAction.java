/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and others.
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
package org.eclipse.mat.ui.actions;

import java.io.File;
import java.io.IOException;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.util.FileUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.PlatformUI;

public class ImportReportAction extends Action
{
    private MultiPaneEditor editor;
    private File reportZipFile;

    public ImportReportAction(MultiPaneEditor editor)
    {
        this(editor, null);
    }

    public ImportReportAction(MultiPaneEditor editor, File reportZipFile)
    {
        super(Messages.ImportReportAction_OpenReport,
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.IMPORT_REPORT));

        this.editor = editor;
        this.reportZipFile = reportZipFile;
    }

    @Override
    public void run()
    {
        if (reportZipFile == null)
        {

            FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                            SWT.OPEN | SWT.MULTI);

            dialog.setText(Messages.ImportReportAction_ImportReport);
            dialog.setFilterExtensions(new String[] { "*_*.zip" });//$NON-NLS-1$
            dialog.setFilterNames(new String[] { Messages.ImportReportAction_MemoryAnalyzerReports });

            prepareFilterSelection(dialog);

            dialog.open();
            String[] names = dialog.getFileNames();

            if (names != null)
            {
                for (String name : names)
                {
                    try
                    {
                        openReport(editor, new File(dialog.getFilterPath(), name));
                    }
                    catch (IOException e)
                    {
                        ErrorHelper.logThrowableAndShowMessage(e);
                    }
                }
            }
        }
        else
        {
            try
            {
                openReport(editor, reportZipFile);
            }
            catch (IOException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }
    }

    public static void openReport(MultiPaneEditor editor, File reportZipFile) throws IOException
    {
        IResult result = unzipAndOpen(reportZipFile);
        QueryResult queryResult = new QueryResult(null, Messages.ImportReportAction_Report + reportZipFile.getName(),
                        result);
        QueryExecution.displayResult(editor, null, null, queryResult, false);
    }

    public static IResult unzipAndOpen(File reportZipFile) throws IOException
    {

        File targetDir = FileUtils.createTempDirectory("report", null);//$NON-NLS-1$
        FileUtils.unzipFile(reportZipFile, targetDir);
        return new DisplayFileResult(new File(targetDir, "index.html"));//$NON-NLS-1$
    }

    private void prepareFilterSelection(FileDialog dialog)
    {
        // extract prefix
        File prefixFile = new File(editor.getQueryContext().getPrefix());
        String prefix = prefixFile.getName();
        if (prefix.endsWith(".")) //$NON-NLS-1$
            prefix = prefix.substring(0, prefix.length() - 1);

        dialog.setFilterPath(prefixFile.getParentFile().getAbsolutePath());
        dialog.setFileName(prefix + "_");//$NON-NLS-1$
    }

}
