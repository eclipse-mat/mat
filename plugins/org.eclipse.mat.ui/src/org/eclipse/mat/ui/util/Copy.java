/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - alignment and order of columns
 *******************************************************************************/
package org.eclipse.mat.ui.util;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.Alignment;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.RendererRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

public abstract class Copy
{
    protected Control control;
    protected Item[] selection;

    public static void copyToClipboard(Control control)
    {
        Item[] selection = (control instanceof Table) ? ((Table) control).getSelection() //
                        : ((Tree) control).getSelection();

        new CopyToClipboard(control, selection).doCopy();
    }

    public static void copyToClipboard(String text, Display display)
    {
        Clipboard clipboard = new Clipboard(display);
        clipboard.setContents(new Object[] { text.toString() }, new Transfer[] { TextTransfer.getInstance() });
        clipboard.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // private methods
    // //////////////////////////////////////////////////////////////

    private Copy(Control control, Item[] selection)
    {
        this.control = control;
        this.selection = selection != null && selection.length > 0 ? selection : null;
    }

    private static class CopyToClipboard extends Copy
    {
        private CopyToClipboard(Control control, Item[] selection)
        {
            super(control, selection);
        }
        private void doCopy()
        {
            IResult t = control instanceof Table ? convert((Table)control) : convert((Tree)control);
            StringWriter writer = null;
            try
            {
                IOutputter outputter = RendererRegistry.instance().match("txt", t.getClass());//$NON-NLS-1$
                writer = new StringWriter();
                outputter.process(new ContextImpl(null, null), t, writer);
                writer.flush();
                writer.close();
                copyToClipboard(writer.toString(), control.getDisplay());
            }
            catch (IOException e)
            {
                MemoryAnalyserPlugin.log(e);
                return;
            }
        }
    }

    protected IResultTable convert(Table ct)
    {
        IResultTable t = new IResultTable() {

            @Override
            public Column[] getColumns()
            {
                TableColumn tcs[] = ct.getColumns();
                int order[] = ct.getColumnOrder();
                Column c[] = new Column[order.length];
                for (int i = 0; i < order.length; ++i)
                {
                    int col = order[i];
                    TableColumn tc = tcs[col];
                    c[i] = new Column(tc.getText());
                    switch (tc.getAlignment()) {
                        case SWT.RIGHT:
                            c[i].aligning(Alignment.RIGHT);
                            break;
                        case SWT.CENTER:
                            c[i].aligning(Alignment.CENTER);
                            break;
                        case SWT.LEFT:
                            c[i].aligning(Alignment.LEFT);
                            break;
                    }
                }
                return c;
            }

            @Override
            public Object getColumnValue(Object row, int columnIndex)
            {
                return ((TableItem)row).getText(ct.getColumnOrder()[columnIndex]);
            }

            @Override
            public IContextObject getContext(Object row)
            {
                return null;
            }

            @Override
            public ResultMetaData getResultMetaData()
            {
                return null;
            }

            @Override
            public int getRowCount()
            {
                if (selection != null)
                    return selection.length;
                else
                    return ct.getItemCount();
            }

            @Override
            public Object getRow(int rowId)
            {
                if (selection != null)
                    return selection[rowId];
                else
                    return ct.getItems()[rowId];
            }

        };
        return t;
    }

    interface IRTS extends IResultTree, ISelectionProvider {};
    protected IResultTree convert(Tree ct)
    {
        final Set<Item> selset = selection != null ? new HashSet<Item>(Arrays.asList(selection)) : Collections.<Item>emptySet();
        IResultTree t = new IRTS() {

            @Override
            public Column[] getColumns()
            {
                TreeColumn tcs[] = ct.getColumns();
                int order[] = ct.getColumnOrder();
                Column c[] = new Column[tcs.length];
                for (int i = 0; i < order.length; ++i)
                {
                    int col = order[i];
                    TreeColumn tc = tcs[col];
                    c[i] = new Column(tc.getText());
                    switch (tc.getAlignment()) {
                        case SWT.RIGHT:
                            c[i].aligning(Alignment.RIGHT);
                            break;
                        case SWT.CENTER:
                            c[i].aligning(Alignment.CENTER);
                            break;
                        case SWT.LEFT:
                            c[i].aligning(Alignment.LEFT);
                            break;

                    }
                }
                return c;
            }

            @Override
            public Object getColumnValue(Object row, int columnIndex)
            {
                TreeItem treeitem = (TreeItem)row;
                int[] order = ct.getColumnOrder();
                // Special case for navigator view or other tree without columns
                if (order.length == 0 || columnIndex == -1)
                    return treeitem.getText();
                return treeitem.getText(order[columnIndex]);
            }

            @Override
            public IContextObject getContext(Object row)
            {
                return null;
            }

            @Override
            public ResultMetaData getResultMetaData()
            {
                return null;
            }

            @Override
            public List<?> getElements()
            {
                if (selection == null)
                    return Arrays.asList(ct.getItems());
                ArrayList<TreeItem>ret = new ArrayList<TreeItem>();
                /*
                 * Add the selection items which do not
                 * have a parent in the selection - these would be missed
                 * as the parent would not be printed or expanded.
                 */
                for (Item i : selection)
                {
                    if (i instanceof TreeItem)
                    {
                        TreeItem ti = (TreeItem)i;
                        TreeItem pi = ti.getParentItem();
                        if (!selset.contains(pi))
                            ret.add(ti);
                    }
                }
                return ret;
            }

            @Override
            public boolean hasChildren(Object element)
            {
                int ret = ((TreeItem)element).getItemCount();
                return ret > 0;
            }

            @Override
            public List<?> getChildren(Object parent)
            {
                if (selection == null)
                    return Arrays.asList(((TreeItem)parent).getItems());
                ArrayList<TreeItem>ret = new ArrayList<TreeItem>();
                for (TreeItem ti : ((TreeItem)parent).getItems())
                {
                    if (selset.contains(ti))
                        ret.add(ti);
                }
                return ret;
            }

            @Override
            public boolean isSelected(Object row)
            {
                return selset.contains(row);
            }

            @Override
            public boolean isExpanded(Object row)
            {
                return ((TreeItem)row).getExpanded();
            }

        };
        return t;
    }

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
}
