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
package org.eclipse.mat.ui.internal.viewer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.RendererRegistry;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer.ControlItem;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer.Key;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/* package */final class ExportActions
{
    /* package */static class HtmlExport extends Action
    {
        private Control control;
        private RefinedStructuredResult result;
        private IQueryContext queryContext;

        public HtmlExport(Control control, RefinedStructuredResult result, IQueryContext queryContext)
        {
            super(Messages.ExportActions_ExportToHTML, MemoryAnalyserPlugin
                            .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_HTML));
            this.control = control;
            this.result = result;
            this.queryContext = queryContext;
        }

        @Override
        public void run()
        {
            ExportDialog dialog = new ExportDialog(control.getShell(), //
                            new String[] { Messages.ExportActions_ZippedWebPage }, //
                            new String[] { "*.zip" });//$NON-NLS-1$
            final String fileName = dialog.open();
            if (fileName == null)
                return;

            // extract limit
            ControlItem controlItem = (ControlItem) control.getData(Key.CONTROL);
            final int limit = (controlItem != null && controlItem.getTotals() != null) ? controlItem.getTotals()
                            .getVisibleItems() : 25;

            // extract expanded items

            // problem:
            // because the result tree can create objects on every #getChilden()
            // call, objects in the hash map will not match and the expansion
            // state of nested objects will not be acknowledged

            // the requirement to implement #equals and #hashCode seems too
            // heavy (and might not help, because one and the same object might
            // show up multiple times in the tree, but the decision has too be
            // made structurally)
            if (control instanceof Tree)
            {
                Tree tree = (Tree) control;

                LinkedList<TreeItem> stack = new LinkedList<TreeItem>();

                TreeItem[] items = tree.getItems();
                for (TreeItem treeItem : items)
                    stack.add(treeItem);

                final Set<Object> expanded = new HashSet<Object>();
                while (!stack.isEmpty())
                {
                    TreeItem item = stack.removeFirst();
                    if (item.getExpanded())
                    {
                        Object data = item.getData();
                        if (data != null)
                            expanded.add(data);

                        items = item.getItems();
                        for (TreeItem treeItem : items)
                            stack.add(treeItem);
                    }
                }

                result.setSelectionProvider(new ISelectionProvider()
                {
                    public boolean isExpanded(Object row)
                    {
                        return expanded.contains(row);
                    }

                    public boolean isSelected(Object row)
                    {
                        return false;
                    }
                });
            }

            new Job(Messages.ExportActions_ExportHTML)
            {
                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    try
                    {
                        QuerySpec spec = new QuerySpec(Messages.ExportActions_Export, result);
                        spec.set(Params.Rendering.LIMIT, String.valueOf(limit));
                        TestSuite suite = new TestSuite.Builder(spec)//
                                        .output(new File(fileName)) //
                                        .build(queryContext);
                        suite.execute(new ProgressMonitorWrapper(monitor));
                        return Status.OK_STATUS;
                    }
                    catch (IOException e)
                    {
                        return ErrorHelper.createErrorStatus(e);
                    }
                    catch (SnapshotException e)
                    {
                        return ErrorHelper.createErrorStatus(e);
                    }
                }

            }.schedule();
        }
    }

    /* package */static class CsvExport extends Action
    {
        private Control control;
        private IResult result;
        private IQueryContext queryContext;

        public CsvExport(Control control, IResult result, IQueryContext queryContext)
        {
            super(Messages.ExportActions_ExportToCSV, MemoryAnalyserPlugin
                            .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_CSV));
            this.control = control;
            this.result = result;
            this.queryContext = queryContext;
        }

        @Override
        public void run()
        {
            ExportDialog dialog = new ExportDialog(control.getShell(), //
                            new String[] { Messages.ExportActions_CsvFiles }, //
                            new String[] { "*.csv" });//$NON-NLS-1$
            final String fileName = dialog.open();
            if (fileName == null)
                return;

            new Job(Messages.ExportActions_ExportCSV)
            {

                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    PrintWriter writer = null;

                    try
                    {
                        IOutputter outputter = RendererRegistry.instance().match("csv", result.getClass());//$NON-NLS-1$
                        writer = new PrintWriter(new FileWriter(fileName));
                        outputter.process(new ContextImpl(queryContext, //
                                        new File(fileName).getParentFile()), result, writer);
                        writer.flush();
                        writer.close();
                    }
                    catch (IOException e)
                    {
                        return ErrorHelper.createErrorStatus(e);
                    }
                    finally
                    {
                        if (writer != null)
                            writer.close();
                    }

                    return Status.OK_STATUS;
                }

            }.schedule();

        }
    }

    /* package */static class TxtExport extends Action
    {
        private Control control;

        public TxtExport(Control control)
        {
            super(Messages.ExportActions_ExportToTxt, MemoryAnalyserPlugin
                            .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_TXT));
            this.control = control;
        }

        @Override
        public void run()
        {
            ExportDialog dialog = new ExportDialog(control.getShell(), //
                            new String[] { Messages.ExportActions_PlainText }, //
                            new String[] { "*.txt" });//$NON-NLS-1$
            String fileName = dialog.open();
            if (fileName == null)
                return;

            Copy.exportToTxtFile(control, fileName);
        }
    }

    private ExportActions()
    {}

    private static class ContextImpl implements IOutputter.Context
    {
        private File outputDir;
        private IQueryContext context;

        public ContextImpl(IQueryContext context, File outputDir)
        {
            this.context = context;
            this.outputDir = outputDir;
        }

        public String getId()
        {
            return "X"; //$NON-NLS-1$
        }

        public int getLimit()
        {
            return 0;
        }

        public File getOutputDirectory()
        {
            return outputDir;
        }

        public String getPathToRoot()
        {
            return "";//$NON-NLS-1$
        }

        public IQueryContext getQueryContext()
        {
            return context;
        }

        public String addIcon(URL icon)
        {
            return null;
        }

        public String addContextResult(String name, IResult result)
        {
            return null;
        }

        public boolean hasLimit()
        {
            return false;
        }

        public boolean isColumnVisible(int columnIndex)
        {
            return true;
        }

        public String param(String key, String defaultValue)
        {
            return defaultValue;
        }

        public String param(String key)
        {
            return null;
        }

        public boolean isTotalsRowVisible()
        {
            return true;
        }
    }

    private static class ExportDialog
    {

        private FileDialog dlg;

        public ExportDialog(Shell shell, String[] names, String[] extentions)
        {
            dlg = new FileDialog(shell, SWT.SAVE);
            dlg.setFilterNames(names);
            dlg.setFilterExtensions(extentions);
        }

        public String open()
        {
            // We store the selected file name in fileName
            String fileName = null;

            // The user has finished when one of the
            // following happens:
            // 1) The user dismisses the dialog by pressing Cancel
            // 2) The selected file name does not exist
            // 3) The user agrees to overwrite existing file
            boolean done = false;

            while (!done)
            {
                // Open the File Dialog
                fileName = dlg.open();
                if (fileName == null)
                {
                    // User has canceled, so quit and return
                    done = true;
                }
                else
                {
                    // User has selected a file; see if it already exists
                    File file = new File(fileName);
                    if (file.exists())
                    {
                        // The file already exists; asks for confirmation
                        MessageBox mb = new MessageBox(dlg.getParent(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
                        mb.setMessage(MessageFormat.format(Messages.ExportActions_AlreadyExists, fileName));

                        // If they click Yes, we're done and we drop out. If
                        // they click No, we redisplay the File Dialog
                        done = mb.open() == SWT.YES;
                    }
                    else
                    {
                        // File does not exist, so drop out
                        done = true;
                    }
                }
            }

            return fileName;
        }
    }

}
