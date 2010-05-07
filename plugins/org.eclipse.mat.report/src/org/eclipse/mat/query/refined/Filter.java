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
package org.eclipse.mat.query.refined;

import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

public abstract class Filter
{
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

                return isPercentage ? new PercentageFilter(listener, converter)
                                : new NumericFilter(listener, converter);
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
     * 
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

        public NumericFilter(FilterChangeListener listener, ValueConverter converter)
        {
            super(listener);
            this.converter = converter;
        }

        @Override
        boolean acceptObject()
        {
            return true;
        }

        @Override
        boolean accept(Object value)
        {
            if (value == null)
                return false;

            double doubleValue = ((Number) value).doubleValue();
            if (converter != null)
                doubleValue = converter.convert(doubleValue);

            return test.accept(doubleValue);
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
                    Double lowerBound = number(criteria.substring(0, indexOfDots).trim());
                    int lastIndexOfDots = criteria.lastIndexOf(".."); //$NON-NLS-1$
                    Double upperBound = number(criteria.substring(lastIndexOfDots + 2).trim());

                    if (lowerBound != null && upperBound != null)
                        newTest = new CompositeTest(new LowerEqualBoundary(lowerBound.doubleValue()),
                                        new UpperEqualBoundary(upperBound.doubleValue()));
                    else if (lowerBound != null)
                        newTest = new LowerEqualBoundary(lowerBound.doubleValue());
                    else if (upperBound != null)
                        newTest = new UpperEqualBoundary(upperBound.doubleValue());
                }
                else
                {
                    if (criteria.charAt(0) == '>')
                    {
                        if (criteria.charAt(1) == '=')
                            newTest = new LowerEqualBoundary(number(criteria.substring(2)).doubleValue());
                        else
                            newTest = new LowerBoundary(number(criteria.substring(1)).doubleValue());
                    }
                    else if (criteria.charAt(0) == '<')
                        if (criteria.charAt(1) == '=')
                            newTest = new UpperEqualBoundary(number(criteria.substring(2)).doubleValue());
                        else
                            newTest = new UpperBoundary(number(criteria.substring(1)).doubleValue());
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

            return true;
        }

        protected Double number(String string) throws ParseException
        {
            if (string.length() == 0)
                return null;

            ParsePosition pos = new ParsePosition(0);
            NumberFormat f = DecimalFormat.getInstance();
            Double result = f.parse(string, pos).doubleValue();

            if (pos.getIndex() < string.length())
                throw new ParseException(MessageUtil.format(Messages.Filter_Error_IllegalCharacters, //
                                string.substring(pos.getIndex())), pos.getIndex());

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
                return this.value == value;
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
    }

    private static class PercentageFilter extends NumericFilter
    {

        public PercentageFilter(FilterChangeListener listener, ValueConverter converter)
        {
            super(listener, converter);
        }

        @Override
        protected Double number(String string) throws ParseException
        {
            if (string.length() == 0)
                return null;

            if (string.charAt(string.length() - 1) == '%')
            {
                String substring = string.substring(0, string.length() - 1);
                return super.number(substring) / 100;
            }
            else
            {
                return super.number(string);
            }
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
                return changed;
            }
            else
            {
                try
                {
                    Pattern p = Pattern.compile(PatternUtil.smartFix(criteria));
                    boolean changed = this.pattern == null || !this.pattern.pattern().equals(p.pattern());
                    pattern = p;
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
