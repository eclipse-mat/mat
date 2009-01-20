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
package org.eclipse.mat.ui.actions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        super(Messages.ImportReportAction_OpenReport, MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.IMPORT_REPORT));

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
            dialog.setFilterExtensions(new String[] { "*.zip" });//$NON-NLS-1$
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
        QueryResult queryResult = new QueryResult(null, Messages.ImportReportAction_Report + reportZipFile.getName(), result);
        QueryExecution.displayResult(editor, null, null, queryResult, false);
    }

    public static IResult unzipAndOpen(File reportZipFile) throws IOException
    {
        ZipFile zipFile = null;
        try
        {
            File targetDir = FileUtils.createTempDirectory("report", null);//$NON-NLS-1$

            zipFile = new ZipFile(reportZipFile);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                File file = new File(targetDir, entry.getName());

                if (entry.isDirectory())
                {
                    file.mkdirs();
                }
                else
                {
                    file.getParentFile().mkdirs();

                    FileOutputStream fout = null;
                    BufferedOutputStream out = null;
                    try
                    {
                        fout = new FileOutputStream(file);
                        out = new BufferedOutputStream(fout);
                        FileUtils.copy(zipFile.getInputStream(entry), out);
                    }
                    finally
                    {
                        try
                        {
                            if (out != null)
                                out.close();
                        }
                        catch (IOException ignore)
                        {
                            // $JL-EXC$
                        }

                        try
                        {
                            if (fout != null)
                                fout.close();
                        }
                        catch (IOException ignore)
                        {
                            // $JL-EXC$
                        }
                    }
                }
            }

            return new DisplayFileResult(new File(targetDir, "index.html"));//$NON-NLS-1$
        }
        finally
        {
            try
            {
                if (zipFile != null)
                    zipFile.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
        }
    }

    private void prepareFilterSelection(FileDialog dialog)
    {
        File snapshot = editor.getQueryContext().getPrimaryFile();

        // extract prefix
        String name = snapshot.getName();
        int p = name.lastIndexOf('.');
        String prefix = p < 0 ? name : name.substring(0, p);

        dialog.setFilterPath(snapshot.getParentFile().getAbsolutePath());
        dialog.setFileName(prefix + "*.zip");//$NON-NLS-1$
    }

}
