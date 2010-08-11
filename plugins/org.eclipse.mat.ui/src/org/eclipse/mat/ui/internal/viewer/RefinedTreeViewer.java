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
package org.eclipse.mat.ui.internal.viewer;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;

public class RefinedTreeViewer extends RefinedResultViewer
{
    Tree tree;
    TreeEditor treeEditor;

    public RefinedTreeViewer(IQueryContext context, QueryResult result, RefinedTree tree)
    {
        super(context, result, tree);
    }

    @Override
    public void init(Composite parent, MultiPaneEditor editor, AbstractEditorPane pane)
    {
        super.init(new TreeAdapter(), parent, editor, pane);

        tree.addListener(SWT.Expand, new Listener()
        {
            public void handleEvent(Event event)
            {
                TreeItem parentItem = (TreeItem) event.item;
                doExpand(parentItem);
            }

        });

        tree.addListener(SWT.DefaultSelection, new Listener()
        {
            public void handleEvent(Event event)
            {
                TreeItem widget = (TreeItem) event.item;
                if (widget == null)
                    return;
                if (widget.isDisposed())
                    return;

                Object data = widget.getData();
                if (data == null)
                    return;

                if (widget.getExpanded())
                {
                    widget.setExpanded(false);
                }
                else
                {
                    if (widget.getItemCount() > 0)
                    {
                        widget.setExpanded(true);
                        Object ctrl = widget.getData(Key.CONTROL);
                        if (ctrl == null)
                            doExpand(widget);
                    }
                }
            }
        });
    }
    
    @Override
    protected void configureColumns()
    {
        /* temporarily removed because of dependency on 3.5, see comments 3-5 in bug 307031 */
//    	ConfigureColumns.forTree(tree, editor.getEditorSite());
    }

    private void doExpand(TreeItem parentItem)
    {
        ControlItem parentCtrl = (ControlItem) parentItem.getData(Key.CONTROL);
        if (parentCtrl == null) // initial expansion
        {
            int level = 1;
            TreeItem grandfather = parentItem.getParentItem();
            if (grandfather != null)
            {
                ControlItem pc = (ControlItem) grandfather.getData(Key.CONTROL);
                level = pc.level + 1;
            }
            parentItem.setData(Key.CONTROL, parentCtrl = new ControlItem(false, level));
        }

        synchronized (parentCtrl)
        {
            if (parentCtrl.children == null)
            {
                Thread t = new ReadDataThread(RefinedTreeViewer.this, parentCtrl, parentItem, parentItem.getData(),
                                false);
                t.start();
                try
                {
                    parentCtrl.wait(100);
                    // $JL-WAIT$

                    if (parentCtrl.children == null || parentCtrl.children.isEmpty())
                    {
                        TreeItem[] items = parentItem.getItems();
                        for (int ii = 0; ii < items.length; ii++)
                            items[ii].dispose();
                        applyUpdating(new TreeItem(parentItem, SWT.NONE, 0));
                        return;
                    }
                    else
                    {
                        parentCtrl.hasBeenPainted = true;
                        doUpdateChildren(parentItem, parentCtrl);
                    }
                }
                catch (InterruptedException e)
                {
                    // $JL-EXC$
                }
            }
        }
    }

    static class ReadDataThread extends Thread
    {
        RefinedResultViewer viewer;
        ControlItem ctrl;
        TreeItem item;
        Object data;
        boolean initial;

        public ReadDataThread(RefinedResultViewer viewer, ControlItem ctrl, TreeItem item, Object data, boolean initial)
        {
            this.viewer = viewer;
            this.ctrl = ctrl;
            this.item = item;
            this.data = data;
            this.initial = initial;
        }

        @Override
        public void run()
        {
            List<?> elements = viewer.getElements(data);
            TotalsRow totals = viewer.result.buildTotalsRow(elements);
            totals.setVisibleItems(Math.min(LIMIT, totals.getNumberOfItems()));

            synchronized (ctrl)
            {
                ctrl.children = elements;
                ctrl.totals = totals;
                ctrl.hasBeenPainted = false;
                ctrl.notifyAll();
            }

            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    if (viewer.control.isDisposed())
                        return;

                    synchronized (ctrl)
                    {
                        if (!ctrl.hasBeenPainted)
                        {
                            ctrl.hasBeenPainted = true;
                            viewer.control.getParent().setRedraw(false);
                            try
                            {
                                viewer.doUpdateChildren(item, ctrl);
                            }
                            finally
                            {
                                viewer.control.getParent().setRedraw(true);
                            }
                        }
                    }
                }
            });

            if (!elements.isEmpty())
                calculateTotals(elements);
        }

        private void calculateTotals(final List<?> elements)
        {
            new AbstractPaneJob(Messages.RefinedTreeViewer_CalculateTotals, viewer.pane)
            {
                @Override
                protected IStatus doRun(IProgressMonitor monitor)
                {
                    viewer.result.calculateTotals(elements, ctrl.totals, new VoidProgressListener());

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            if (viewer.control.isDisposed())
                                return;

                            synchronized (ctrl)
                            {
                                viewer.control.getParent().setRedraw(false);
                                try
                                {
                                    if (item.isDisposed())
                                        return;

                                    Item i = viewer.adapter.getItem(item, ctrl.totals.getVisibleItems());
                                    viewer.applyTotals(i, ctrl.totals);
                                }
                                finally
                                {
                                    viewer.control.getParent().setRedraw(true);
                                }
                            }
                        }
                    });

                    return Status.OK_STATUS;
                }

            };

        }
    }

    @Override
    protected void widgetRevealChildren(Item parent, TotalsRow totalsData)
    {
        if (parent == null)
        {
            ControlItem ctrl = (ControlItem) tree.getData(Key.CONTROL);
            int nrItems = tree.getItemCount();

            // dispose "old" totals line
            tree.getItem(nrItems - 1).dispose();

            int currentlyVisible = nrItems - 2;

            int visible = ctrl.totals.getVisibleItems();
            for (int ii = currentlyVisible; ii < visible; ii++)
            {
                TreeItem item = new TreeItem(tree, SWT.NONE, ii + 1);
                doUpdateChild(item, ctrl, ctrl.children.get(ii));
            }

            boolean isTotalsRowVisible = ctrl.totals.isVisible();

            if (isTotalsRowVisible)
                applyTotals(new TreeItem(tree, SWT.NONE, visible + 1), ctrl.totals);
        }
        else
        {
            ControlItem ctrl = (ControlItem) parent.getData(Key.CONTROL);
            TreeItem parentItem = (TreeItem) parent;

            int nrItems = parentItem.getItemCount();

            // dispose "old" totals line
            parentItem.getItem(nrItems - 1).dispose();

            int currentlyVisible = nrItems - 1;

            int visible = ctrl.totals.getVisibleItems();
            for (int ii = currentlyVisible; ii < visible; ii++)
            {
                TreeItem item = new TreeItem(parentItem, SWT.NONE, ii);
                doUpdateChild(item, ctrl, ctrl.children.get(ii));
            }

            boolean isTotalsRowVisible = ctrl.totals.isVisible();

            if (isTotalsRowVisible)
                applyTotals(new TreeItem(parentItem, SWT.NONE, visible), ctrl.totals);
        }
    }

    @Override
    protected List<?> getElements(Object parent)
    {
        return parent == null ? ((IResultTree) result).getElements() : ((IResultTree) result).getChildren(parent);
    }

    @Override
    protected void doCalculateDerivedValuesForAll(ContextProvider provider, DerivedOperation operation)
    {
        super.doCalculateDerivedValuesForAll(provider, operation);

        // trigger jobs for all other expanded tree items

        LinkedList<TreeItem> children = new LinkedList<TreeItem>();

        int count = tree.getItemCount();
        for (int index = 1; index < count; index++)
        {
            TreeItem item = tree.getItem(index);
            ControlItem ctrl = (ControlItem) item.getData(Key.CONTROL);
            if (ctrl != null && ctrl.children != null)
            {
                new DerivedDataJob.OnFullList(this, provider, operation, ctrl.children, item, ctrl).schedule();
                children.add(item);
            }
        }

        while (!children.isEmpty())
        {
            TreeItem child = children.removeFirst();
            count = child.getItemCount();
            for (int index = 0; index < count; index++)
            {
                TreeItem item = child.getItem(index);
                ControlItem ctrl = (ControlItem) item.getData(Key.CONTROL);
                if (ctrl != null && ctrl.children != null)
                {
                    new DerivedDataJob.OnFullList(this, provider, operation, ctrl.children, item, ctrl).schedule();
                    children.add(item);
                }
            }
        }

    }

    @Override
    protected void refresh(boolean expandAndSelect)
    {
        ControlItem ctrl = new ControlItem(expandAndSelect, 0);
        tree.setData(Key.CONTROL, ctrl);

        TreeItem[] items = tree.getItems();
        for (int ii = 0; ii < items.length; ii++)
            items[ii].dispose();

        applyFilterData(new TreeItem(tree, SWT.NONE, 0));
        applyUpdating(new TreeItem(tree, SWT.NONE, 1));

        new RefinedResultViewer.RetrieveChildrenJob(this, ctrl, null, null).schedule();
    }

    @Override
    protected void doUpdateChildren(Item parentItem, ControlItem ctrl)
    {
        if (parentItem == null)
        {
            tree.setData(Key.CONTROL, ctrl);

            TreeItem[] items = tree.getItems();
            for (int ii = 0; ii < items.length; ii++)
                items[ii].dispose();

            applyFilterData(new TreeItem(tree, SWT.NONE, 0));

            int visible = ctrl.totals.getVisibleItems();
            for (int ii = 0; ii < visible; ii++)
            {
                TreeItem item = new TreeItem(tree, SWT.NONE, ii + 1);
                doUpdateChild(item, ctrl, ctrl.children.get(ii));
            }

            boolean isTotalsRowVisible = ctrl.totals.isVisible();

            if (isTotalsRowVisible)
                applyTotals(new TreeItem(tree, SWT.NONE, visible + 1), ctrl.totals);

            if (needsPacking)
            {
                needsPacking = false;
                pack();
            }
        }
        else
        {
            TreeItem parent = (TreeItem) parentItem;
            parent.setData(Key.CONTROL, ctrl);

            TreeItem[] items = parent.getItems();
            for (int ii = 0; ii < items.length; ii++)
                items[ii].dispose();

            int visible = ctrl.totals.getVisibleItems();
            for (int ii = 0; ii < visible; ii++)
            {
                TreeItem item = new TreeItem(parent, SWT.NONE, ii);
                doUpdateChild(item, ctrl, ctrl.children.get(ii));
            }

            boolean isTotalsRowVisible = ctrl.totals.isVisible();

            if (isTotalsRowVisible)
                applyTotals(new TreeItem(parent, SWT.NONE, visible), ctrl.totals);
        }
    }

    private void doUpdateChild(TreeItem item, ControlItem ctrl, Object element)
    {
        applyTextAndImage(item, element);

        if (((RefinedTree) result).hasChildren(element))
        {
            if (ctrl.expandAndSelect && result.isExpanded(element) && ctrl.level < 10)
            {
                item.setData(Key.CONTROL, new ControlItem(true, ctrl.level + 1));
                doExpand(item);
                item.setExpanded(true);
            }
            else
            {
                new TreeItem(item, 0);
            }
        }

        if (ctrl.expandAndSelect && result.isSelected(element))
        {
            TreeItem[] selection = tree.getSelection();
            if (selection.length == 0)
            {
                tree.setSelection(item);
            }
            else
            {
                TreeItem[] newSelection = new TreeItem[selection.length + 1];
                System.arraycopy(selection, 0, newSelection, 0, selection.length);
                newSelection[selection.length] = item;
                tree.setSelection(newSelection);
            }
        }
    }

    public void refresh(List<?> elementPath)
    {
        if (elementPath == null || elementPath.isEmpty())
            return;

        LinkedList<Object> list = new LinkedList<Object>();
        list.addAll(elementPath);

        Object root = list.removeFirst();

        int count = tree.getItemCount();
        for (int ii = 0; ii < count; ii++)
        {
            TreeItem item = tree.getItem(ii);
            if (item.getData() == root)
            {
                doClear(list, item);
                return;
            }
        }

        // if we get here, a new root element has appeared
        refresh(false);
    }

    private void doClear(LinkedList<?> path, TreeItem item)
    {
        PathLoop: while (!path.isEmpty())
        {
            Object current = path.removeFirst();

            int count = item.getItemCount();
            for (int index = 0; index < count; index++)
            {
                TreeItem child = item.getItem(index);
                if (child.getData() == current)
                {
                    if (path.isEmpty())
                    {
                        child.setData(Key.CONTROL, null);
                        doExpand(child);
                        return;
                    }
                    item = child;
                    continue PathLoop;
                }
            }

            break;
        }
    }

    private void pack()
    {
        tree.getParent().setRedraw(false);
        try
        {
            for (Item item : columns)
            {
                TreeColumn column = (TreeColumn) item;
                column.pack();
                if (column.getWidth() > MAX_COLUMN_WIDTH)
                    column.setWidth(MAX_COLUMN_WIDTH);
                if (column.getWidth() < MIN_COLUMN_WIDTH)
                    column.setWidth(MIN_COLUMN_WIDTH);
            }
        }
        finally
        {
            tree.getParent().setRedraw(true);
        }
    }

    public class TreeAdapter implements WidgetAdapter
    {

        public Composite createControl(Composite parent)
        {
            tree = new Tree(parent, SWT.FULL_SELECTION | SWT.MULTI);
            tree.setHeaderVisible(true);
            tree.setLinesVisible(true);

            return tree;
        }

        public ControlEditor createEditor()
        {
            return treeEditor = new TreeEditor(tree);
        }

        public Item getItem(Item item, int index)
        {
            if (item == null)
                return tree.getItem(index);
            else
                return ((TreeItem) item).getItem(index);
        }

        public void setExpanded(Item item, boolean expanded)
        {
            ((TreeItem) item).setExpanded(expanded);
        }

        public void setItemCount(Item item, int count)
        {
            if (item == null)
                tree.setItemCount(count);
            else
                ((TreeItem) item).setItemCount(count);
        }

        public Item getItem(Point pt)
        {
            return tree.getItem(pt);
        }

        public void setEditor(Composite composite, Item item, int columnIndex)
        {
            tree.showItem((TreeItem) item);
            treeEditor.setEditor(composite, (TreeItem) item, columnIndex);
        }

        public Item getParentItem(Item item)
        {
            return ((TreeItem) item).getParentItem();
        }

        public Item[] getSelection()
        {
            return tree.getSelection();
        }

        public Item createColumn(Column queryColumn, int index, SelectionListener listener)
        {
            TreeColumn column = new TreeColumn(tree, queryColumn.getAlign().getSwtCode(), index);
            column.setData(queryColumn);
            column.setText(queryColumn.getLabel());
            column.setMoveable(true);
            column.setWidth(MIN_COLUMN_WIDTH);
            column.addSelectionListener(listener);
            return column;
        }

        public Font getFont()
        {
            return tree.getFont();
        }

        public Item getSortColumn()
        {
            return tree.getSortColumn();
        }

        public int getSortDirection()
        {
            return tree.getSortDirection();
        }

        public void setSortColumn(Item column)
        {
            tree.setSortColumn((TreeColumn) column);
        }

        public void setSortDirection(int direction)
        {
            tree.setSortDirection(direction);
        }

        public int getItemCount(Item item)
        {
            if (item == null)
                return tree.getItemCount();
            else
                return ((TreeItem) item).getItemCount();
        }

        public int indexOf(Item item)
        {
            return tree.indexOf((TreeItem) item);
        }

        public Rectangle getBounds(Item item, int index)
        {
            return ((TreeItem) item).getBounds(index);
        }

        public Rectangle getTextBounds(Widget item, int index)
        {
            return ((TreeItem) item).getBounds(index);
        }

        public Rectangle getImageBounds(Item item, int index)
        {
            return ((TreeItem) item).getImageBounds(index);
        }

        public void apply(Item item, int index, String label, Color color, Font font)
        {
            TreeItem treeItem = (TreeItem) item;
            treeItem.setFont(index, font);
            treeItem.setText(index, label);
            treeItem.setForeground(index, color);
        }

        public void apply(Item item, Font font)
        {
            ((TreeItem) item).setFont(font);
        }

        public void apply(Item item, int index, String label)
        {
            ((TreeItem) item).setText(index, label);
        }

        public int getLineHeightEstimation()
        {
            if (Platform.OS_LINUX.equals(Platform.getOS()))
                return 26;
            if (Platform.OS_MACOSX.equals(Platform.getOS()))
                return 20;
            if (System.getProperty("os.name").indexOf("Vista") >= 0)//$NON-NLS-1$//$NON-NLS-2$
                return 19;
            return 18;
        }
    }
}
