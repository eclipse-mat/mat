/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

import com.ibm.icu.text.DecimalFormat;

import java.text.Format;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes a column of a {@link IStructuredResult}.
 */
public final class Column
{
    /**
     * Alignment of the column, i.e. left, center, or right-aligned.
     */
    public enum Alignment
    {
        LEFT(1 << 14), CENTER(1 << 24), RIGHT(1 << 17);

        private int swtCode;

        private Alignment(int swtCode)
        {
            this.swtCode = swtCode;
        }

        /**
         * The SWT code of the alignment for convenience purposes.
         * @return the alignment code, CENTER, LEFT, RIGHT
         */
        public int getSwtCode()
        {
            return swtCode;
        }
    }

    /**
     * Sort direction of the column. To sort the values, either the attached
     * formatter is used or the natural sorting order.
     */
    public enum SortDirection
    {
        /**
         * Ascending
         */
        ASC(1 << 7),
        /**
         * Descending
         */
        DESC(1 << 10);

        private int swtCode;

        private SortDirection(int swtCode)
        {
            this.swtCode = swtCode;
        }

        /**
         * The SWT code of the alignment for convenience purposes.
         * @return a code for use with Eclipse SWT
         */
        public int getSwtCode()
        {
            return swtCode;
        }

        /**
         * Get the direction the column is sorted in based on SWT code
         * @param swtCode the code from SWT
         * @return  the sort direction
         */
        public static SortDirection of(int swtCode)
        {
            switch (swtCode)
            {
                case 1 << 7:
                    return ASC;
                case 1 << 10:
                    return DESC;
                default:
                    return null;
            }
        }

        /**
         * Get the default ordering for a column.
         * Currently descending for numeric, ascending for the rest.
         * @param column the column to inspect
         * @return the direction
         */
        public static SortDirection defaultFor(Column column)
        {
            return column.isNumeric() ? DESC : ASC;
        }
    }

    private final String label;
    private final Class<?> type;
    private Alignment align;
    private SortDirection sortDirection;
    private Format formatter;
    private Comparator<?> comparator;
    private IDecorator decorator;

    private boolean calculateTotals = false;

    private Map<Object, Object> data = new HashMap<Object, Object>();

    // //////////////////////////////////////////////////////////////
    // constructors
    // //////////////////////////////////////////////////////////////

    /**
     * Build a column with the given label.
     * @param label the top of the column
     */
    public Column(String label)
    {
        this(label, Object.class);
    }

    /**
     * Build a column with the given label.
     * @param label the top of the column
     * @param type a type of the column, such as float, int, Double
     */
    public Column(String label, Class<?> type)
    {
        this.label = label;
        this.type = type;
        this.align = align(type);
        this.formatter = format(type);

        this.calculateTotals = calculateTotals(type);
    }

    /**
     * Build a column with the given label.
     * @param label the top of the column
     * @param type a type of the column, such as float, int, Double
     * @param align cell alignment - see {@link Column.Alignment} for the choices
     * @param direction sorting direction
     * @param formatter how to display items
     * @param comparator how to sort the items
     */
    public Column(String label, Class<?> type, Alignment align, SortDirection direction, Format formatter,
                    Comparator<?> comparator)
    {
        this(label, type);
        aligning(align);
        sorting(direction);
        formatting(formatter);
        comparing(comparator);
    }

    /**
     * Formatter to format the column values.
     * @param formatter the formatter
     * @return the original column to allow chaining
     */
    public Column formatting(Format formatter)
    {
        this.formatter = formatter;
        return this;
    }

    /**
     * Alignment of the column.
     * @param align the alignment
     * @return the original column to allow chaining
     */
    public Column aligning(Alignment align)
    {
        this.align = align;
        return this;
    }

    /**
     * Comparator to sort the column. The row object will be passed to the
     * comparator!
     * @param comparator the comparator for sorting
     * @return the original column to allow chaining
     */
    public Column comparing(Comparator<?> comparator)
    {
        this.comparator = comparator;
        return this;
    }

    /**
     * Initial sort direction of the column.
     * @param direction the initial direction
     * @return the original column to allow chaining
     */
    public Column sorting(SortDirection direction)
    {
        this.sortDirection = direction;
        return this;
    }

    /**
     * Indicates that no totals are to be calculated for the column even if the
     * column contains numbers.
     * @return the original column to allow chaining
     */
    public Column noTotals()
    {
        calculateTotals = false;
        return this;
    }

    /**
     * Add a decorator to a column
     * @param decorator the decorator to allow a prefix or suffix
     * @return the original column to allow chaining
     */
    public Column decorator(IDecorator decorator)
    {
        this.decorator = decorator;
        return this;
    }

    // //////////////////////////////////////////////////////////////
    // getters
    // //////////////////////////////////////////////////////////////

    public Class<?> getType()
    {
        return type;
    }

    public Alignment getAlign()
    {
        return align;
    }

    public SortDirection getSortDirection()
    {
        return sortDirection;
    }

    public String getLabel()
    {
        return label;
    }

    public Comparator<?> getComparator()
    {
        return comparator;
    }

    public Format getFormatter()
    {
        return formatter;
    }

    public boolean getCalculateTotals()
    {
        return calculateTotals;
    }

    public IDecorator getDecorator()
    {
        return decorator;
    }

    /**
     * Returns true if the columns represents a numeric type, i.e. if it is
     * assignable to number or one of the primitive numeric types.
     * @return true if numeric
     */
    public boolean isNumeric()
    {
        return (type.isPrimitive() && !(type.equals(char.class) || type.equals(boolean.class)))
                        || Number.class.isAssignableFrom(type) || type.equals(Bytes.class);
    }

    public Object setData(Object key, Object value)
    {
        return data.put(key, value);
    }

    public Object getData(Object key)
    {
        return data.get(key);
    }

    @Override
    public int hashCode()
    {
        return label.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Column other = (Column) obj;
        return label.equals(other.label);
    }

    // //////////////////////////////////////////////////////////////
    // static default alignment & formatting info
    // //////////////////////////////////////////////////////////////

    private static final Map<Class<?>, Format> CLASS2FORMAT = new HashMap<Class<?>, Format>();
    private static final Map<Class<?>, Alignment> CLASS2ALIGNMENT = new HashMap<Class<?>, Alignment>();

    static
    {
        CLASS2FORMAT.put(Integer.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(Integer.class, Alignment.RIGHT);

        CLASS2FORMAT.put(int.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(int.class, Alignment.RIGHT);

        CLASS2FORMAT.put(Long.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(Long.class, Alignment.RIGHT);

        CLASS2FORMAT.put(long.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(long.class, Alignment.RIGHT);

        CLASS2FORMAT.put(Double.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(Double.class, Alignment.RIGHT);

        CLASS2FORMAT.put(double.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(double.class, Alignment.RIGHT);

        CLASS2FORMAT.put(Float.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(Float.class, Alignment.RIGHT);

        CLASS2FORMAT.put(float.class, DecimalFormat.getInstance());
        CLASS2ALIGNMENT.put(float.class, Alignment.RIGHT);

        CLASS2FORMAT.put(Bytes.class, BytesFormat.getInstance());
        CLASS2ALIGNMENT.put(Bytes.class, Alignment.RIGHT);
    }

    private static final Format format(Class<?> type)
    {
        return CLASS2FORMAT.get(type);
    }

    private static final Alignment align(Class<?> type)
    {
        Alignment alignment = CLASS2ALIGNMENT.get(type);
        return alignment != null ? alignment : Alignment.LEFT;
    }

    private static boolean calculateTotals(Class<?> type)
    {
        return CLASS2ALIGNMENT.containsKey(type);
    }

}
