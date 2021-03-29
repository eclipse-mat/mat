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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.mat.report.internal.TextEmitter;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public abstract class Copy extends TextEmitter
{
    protected Control control;
    protected Item[] selection;

    public static void copyToClipboard(Control control)
    {
        Item[] selection = (control instanceof Table) ? ((Table) control).getSelection() //
                        : ((Tree) control).getSelection();

        new CopyToClipboard(control, selection).doCopy();
    }

    public static void exportToTxtFile(Control control, String fileName)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(fileName));
            new ExportToFile(control, null, writer).doCopy();
        }
        catch (IOException e)
        {
            throw new RuntimeException(Messages.Copy_ErrorInExport, e);
        }
        finally
        {
            if (writer != null)
                writer.close();
        }
    }

    // //////////////////////////////////////////////////////////////
    // private methods
    // //////////////////////////////////////////////////////////////

    private Copy(Control control, Item[] selection)
    {
        this.control = control;
        this.selection = selection != null && selection.length > 0 ? selection : null;
    }

    @Override
    protected boolean shouldAddNextLine(Object item)
    {
        return item instanceof TreeItem && ((TreeItem) item).getExpanded() && toPrint((TreeItem) item);
    }

    @Override
    protected String getItemValue(Object item, int columnIndex)
    {
        String value = "";//$NON-NLS-1$
        if (item instanceof TableItem)
        {
            value = ((TableItem) item).getText(columnIndex);
        }
        else if ((item instanceof TreeItem) && toPrint((TreeItem) item))
        {
            value = ((TreeItem) item).getText(columnIndex);
        }
        return value;
    }

    @Override
    protected boolean shouldSuppressLineBreak(Object item)
    {
        if (item instanceof TableItem)
        {}
        else if ((item instanceof TreeItem) && toPrint((TreeItem) item))
        {}
        else if ((item instanceof TreeItem) && !toPrint((TreeItem) item))
        { return true; }
        return false;
    }

    @Override
    protected Object[] getItems()
    {
        Object[] items;
        if (selection != null)
            items = selection;
        else if (control instanceof Table)
            items = ((Table) control).getItems();
        else
            // if (copyData.control instanceof Tree)
            items = ((Tree) control).getItems();
        return items;
    }

    @Override
    protected void findColumnOrderAndAlignment(Object[] columns)
    {
        // Find the alignment and order of the columns
        align = new int[columns.length];
        if (control instanceof Table)
        {
            Table table = (Table) control;
            order = table.getColumnOrder();
            for (int i = 0; i < columns.length; ++i)
            {
                align[i] = table.getColumn(i).getAlignment();
            }
        }
        else
        {
            Tree tree = (Tree) control;
            order = tree.getColumnOrder();
            for (int i = 0; i < columns.length; ++i)
            {
                align[i] = tree.getColumn(i).getAlignment();
            }
        }
    }

    @Override
    protected String getDisplayableColumnValueInSimpleStructure(Object item)
    {
        String value = "";//$NON-NLS-1$
        if (item instanceof TableItem)
        {
            value = ((TableItem) item).getText();
        }
        else if ((item instanceof TreeItem) && toPrint((TreeItem) item))
        {
            value = ((TreeItem) item).getText();
        }
        return value;
    }

    @Override
    protected boolean isAlignmentRight(int alignment)
    {
        return alignment == SWT.RIGHT;
    }

    @Override
    protected boolean isAlignmentCenter(int alignment)
    {
        return alignment == SWT.CENTER;
    }

    @Override
    protected Object[] getColumns()
    {
        if (control instanceof Table)
            return ((Table) control).getColumns();
        else
            // Tree
            return ((Tree) control).getColumns();
    }

    @Override
    protected String getColumnName(Object column)
    {
        return ((Item) column).getText();
    }

    @Override
    protected String getDisplayableRowValue(Object item)
    {
        return ((TreeItem) item).getText();
    }

    @Override
    protected String getDisplayableColumnValue(Object item, int index)
    {
        return ((TreeItem) item).getText(index);
    }

    @Override
    protected boolean isExpanded(Object item)
    {
        return ((TreeItem) item).getExpanded();
    }

    @Override
    protected int getColumnLength(Object[] items, Object[] objColumns, int columnNumber)
    {
        int lengthToCompare = 0;
        Item[] columns = (Item[]) objColumns;
        String header = columns[columnNumber].getText();
        int length = header != null ? header.length() : 0;

        for (int i = 0; i < items.length; i++)
        {
            if (items[i] instanceof TableItem)
                lengthToCompare = ((TableItem) items[i]).getText(columnNumber).length();

            else
                lengthToCompare = ((TreeItem) items[i]).getText(columnNumber).length();

            if (lengthToCompare > length)
                length = lengthToCompare;

            if (items[i] instanceof TreeItem && ((TreeItem) items[i]).getExpanded())
            {
                if (columnNumber == order[0])
                    lengthToCompare = compare((TreeItem) items[i], length, columnNumber, new StringBuilder());
                else
                    lengthToCompare = getOtherColumnLength((TreeItem) items[i], length, columnNumber);

            }
            if (lengthToCompare > length)
                length = lengthToCompare;
        }

        return length;
    }

    @Override
    protected Object[] getChildren(Object item)
    {
        return ((TreeItem) item).getItems();
    }

    private int getOtherColumnLength(TreeItem item, int length, int columnNumber)
    {
        int lengthToCompare = 0;
        TreeItem[] children = item.getItems();
        for (int i = 0; i < children.length; i++)
        {
            if (selection != null && skip(children[i]))
                continue;
            String columnText = children[i].getText(columnNumber);
            if (columnText != null)
                lengthToCompare = columnText.length();
            if (lengthToCompare > length)
                length = lengthToCompare;

            if (children[i].getExpanded())
            {
                lengthToCompare = getOtherColumnLength(children[i], length, columnNumber);
                if (lengthToCompare > length)
                    length = lengthToCompare;
            }

        }
        return length;
    }

    private int compare(TreeItem item, int length, int columnNumber, StringBuilder level)
    {
        int lengthToCompare = 0;
        TreeItem[] children = item.getItems();
        for (int i = 0; i < children.length; i++)
        {
            if (selection != null && skip(children[i]))
                continue;
            level = getLevel(level, children.length, i);
            lengthToCompare = children[i].getText(columnNumber).length() + level.length();
            if (lengthToCompare > length)
                length = lengthToCompare;

            if (children[i].getExpanded())
            {
                lengthToCompare = compare(children[i], length, columnNumber, level);
                if (lengthToCompare > length)
                    length = lengthToCompare;
            }
            if (level.length() >= 3)
                level.delete(level.length() - 3, level.length());

        }
        return length;
    }

    private boolean toPrint(TreeItem item)
    {
        if (selection == null)
            return true;
        TreeItem[] selection = (TreeItem[]) this.selection;
        for (TreeItem treeItem : selection)
        {
            if (treeItem.equals(item.getParentItem()))
                return false;
        }
        return true;
    }

    private boolean skip(TreeItem item)
    {
        TreeItem[] selection = (TreeItem[]) this.selection;
        for (TreeItem treeItem : selection)
        {
            if (treeItem.equals(item))
                return false;
        }
        return true;
    }

    @Override
    protected boolean shouldProcessChild(Object child)
    {
        return selection == null || !skip((TreeItem) child);
    }

    private static class ExportToFile extends Copy
    {
        private PrintWriter writer;

        public ExportToFile(Control control, Item[] selection, PrintWriter writer)
        {
            super(control, selection);
            this.writer = writer;
        }

        @Override
        protected void append(String string)
        {
            writer.write(string);
        }

        @Override
        protected void done()
        {
            // flush the writer at the end
            writer.flush();
        }
    }

    private static class CopyToClipboard extends Copy
    {
        private StringBuilder buffer = new StringBuilder(); // a buffer to keep
                                                            // the content for
                                                            // the clipboard

        private CopyToClipboard(Control control, Item[] selection)
        {
            super(control, selection);
        }

        @Override
        protected void append(String string)
        {
            // just append everything to a buffer in memory
            buffer.append(string);
        }

        @Override
        protected void done()
        {
            // done -> just copy the buffer to the clipboard
            copyToClipboard(buffer.toString(), control.getDisplay());
        }
    }

    public static void copyToClipboard(String text, Display display)
    {
        Clipboard clipboard = new Clipboard(display);
        clipboard.setContents(new Object[] { text.toString() }, new Transfer[] { TextTransfer.getInstance() });
        clipboard.dispose();
    }
}
