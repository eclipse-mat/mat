/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class SearchOnTyping
{
    public static final void attachTo(Table table, ITableLabelProvider labelProvider, int columnIndex)
    {
        table.addKeyListener(new SearchKeyListener(new TableImpl(table, labelProvider, columnIndex)));
    }

    public static final void attachTo(Tree tree, ITableLabelProvider labelProvider, int columnIndex)
    {
        tree.addKeyListener(new SearchKeyListener(new TreeImpl(tree, labelProvider, columnIndex)));
    }

    public static final void attachTo(Control control, int columnIndex)
    {
        if (control instanceof Tree)
            control.addKeyListener(new SearchKeyListener(new TreeImpl((Tree) control, null, columnIndex)));
        else if (control instanceof Table)
            control.addKeyListener(new SearchKeyListener(new TableImpl((Table) control, null, columnIndex)));
        else
            throw new RuntimeException(Messages.SearchOnTyping_Exception + control.getClass().getName());
    }

    private SearchOnTyping()
    {}

    // //////////////////////////////////////////////////////////////
    // search implementation for tree and table
    // //////////////////////////////////////////////////////////////

    private static final int SELECTION_LIMIT = 1000;

    private interface SearchThingy
    {
        Control getControl();

        void deselectAll();

        void select(String pattern);
    }

    private static final class TableImpl implements SearchThingy
    {
        Table table;
        ITableLabelProvider labelProvider;
        int columnIndex;

        private TableImpl(Table t, ITableLabelProvider labelProvider, int columnIndex)
        {
            this.table = t;
            this.labelProvider = labelProvider;
            this.columnIndex = columnIndex;
        }

        public Control getControl()
        {
            return table;
        }

        public void deselectAll()
        {
            table.deselectAll();

        }

        public void select(String pattern)
        {
            final ArrayInt selected = new ArrayInt();
            int itemCount = table.getItemCount();
            for (int ii = 0; ii < itemCount; ii++)
            {
                if (selected.size() > SELECTION_LIMIT)
                    break;

                TableItem item = table.getItem(ii);
                String text = null;

                if (labelProvider != null)
                {
                    Object data = item.getData();
                    text = data != null ? labelProvider.getColumnText(data, columnIndex) : null;
                }
                else
                {
                    text = item.getText(columnIndex);
                }

                if (text != null)
                {
                    text = text.toLowerCase();
                    if (text.indexOf(pattern) >= 0)
                        selected.add(ii);
                }
            }

            table.setSelection(selected.toArray());
        }
    }

    private static final class TreeImpl implements SearchThingy
    {
        Tree tree;
        ITableLabelProvider labelProvider;
        int columnIndex;

        private TreeImpl(Tree tree, ITableLabelProvider labelProvider, int columnIndex)
        {
            this.tree = tree;
            this.labelProvider = labelProvider;
            this.columnIndex = columnIndex;
        }

        public void deselectAll()
        {
            tree.deselectAll();
        }

        public Control getControl()
        {
            return tree;
        }

        public void select(String pattern)
        {
            final List<TreeItem> items = new ArrayList<TreeItem>();

            LinkedList<TreeItem> stack = new LinkedList<TreeItem>();
            for (int ii = 0; ii < tree.getItemCount(); ii++)
                stack.add(tree.getItem(ii));

            while (!stack.isEmpty())
            {
                if (items.size() > SELECTION_LIMIT)
                    return;

                TreeItem item = stack.removeFirst();

                String text = null;
                if (labelProvider != null)
                {
                    Object data = item.getData();
                    text = data != null ? labelProvider.getColumnText(data, columnIndex) : null;
                }
                else
                {
                    text = item.getText(columnIndex);
                }

                if (text != null)
                {
                    text = text.toLowerCase();
                    if (text.indexOf(pattern) >= 0)
                        items.add(item);
                }

                if (item.getExpanded())
                {
                    int itemCount = item.getItemCount();
                    for (int ii = 0; ii < itemCount; ii++)
                        stack.add(item.getItem(ii));
                }
            }

            tree.setSelection(items.toArray(new TreeItem[0]));
        }
    }

    // //////////////////////////////////////////////////////////////
    // common implementation
    // //////////////////////////////////////////////////////////////

    private static final class SearchKeyListener implements KeyListener
    {
        SearchThingy thingy;

        public SearchKeyListener(SearchThingy thingy)
        {
            this.thingy = thingy;
        }

        public void keyPressed(KeyEvent e)
        {
            // Space is used for keyboard selection, so exclude it
            if (!Character.isISOControl(e.character) && e.character != ' ')
            {
                SearchPopup search = new SearchPopup(thingy, Character.toString(e.character));
                search.open();
                e.doit = false;
            }
        }

        public void keyReleased(KeyEvent e)
        {}
    }

    private static class SearchPopup extends PopupDialog
    {
        SearchThingy thingy;
        String initialPattern;

        Text filterText;

        private SearchPopup(SearchThingy thingy, String initialPattern)
        {
            super(thingy.getControl().getShell(), SWT.RESIZE, true, false, false, false, null, null);
            this.thingy = thingy;
            this.initialPattern = initialPattern;
        }

        @Override
        protected Point getInitialLocation(Point initialSize)
        {
            Control control = thingy.getControl();

            // Point size = control.getSize();
            // int x = Math.max(0, size.x - initialSize.x);
            // int y = Math.max(0, size.y - initialSize.y);

            Point location = control.getShell().getDisplay().map(control, null, control.getLocation());

            return new Point(location.x /* + x */, location.y /* + y */);
        }

        @Override
        protected Control createDialogArea(Composite parent)
        {
            Composite composite = (Composite) super.createDialogArea(parent);

            filterText = new Text(composite, SWT.NONE);
            filterText.setText(initialPattern);
            filterText.setSelection(initialPattern.length());

            GC gc = new GC(parent);
            gc.setFont(parent.getFont());
            FontMetrics fontMetrics = gc.getFontMetrics();
            gc.dispose();

            GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).hint(100,
                            Dialog.convertHeightInCharsToPixels(fontMetrics, 1)).applyTo(filterText);

            filterText.addModifyListener(new ModifyListener()
            {
                public void modifyText(ModifyEvent e)
                {
                    searchChanged();
                }
            });

            searchChanged();

            return composite;
        }

        protected Control getFocusControl()
        {
            return filterText;
        }

        private void searchChanged()
        {
            final String text = filterText.getText().trim();

            if (text.length() < 3)
                thingy.deselectAll();
            else
            {
                new SearchJob(thingy, text, filterText).schedule(250);
            }
        }
    }

    private static class SearchJob extends Job
    {
        SearchThingy thingy;
        String text;
        Text filterText;

        private SearchJob(SearchThingy thingy, String text, Text filterText)
        {
            super(Messages.SearchOnTyping_searching);
            setSystem(true);

            this.thingy = thingy;
            this.text = text;
            this.filterText = filterText;
        }

        @Override
        protected IStatus run(IProgressMonitor monitor)
        {
            if (!filterText.isDisposed())
            {
                filterText.getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (filterText.isDisposed())
                            return;

                        String t = filterText.getText();
                        if (text.equals(t))
                            thingy.select(text.toLowerCase());
                    }
                });
            }

            return Status.OK_STATUS;
        }
    }
}
