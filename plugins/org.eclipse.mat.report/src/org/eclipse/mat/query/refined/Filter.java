/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates and Bytes + filters
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

/**
 * Used to filter values in a result, to avoid displaying rows not matching the filter.
 */
public abstract class Filter
{
    /**
     * A ValueConverter attached to a column modifies the cell before
     * it is tested in a filter or displayed.
     * An example is where approximate retained sizes are stored as negative
     * numbers, but need the positive value for display.
     * See {@link Column#setData(Object, Object)} and see {@link Column#getData(Object)},
     * as used in
     * and {@link org.eclipse.mat.snapshot.query.RetainedSizeDerivedData#columnFor(org.eclipse.mat.query.DerivedColumn, org.eclipse.mat.query.IResult, org.eclipse.mat.query.ContextProvider)}
     */
    public interface ValueConverter
    {
        double convert(double source);
    }

    /* package */static class Factory
    {
        public static Filter build(Column column, FilterChangeListener listener)
        {
            if (column.isNumeric())
            {
                Format formatter = column.getFormatter();

                // value converter are attached to the column, if the value
                // needs some conversion
                ValueConverter converter = (ValueConverter) column.getData(ValueConverter.class);

                boolean isPercentage = formatter instanceof DecimalFormat
                                && ((DecimalFormat) formatter).toPattern().indexOf('%') >= 0;

                return isPercentage ? new PercentageFilter(listener, converter, formatter)
                                : new NumericFilter(listener, converter, formatter);
            }
            else
            {
                return new PatternFilter(listener);
            }
        }

        private Factory()
        {}
    }

    /* package */interface FilterChangeListener
    {
        void filterChanged(Filter filter);
    }

    public static final String[] FILTER_TYPES = new String[] { Messages.Filter_Label_Regex,
                    Messages.Filter_Label_Numeric };

    /**
     * if an exceptions is thrown, the internal state stays untouched. Hence, no
     * re-filtering necessary
     * @param criteria for the filter
     * @return true if the filter criteria changed
     */
    public abstract boolean setCriteria(String criteria) throws IllegalArgumentException;

    public abstract String getCriteria();

    public abstract String getLabel();

    public abstract boolean isActive();

    /** indicates which filter method to use -> avoid conversion to string */
    /* package */abstract boolean acceptObject();

    /* package */abstract boolean accept(Object value);

    /* package */abstract boolean accept(String value);

    // //////////////////////////////////////////////////////////////
    // abstract impl.
    // //////////////////////////////////////////////////////////////

    protected FilterChangeListener listener;

    protected Filter(FilterChangeListener listener)
    {
        this.listener = listener;
    }

    // //////////////////////////////////////////////////////////////
    // internal implementations
    // //////////////////////////////////////////////////////////////

    private static class NumericFilter extends Filter
    {
        private static final String ERROR_MSG = Messages.Filter_Error_Parsing;

        String criteria;

        Test test;
        ValueConverter converter;
        Format format;

        public NumericFilter(FilterChangeListener listener, ValueConverter converter, Format format)
        {
            super(listener);
            this.converter = converter;
            this.format = format;
        }

        @Override
        boolean acceptObject()
        {
            return true;
        }

        @Override
        boolean accept(Object value)
        {
            // Experiment: Let null values be converted here to NaN, which will normally fail the accept() test
            double doubleValue = value instanceof Bytes ? ((Bytes)value).getValue() : value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
            if (converter != null)
                doubleValue = converter.convert(doubleValue);

            Test currentTest = test;
            // If the filter is cleared while filtering it is possible for 'test' to go null
            if (currentTest == null)
                return true;
            return currentTest.accept(doubleValue);
        }

        @Override
        boolean accept(String value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCriteria()
        {
            return criteria;
        }

        @Override
        public boolean setCriteria(String criteria) throws IllegalArgumentException
        {
            if (criteria == null || criteria.trim().length() == 0)
            {
                boolean changed = this.criteria != null;
                this.criteria = null;
                this.test = null;
                if (changed)
                {
                    listener.filterChanged(this);
                }
                return changed;
            }

            if (this.criteria != null && this.criteria.equals(criteria))
                return false;

            Test newTest = null;

            try
            {
                int indexOfDots = criteria.indexOf(".."); //$NON-NLS-1$
                if (indexOfDots >= 0)
                {
                    // U\\ Invert of range (universal set difference from range, excludes NaN)
                    // ! strict invert of range (universal set difference from range, includes NaN)
                    int st = criteria.startsWith("U\\") ? 2 : criteria.startsWith("!") ? 1 : 0; //$NON-NLS-1$ //$NON-NLS-2$
                    Double lowerBound = number(criteria.substring(st, indexOfDots).trim());
                    int lastIndexOfDots = criteria.lastIndexOf(".."); //$NON-NLS-1$
                    Double upperBound = number(criteria.substring(lastIndexOfDots + 2).trim());

                    if (lowerBound != null && upperBound != null)
                        newTest = new CompositeTest(new LowerEqualBoundary(lowerBound.doubleValue()),
                                        new UpperEqualBoundary(upperBound.doubleValue()));
                    else if (lowerBound != null)
                        newTest = new LowerEqualBoundary(lowerBound.doubleValue());
                    else if (upperBound != null)
                        newTest = new UpperEqualBoundary(upperBound.doubleValue());
                    else
                        lowerBound = number(criteria); // cause an error
                    if (criteria.startsWith("!")) //$NON-NLS-1$
                        newTest = new NotTest(newTest);
                    else if (criteria.startsWith("U\\")) //$NON-NLS-1$
                        newTest = new CompositeTest(new NotTest(newTest), new NotEqualsTest(Double.NaN));
                }
                else
                {
                    // Check lengths are enough for the condition and at least one character for the number
                    if (criteria.charAt(0) == '>' && criteria.length() >= 2)
                    {
                        if (criteria.length() >= 3 && criteria.charAt(1) == '=')
                            newTest = new LowerEqualBoundary(number(criteria.substring(2)).doubleValue());
                        else
                            newTest = new LowerBoundary(number(criteria.substring(1)).doubleValue());
                    }
                    else if (criteria.charAt(0) == '<' && criteria.length() >= 2)
                        if (criteria.length() >= 3 && criteria.charAt(1) == '=')
                            newTest = new UpperEqualBoundary(number(criteria.substring(2)).doubleValue());
                        else if (criteria.length() >= 3 && criteria.charAt(1) == '>')
                            newTest = new NotEqualsTest(number(criteria.substring(2)).doubleValue());
                        else
                            newTest = new UpperBoundary(number(criteria.substring(1)).doubleValue());
                    else if (criteria.charAt(0) == '!' && criteria.length() >= 3 && criteria.charAt(1) == '=')
                        newTest = new NotEqualsTest(number(criteria.substring(2)).doubleValue());
                    else
                        newTest = new EqualsTest(number(criteria).doubleValue());
                }
            }
            catch (ParseException e)
            {
                throw new IllegalArgumentException(ERROR_MSG + e.getMessage(), e);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(ERROR_MSG + e.getMessage(), e);
            }

            this.criteria = criteria;
            this.test = newTest;

            listener.filterChanged(this);
            return true;
        }

        protected Double number(String string) throws ParseException
        {
            if (string.length() == 0)
                return null;

            ParsePosition pos = new ParsePosition(0);
            // Try to parse the filter with column formatter
            Object oresult = format.parseObject(string, pos);
            Number nresult;
            if (oresult instanceof Number)
            {
               nresult = (Number)oresult;
            }
            else if (oresult instanceof Bytes)
            {
                nresult = ((Bytes)(oresult)).getValue();
            }
            else
            {
                // Old way - just use a decimal formatter
                pos = new ParsePosition(0);
                NumberFormat f = DecimalFormat.getInstance();
                nresult = f.parse(string, pos);
            }

            if (pos.getIndex() < string.length())
                throw new ParseException(MessageUtil.format(Messages.Filter_Error_IllegalCharacters, //
                                string.substring(pos.getIndex())), pos.getIndex());

            Double result = nresult.doubleValue();
            return result;
        }

        @Override
        public String getLabel()
        {
            return FILTER_TYPES[1];
        }

        @Override
        public boolean isActive()
        {
            return criteria != null;
        }

        // //////////////////////////////////////////////////////////////
        // tests
        // //////////////////////////////////////////////////////////////

        interface Test
        {
            boolean accept(double value);
        }

        static class UpperBoundary implements Test
        {
            double bound;

            UpperBoundary(double bound)
            {
                this.bound = bound;
            }

            public boolean accept(double value)
            {
                return value < bound;
            }
        }

        static class UpperEqualBoundary implements Test
        {
            double bound;

            UpperEqualBoundary(double bound)
            {
                this.bound = bound;
            }

            public boolean accept(double value)
            {
                return value <= bound;
            }
        }

        static class LowerBoundary implements Test
        {
            double bound;

            LowerBoundary(double bound)
            {
                this.bound = bound;
            }

            public boolean accept(double value)
            {
                return value > bound;
            }
        }

        static class LowerEqualBoundary implements Test
        {
            double bound;

            LowerEqualBoundary(double bound)
            {
                this.bound = bound;
            }

            public boolean accept(double value)
            {
                return value >= bound;
            }
        }

        static class EqualsTest implements Test
        {
            double value;

            EqualsTest(double value)
            {
                this.value = value;
            }

            public boolean accept(double value)
            {
                // Not standard, but want =NaN to find NaN
                return this.value == value || Double.isNaN(this.value) && Double.isNaN(value);
            }
        }

        static class NotEqualsTest implements Test
        {
            double value;

            NotEqualsTest(double value)
            {
                this.value = value;
            }

            public boolean accept(double value)
            {
                // Not standard, but want !=NaN not to find NaN
                return this.value != value && !(Double.isNaN(this.value) && Double.isNaN(value));
            }
        }

        static class CompositeTest implements Test
        {
            Test a;
            Test b;

            private CompositeTest(Test a, Test b)
            {
                this.a = a;
                this.b = b;
            }

            public boolean accept(double value)
            {
                return a.accept(value) && b.accept(value);
            }
        }

        static class NotTest implements Test
        {
            Test a;
            private NotTest(Test t)
            {
                a = t;
            }

            public boolean accept(double value)
            {
                return !a.accept(value);
            }
        }
    }

    private static class PercentageFilter extends NumericFilter
    {

        public PercentageFilter(FilterChangeListener listener, ValueConverter converter, Format format)
        {
            super(listener, converter, format);
        }

        @Override
        protected Double number(String string) throws ParseException
        {
            if (string.length() == 0)
                return null;

            ParsePosition pos = new ParsePosition(0);
            // Try to parse the filter with column formatter
            Object oresult = format.parseObject(string, pos);
            Number nresult;
            if (oresult instanceof Number)
            {
               nresult = (Number)oresult;
            }
            else
            {
                // Old way - just use a percent formatter
                pos = new ParsePosition(0);
                NumberFormat f = DecimalFormat.getPercentInstance();
                nresult = f.parse(string, pos);
                if (nresult == null)
                {
                    // Also allow a simple decimal format
                    ParsePosition p2 = new ParsePosition(0);
                    f = DecimalFormat.getNumberInstance();
                    nresult = f.parse(string, p2);
                    // Only report the pos if this was successful
                    /// otherwise report the failure from the percent formatter
                    if (nresult != null)
                    {
                        if (string.charAt(p2.getIndex()) == '%' && nresult instanceof Number)
                        {
                            // Old way with trailing % (no space) 
                            // - some locale formatters just parse "12.34 %"
                            nresult = ((Number)nresult).doubleValue() / 100;
                            p2.setIndex(p2.getIndex() + 1);
                        }
                        pos = p2;
                    }
                    
                }
            }

            if (pos.getIndex() < string.length())
                throw new ParseException(MessageUtil.format(Messages.Filter_Error_IllegalCharacters, //
                                string.substring(pos.getIndex())), pos.getIndex());

            Double result = nresult.doubleValue();
            return result;
        }
    }

    private static class PatternFilter extends Filter
    {
        Pattern pattern;

        public PatternFilter(FilterChangeListener listener)
        {
            super(listener);
        }

        @Override
        boolean acceptObject()
        {
            return false;
        }

        @Override
        boolean accept(Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean accept(String value)
        {
            return value == null ? false : pattern.matcher(value).matches();
        }

        @Override
        public boolean setCriteria(String criteria) throws IllegalArgumentException
        {
            if (criteria == null || criteria.trim().length() == 0)
            {
                boolean changed = this.pattern != null;
                this.pattern = null;
                if (changed)
                {
                    listener.filterChanged(this);
                }
                return changed;
            }
            else
            {
                try
                {
                    Pattern p = Pattern.compile(PatternUtil.smartFix(criteria));
                    boolean changed = this.pattern == null || !this.pattern.pattern().equals(p.pattern());
                    pattern = p;
                    if (changed)
                    {
                        listener.filterChanged(this);
                    }
                    return changed;
                }
                catch (PatternSyntaxException e)
                {
                    throw new IllegalArgumentException(Messages.Filter_Error_InvalidRegex + "\n\n" + e.getMessage(), e); //$NON-NLS-1$
                }
            }
        }

        @Override
        public String getCriteria()
        {
            return pattern != null ? pattern.pattern() : null;
        }

        @Override
        public String getLabel()
        {
            return FILTER_TYPES[0];
        }

        @Override
        public boolean isActive()
        {
            return pattern != null;
        }

    }

}
