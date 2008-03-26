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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.impl.test.html.FileUtils;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.PlatformUI;


public class ImportReportAction extends Action
{
    private HeapEditor heapEditor;

    public ImportReportAction(HeapEditor heapEditor)
    {
        super("Import Report", MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.IMPORT_REPORT));

        this.heapEditor = heapEditor;
    }

    @Override
    public void run()
    {
        FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), SWT.OPEN
                        | SWT.MULTI);

        dialog.setText("Import Report");
        dialog.setFilterExtensions(new String[] { "*.zip" });
        dialog.setFilterNames(new String[] { "Memory Analyzer Reports" });

        prepareFilterSelection(dialog);

        dialog.open();
        String[] names = dialog.getFileNames();

        if (names != null)
        {
            for (String name : names)
            {
                try
                {
                    IResult result = unzipAndOpen(dialog.getFilterPath(), name);
                    QueryResult queryResult = new QueryResult(null, "Report: " + name, result);
                    QueryExecution.displayResult(heapEditor, queryResult);
                }
                catch (IOException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }
            }
        }
    }

    public static IResult unzipAndOpen(String directory, String name) throws IOException
    {
        ZipFile zipFile = null;
        try
        {
            File targetDir = FileUtils.createTempDirectory("report", null);

            zipFile = new ZipFile(new File(directory, name));

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

            return new DisplayFileResult(new File(targetDir, "index.html"));
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
        File snapshot = new File(heapEditor.getSnapshotInput().getSnapshot().getSnapshotInfo().getPath());

        // extract prefix
        String name = snapshot.getName();
        int p = name.lastIndexOf('.');
        String prefix = p < 0 ? name : name.substring(0, p);

        dialog.setFilterPath(snapshot.getParentFile().getAbsolutePath());
        dialog.setFileName(prefix + "*.zip");
    }

}
