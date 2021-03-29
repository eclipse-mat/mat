/*******************************************************************************
 * Copyright (c) 2021 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.query.refined.Filter;

/**
 */
public abstract class TextEmitter
{
    protected static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
    protected static final String COLUMN_SEPARATOR = "|"; //$NON-NLS-1$

    private Map<Integer, Integer> columnLengths = new HashMap<Integer, Integer>();
    protected int order[];
    protected int align[];

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

    protected abstract Object[] getItems();

    protected abstract Object[] getColumns();

    protected abstract boolean shouldAddNextLine(Object item);

    protected abstract String getItemValue(Object item, int columnIndex);

    protected abstract boolean shouldSuppressLineBreak(Object item);

    protected abstract void findColumnOrderAndAlignment(Object[] columns);

    protected abstract String getColumnName(Object column);

    protected abstract int getColumnLength(Object[] items, Object[] objColumns, int columnNumber);

    protected abstract boolean shouldProcessChild(Object child);

    protected abstract String getDisplayableRowValue(Object item);

    protected abstract String getDisplayableColumnValue(Object item, int index);

    protected abstract String getDisplayableColumnValueInSimpleStructure(Object item);

    protected abstract boolean isExpanded(Object item);

    protected abstract Object[] getChildren(Object item);

    protected abstract boolean isAlignmentRight(int alignment);

    protected abstract boolean isAlignmentCenter(int alignment);

    public void doCopy()
    {
        Object[] items = getItems();

        Object[] columns = getColumns();

        int numberOfColumns = columns.length;

        if (numberOfColumns == 0)
        {
            copySimpleStructure(items);
        }
        else
        {
            findColumnOrderAndAlignment(columns);

            // find the length of columns by running through all the
            // entries and finding the longest one
            for (int columnIndex = 0; columnIndex < numberOfColumns; columnIndex++)
            {
                int columnLength = getColumnLength(items, columns, columnIndex);

                Integer existingLength = columnLengths.get(columnIndex);
                if (existingLength == null || columnLength > existingLength)
                {
                    columnLengths.put(columnIndex, columnLength);
                }
            }

            // add column names to the result buffer
            for (int i = 0; i < numberOfColumns; i++)
            {
                int col = order[i];
                if (i != 0)
                    append(COLUMN_SEPARATOR);
                append(align(getColumnName(columns[col]), align[col], columnLengths.get(col),
                                i + 1 == numberOfColumns));
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
                    if (shouldSuppressLineBreak(item))
                    {
                        addLineBreak = false;
                        break;
                    }
                    String value = getItemValue(item, columnIndex);
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
                if (shouldAddNextLine(item))
                {
                    addNextLine(new StringBuilder(), item, numberOfColumns, columnLengths.get(order[0]));
                }
            }

            columnLengths.clear();
            append(dashedLine + LINE_SEPARATOR);
        }
        done();

    }

    protected StringBuilder getLevel(StringBuilder level, int length, int counter)
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

    private void addNextLine(StringBuilder level, Object item, int numberOfColumns, int length)
    {
        Object[] children = getChildren(item);
        if (children != null)
        {

            for (int j = 0; j < children.length; j++)
            {
                level = getLevel(level, children.length, j);
                if (shouldProcessChild(children[j]))
                {
                    if (numberOfColumns < 1)
                    {
                        // no append of empty spaces needed
                        append(level.toString());
                        append(getDisplayableRowValue(children[j]));
                    }
                    else
                    {
                        int col = order[0];
                        // add the first column with the tree branches prefix
                        append(level.toString());
                        append(align(getDisplayableColumnValue(children[j], col), align[col], length - level.length(),
                                        numberOfColumns == 1));
                        // add the rest of the columns
                        for (int i = 1; i < numberOfColumns; i++)
                        {
                            col = order[i];
                            append(COLUMN_SEPARATOR);
                            append(align(getDisplayableColumnValue(children[j], col), align[col],
                                            columnLengths.get(col), i + 1 == numberOfColumns));
                        }
                    }
                    append(LINE_SEPARATOR);

                    if (isExpanded(children[j]))
                    {
                        addNextLine(level, children[j], numberOfColumns, length);
                    }

                    if (level.length() >= 3)
                        level.delete(level.length() - 3, level.length());
                }
            }
        }
    }

    protected void copySimpleStructure(Object[] items)
    {
        for (Object item : items)
        {
            boolean addLineBreak = true;

            if (shouldSuppressLineBreak(item))
            {
                addLineBreak = false;
                continue;
            }

            String value = getDisplayableColumnValueInSimpleStructure(item);

            append(value);
            if (addLineBreak)
                append(LINE_SEPARATOR);

            if (shouldAddNextLine(item))
            {
                addNextLine(new StringBuilder(), item, 0, getDisplayableRowValue(item).length());
            }
        }
    }

    /**
     * Align a string in a field. Right aligned fields have a space either side
     * of the longest entry
     * 
     * @param s
     *            the data
     * @param alignment
     *            An integer representing alignment which is seeded by the
     *            subclass as part of
     *            {@link #findColumnOrderAndAlignment(Object[])} and interpreted
     *            using {@link #isAlignmentRight(int)} and
     *            {@link #isAlignmentCenter(int)}
     * @param length
     *            the size of the field
     * @param last
     *            whether this is the last field in a line, so trailing spaces
     *            can be omitted
     * @return
     */
    protected String align(String s, int alignment, int length, boolean last)
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
            if (isAlignmentRight(alignment) && s.length() == length + 1)
                s = s + ' ';
            return s;
        }

        int blanks = length - s.length();
        int left;
        int right;
        if (isAlignmentRight(alignment))
        {
            // For right align have a space either side for readability
            left = blanks + 1;
            right = 1;
        }
        else if (isAlignmentCenter(alignment))
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

    protected String getDashedLine(int numberOfColumns)
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
}
