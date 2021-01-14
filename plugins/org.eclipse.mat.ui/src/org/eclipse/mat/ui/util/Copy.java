/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.query.refined.Filter;
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

public abstract class Copy
{
    private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final String COLUMN_SEPARATOR = "|"; //$NON-NLS-1$
    protected Control control;
    protected Item[] selection;
    private Map<Integer, Integer> columnLengths = new HashMap<Integer, Integer>();
    private int order[];
    private int align[];
    
	/**
	 * Append content to be copied. Implementations should put the content to
	 * their own storage - buffer, file, etc...
	 * 
	 * @param string
	 */
	protected abstract void append(String string);

	/**
	 * The method is called at the end of the copy procedure. It can be used by
	 * implementations to flush content, move to clipboard, etc...
	 */
	protected abstract void done();

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

    protected void doCopy()
    {
        int numberOfColumns = getColumnCount();

        Object[] items;
        if (selection != null)
            items = selection;
        else if (control instanceof Table)
            items = ((Table) control).getItems();
        else
            // if (copyData.control instanceof Tree)
            items = ((Tree) control).getItems();

        Item[] columns = getColumns(control);

        if (numberOfColumns == 0)
        {
            copySimpleStructure(items);
        }
        else
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

            // find the length of columns by running through all the
            // entries and finding the longest one
            for (int columnIndex = 0; columnIndex < numberOfColumns; columnIndex++)
            {
                getColumnLength(items, columns, columnIndex);
            }

            // add column names to the result buffer
            for (int i = 0; i < numberOfColumns; i++)
            {
                int col = order[i];
                if (i != 0)
                    append(COLUMN_SEPARATOR);
                append(align(columns[col].getText(), align[col], columnLengths.get(col), i + 1 == numberOfColumns));
            }
            append(LINE_SEPARATOR);
            
            String dashedLine = getDashedLine(numberOfColumns);
            append(dashedLine + LINE_SEPARATOR);

            for (Object item : items)
            {
                boolean addLineBreak = true;
                for (int i = 0; i < numberOfColumns; i++)
                {
                    int columnIndex = order[i];
                    String value = "";//$NON-NLS-1$
                    if (item instanceof TableItem)
                    {
                        value = ((TableItem) item).getText(columnIndex);
                    }
                    else if ((item instanceof TreeItem) && toPrint((TreeItem) item))
                    {
                        value = ((TreeItem) item).getText(columnIndex);
                    }
                    else if ((item instanceof TreeItem) && !toPrint((TreeItem) item))
                    {
                        addLineBreak = false;
                        break;
                    }
                    for (String filterName : Filter.FILTER_TYPES)
                    {
                        if (value.equals(filterName))
                            value = "";//$NON-NLS-1$
                    }

                    if (i != 0)
                        append(COLUMN_SEPARATOR);
                    append(align(value, align[columnIndex], columnLengths.get(columnIndex), i + 1 == numberOfColumns));

                }
                if (addLineBreak)
                    append(LINE_SEPARATOR);
                if (item instanceof TreeItem && ((TreeItem) item).getExpanded() && toPrint((TreeItem) item))
                {
                    addNextLineToClipboard(new StringBuilder(), (TreeItem) item, numberOfColumns,
                                    columnLengths.get(order[0]));
                }
            }

            columnLengths.clear();
            append(dashedLine + LINE_SEPARATOR);
        }
        done();

    }

    private void copySimpleStructure(Object[] items)
    {
        for (Object item : items)
        {
            boolean addLineBreak = true;

            String value = "";//$NON-NLS-1$
            if (item instanceof TableItem)
            {
                value = ((TableItem) item).getText();
            }
            else if ((item instanceof TreeItem) && toPrint((TreeItem) item))
            {
                value = ((TreeItem) item).getText();
            }
            else if ((item instanceof TreeItem) && !toPrint((TreeItem) item))
            {
                addLineBreak = false;
                continue;
            }

            append(value);
            if (addLineBreak)
                append(LINE_SEPARATOR);

            if (item instanceof TreeItem && ((TreeItem) item).getExpanded() && toPrint((TreeItem) item))
            {
                addNextLineToClipboard(new StringBuilder(), (TreeItem) item, 0, ((TreeItem) item)
                                .getText().length());
            }
        }
    }

    private void addNextLineToClipboard(StringBuilder level, TreeItem item,
                    int numberOfColumns, int length)
    {

        TreeItem[] children = item.getItems();
        for (int j = 0; j < children.length; j++)
        {
            level = getLevel(level, children.length, j);
            if (selection == null || !skip(children[j]))
            {
                if (numberOfColumns < 1)
                {
                    // no append of empty spaces needed
                    append(level.toString());
                    append(children[j].getText());
                }
                else
                {
                    int col = order[0];
                    // add the first column with the tree branches prefix
                    append(level.toString());
                    append(align(children[j].getText(col), align[col], length - level.length(), numberOfColumns == 1));
                    // add the rest of the columns
                    for (int i = 1; i < numberOfColumns; i++)
                    {
                        col = order[i];
                        append(COLUMN_SEPARATOR);
                        append(align(children[j].getText(col), align[col], columnLengths.get(col), i + 1 == numberOfColumns));
                    }
                }
                append(LINE_SEPARATOR);

                if (children[j].getExpanded())
                {
                    addNextLineToClipboard(level, children[j], numberOfColumns, length);
                }

                if (level.length() >= 3)
                    level.delete(level.length() - 3, level.length());
            }
        }
    }

    private Item[] getColumns(Control control)
    {
        if (control instanceof Table)
            return ((Table) control).getColumns();
        else
            // Tree
            return ((Tree) control).getColumns();
    }

    private int getColumnCount()
    {
        if (control instanceof Table)
            return ((Table) control).getColumnCount();
        else if (control instanceof Tree)
            return ((Tree) control).getColumnCount();
        else
            return 0;
    }

    /**
     * Align a string in a field.
     * Right aligned fields have a space either side of the longest entry
     * @param s the data
     * @param alignment SWT.LEFT, SWT.RIGHT
     * @param length the size of the field
     * @param last whether this is the last field in a line, so trailing spaces can be omitted
     * @return
     */
    private static String align(String s, int alignment, int length, boolean last)
    {
        StringBuilder buf = new StringBuilder(length);
        if (s == null)
        {
            s = ""; //$NON-NLS-1$
            if (last)
                return s;
        }
        if (s.length() > length)
        {
            // For right align, we can fit a slightly bigger string
            if (alignment == SWT.RIGHT && s.length() == length + 1)
                s = s + ' ';
            return s;
        }

        int blanks = length - s.length();
        int left;
        int right;
        if (alignment == SWT.RIGHT)
        {
            // For right align have a space either side for readability
            left = blanks + 1;
            right = 1;
        }
        else if (alignment == SWT.CENTER)
        {
            left = blanks / 2;
            right = blanks - left;
        }
        else
        {
            left = 0;
            right = blanks;
        }
        for (int i = 0; i < left; i++)
            buf.append(' ');
        buf.append(s);
        if (!last)
        {
            for (int i = 0; i < right; i++)
                buf.append(' ');
        }
        return buf.toString();
    }

    private String getDashedLine(int numberOfColumns)
    {
    	StringBuilder dashes = new StringBuilder();
        int dashesLength = 0;
        for (int i = 0; i < numberOfColumns; i++)
        {
            int col = order[i];
            // column separator
            if (i != 0)
                dashesLength += COLUMN_SEPARATOR.length();
            dashesLength = dashesLength + align(null, align[col], columnLengths.get(col), false).length();
        }

        // length of all the columns included empty spaces
        for (int i = 0; i < dashesLength; i++)
            dashes.append('-');
        return dashes.toString();
    }

    private int getColumnLength(Object[] items, Item[] columns, int columnNumber)
    {
        int lengthToCompare = 0;
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

        if (!columnLengths.containsKey(columnNumber)
                        || (columnLengths.containsKey(columnNumber) && columnLengths.get(columnNumber) < length))
            columnLengths.put(columnNumber, length);
        return length;
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

    private StringBuilder getLevel(StringBuilder level, int length, int counter)
    {
        int k = level.indexOf("'-"); //$NON-NLS-1$
        if (k != -1)
        {
            level.replace(k, k + 2, "  "); //$NON-NLS-1$
        }
        else
        {
            k = level.indexOf("|-"); //$NON-NLS-1$
            if (k != -1)
            {
                level.replace(k + 1, k + 2, " "); //$NON-NLS-1$
            }
        }
        if (counter == length - 1)
        {
            level.append('\'');
        }
        else
        {
            level.append('|');
        }
        level.append("- "); //$NON-NLS-1$
        return level;
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
    	private StringBuilder buffer = new StringBuilder(); // a buffer to keep the content for the clipboard
    	
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
