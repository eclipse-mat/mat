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
package org.eclipse.mat.impl.result;

import java.text.DecimalFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.PatternUtil;


public abstract class Filter
{
    /* package */static class Factory
    {
        public static Filter build(ISnapshot snapshot, Column column, FilterChangeListener listener)
        {
            if (column.isNumeric())
            {
                Format formatter = column.getFormatter();

                // check if it is a min retained size column, which needs a
                // special handling
                boolean convertToPositive = column.getData(RetainedSizeCalculator.class) != null;

                boolean isPercentage = formatter instanceof DecimalFormat
                                && ((DecimalFormat) formatter).toPattern().indexOf('%') >= 0;

                return isPercentage ? new PercentageFilter(listener, convertToPositive) : new NumericFilter(listener,
                                convertToPositive);
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

    public static final String[] FILTER_TYPES = new String[] { "<Regex Filter>", "<Numeric Filter>" };

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
        private static final String ERROR_MSG = "Error parsing the filter expression.\n\nUse one of the following:"//
                        + "\nIntervals: 1000..10000  1%..10%" //
                        + "\nUpper Boundary: <=10000 <1%" //
                        + "\nLower Boundary: >1000 >=5%\n\n";

        String criteria;

        Test test;
        boolean convertToPositive;

        public NumericFilter(FilterChangeListener listener, boolean convertToPositive)
        {
            super(listener);
            this.convertToPositive = convertToPositive;
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
            if (convertToPositive && doubleValue < 0)
            {
                doubleValue = -doubleValue;
            }

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
                int indexOfDots = criteria.indexOf("..");
                if (indexOfDots >= 0)
                {
                    Double lowerBound = number(criteria.substring(0, indexOfDots).trim());
                    int lastIndexOfDots = criteria.lastIndexOf("..");
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
                throw new ParseException("Illegal characters: " + string.substring(pos.getIndex()), pos.getIndex());

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

        public PercentageFilter(FilterChangeListener listener, boolean convertToPositive)
        {
            super(listener, convertToPositive);
            this.convertToPositive = convertToPositive;
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
                    throw new IllegalArgumentException("Invalid regular expression:\n\n" + e.getMessage(), e);
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
