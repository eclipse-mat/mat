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

import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Widget;

public class RefinedTableViewer extends RefinedResultViewer
{
    Table table;
    TableEditor tableEditor;

    public RefinedTableViewer(IQueryContext context, QueryResult result, RefinedTable table)
    {
        super(context, result, table);
    }

    @Override
    public void init(Composite parent, MultiPaneEditor editor, AbstractEditorPane pane)
    {
        super.init(new TableAdapter(), parent, editor, pane);
    }

    protected void handleSetDataEvent(Event event)
    {
        TableItem item = (TableItem) event.item;

        int index = table.indexOf(item);
        if (index == 0) // filter row
        {
            applyFilterData(item);
            return;
        }

        ControlItem ctrl = (ControlItem) table.getData(Key.CONTROL);
        if (ctrl == null || ctrl.children == null)
        {
            applyUpdating(item);
            new RefinedResultViewer.RetrieveChildrenJob(this, ctrl, null, null).schedule();
            return;
        }

        if (ctrl.totals.isVisible() && index + 1 == table.getItemCount())
        {
            applyTotals(item, ctrl.totals);

            if (needsPacking)
            {
                needsPacking = false;
                pack();
            }

            return;
        }

        Object element = ctrl.children.get(index - 1);

        applyTextAndImage(item, element);

        // pack if it is the last item (but only after data has been set)
        if (needsPacking && index + 1 == table.getItemCount())
        {
            needsPacking = false;
            pack();
        }
    }

    private void pack()
    {
        table.getParent().setRedraw(false);
        try
        {
            for (Item item : columns)
            {
                TableColumn column = (TableColumn) item;
                column.pack();
                if (column.getWidth() > MAX_COLUMN_WIDTH)
                    column.setWidth(MAX_COLUMN_WIDTH);
                if (column.getWidth() < MIN_COLUMN_WIDTH)
                    column.setWidth(MIN_COLUMN_WIDTH);
            }
        }
        finally
        {
            table.getParent().setRedraw(true);
        }
    }

    @Override
    protected void refresh(boolean expandAndSelect)
    {
        ((RefinedTable) result).refresh();
        control.setData(Key.CONTROL, new ControlItem(expandAndSelect, 0));
        table.setItemCount(2); // filter row + "updating..." line
        table.clearAll();
    }

    protected void doUpdateChildren(Item parentItem, ControlItem ctrl)
    {
        boolean hasChildren = ctrl.totals.getNumberOfItems() > 0 || ctrl.totals.getFilteredItems() > 0;
        if (parentItem == null) // root elements
        {
            if (hasChildren)
            {
                table.setData(Key.CONTROL, ctrl);
                table.setItemCount(ctrl.totals.getVisibleItems() //
                                + 1 // filter row
                                + (ctrl.totals.isVisible() ? 1 : 0)); // totals
            }
            else
            {
                table.setItemCount(1);
            }

            table.clearAll();
        }
    }

    @Override
    protected void widgetRevealChildren(Item na, TotalsRow totalsData)
    {
        table.setItemCount(totalsData.getVisibleItems() + 2);
        table.clearAll();
    }

    @Override
    protected List<?> getElements(Object parent)
    {
        return ((RefinedTable) result).getRows();
    }

    public class TableAdapter implements WidgetAdapter
    {

        public Composite createControl(Composite parent)
        {
            table = new Table(parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.MULTI);
            table.setHeaderVisible(true);
            table.setLinesVisible(true);

            table.addListener(SWT.SetData, new Listener()
            {
                public void handleEvent(Event event)
                {
                    handleSetDataEvent(event);
                }
            });

            return table;
        }

        public ControlEditor createEditor()
        {
            return tableEditor = new TableEditor(table);
        }

        public Item getItem(Item item, int index)
        {
            if (item == null)
                return table.getItem(index);
            else
                return null;
        }

        public void setExpanded(Item item, boolean expanded)
        {}

        public void setItemCount(Item item, int count)
        {
            if (item == null)
                table.setItemCount(count);
        }

        public Item getItem(Point pt)
        {
            return table.getItem(pt);
        }

        public void setEditor(Composite composite, Item item, int columnIndex)
        {
            table.showItem((TableItem) item);
            tableEditor.setEditor(composite, (TableItem) item, columnIndex);
        }

        public Item getParentItem(Item item)
        {
            return null;
        }

        public Item[] getSelection()
        {
            return table.getSelection();
        }

        public Item createColumn(Column queryColumn, int index, SelectionListener listener)
        {
            TableColumn column = new TableColumn(table, queryColumn.getAlign().getSwtCode(), index);
            column.setData(queryColumn);
            column.setText(queryColumn.getLabel());
            column.setMoveable(true);
            column.setWidth(MIN_COLUMN_WIDTH);
            column.addSelectionListener(listener);
            return column;
        }

        public Font getFont()
        {
            return table.getFont();
        }

        public Item getSortColumn()
        {
            return table.getSortColumn();
        }

        public int getSortDirection()
        {
            return table.getSortDirection();
        }

        public void setSortColumn(Item column)
        {
            table.setSortColumn((TableColumn) column);
        }

        public void setSortDirection(int direction)
        {
            table.setSortDirection(direction);
        }

        public int getItemCount(Item item)
        {
            return table.getItemCount();
        }

        public int indexOf(Item item)
        {
            return table.indexOf((TableItem) item);
        }

        public Rectangle getBounds(Item item, int index)
        {
            return ((TableItem) item).getBounds(index);
        }

        public Rectangle getTextBounds(Widget item, int index)
        {
            return ((TableItem) item).getTextBounds(index);
        }

        public Rectangle getImageBounds(Item item, int index)
        {
            return ((TableItem) item).getImageBounds(index);
        }

        public void apply(Item item, int index, String label, Color color, Font font)
        {
            TableItem treeItem = (TableItem) item;
            treeItem.setFont(index, font);
            treeItem.setText(index, label);
            treeItem.setForeground(index, color);
        }

        public void apply(Item item, Font font)
        {
            ((TableItem) item).setFont(font);
        }

        public void apply(Item item, int index, String label)
        {
            ((TableItem) item).setText(index, label);
        }

        public int getLineHeightEstimation()
        {
            if (Platform.OS_LINUX.equals(Platform.getOS()))
                return 26;
            if (Platform.OS_MACOSX.equals(Platform.getOS()))
                return 20;
            if (System.getProperty("os.name").indexOf("Vista") >= 0)
                return 20;
            return 18;
        }
    }
}
