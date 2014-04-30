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
package org.eclipse.mat.query.quantize;

/* package */class KeyCalculator
{
    public Object getKey(Object[] columnValues)
    {
        return columnValues[0];
    }

    /* package */static class MultipleKeys extends KeyCalculator
    {
        int noOfGroupedColumns;

        public MultipleKeys(int noOfGroupedColumns)
        {
            this.noOfGroupedColumns = noOfGroupedColumns;
        }

        @Override
        public Object getKey(Object[] columnValues)
        {
            return new KeyCalculator.CompositeKey(columnValues, noOfGroupedColumns);
        }
    }

    /* package */static class LinearDistributionDouble extends KeyCalculator
    {
        double lowerBound;
        double upperBound;
        double step;

        public LinearDistributionDouble(double lowerBound, double upperBound, double step)
        {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.step = step;
        }

        @Override
        public Object getKey(Object[] columnValues)
        {
            // lowerBound < x <= lowerBound + step

            double v = ((Number) columnValues[0]).doubleValue();

            if (v <= lowerBound)
                return lowerBound;
            if (v > upperBound)
                return Double.MAX_VALUE;

            double b = upperBound;

            // Use multiplication rather than repeated subtraction for accuracy
            double ret = b;
            for (int i = 0; v <= b; ++i) {
                ret = b;
                b = upperBound - i * step;
            }
            return ret;
        }

    }

    /* package */static class LinearDistributionLong extends KeyCalculator
    {
        long lowerBound;
        long upperBound;
        long step;

        public LinearDistributionLong(long lowerBound, long upperBound, long step)
        {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.step = step;
        }

        @Override
        public Object getKey(Object[] columnValues)
        {
            long v = ((Number) columnValues[0]).longValue();

            if (v <= lowerBound)
                return lowerBound;
            if (v > upperBound)
                return Long.MAX_VALUE;

            return lowerBound + (step * (((v - 1) / step) + 1));
        }

    }

    /* package */static class CompositeKey implements Comparable<CompositeKey>
    {
        Object[] keys;
        int size;

        public CompositeKey(Object[] columnValues, int noOfGroupedColumns)
        {
            this.keys = columnValues;
            this.size = noOfGroupedColumns;
        }

        @SuppressWarnings("unchecked")
        public int compareTo(CompositeKey o)
        {
            // should never happen as maps contain uniformed keys
            if (this.size != o.size)
                return this.size > o.size ? -1 : 1;

            for (int ii = 0; ii < size; ii++)
            {
                int c = ((Comparable) keys[ii]).compareTo(o.keys[ii]);
                if (c != 0)
                    return c;
            }

            return 0;
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

            CompositeKey other = (CompositeKey) obj;

            if (this.size != other.size)
                return false;

            for (int ii = 0; ii < size; ii++)
            {
                if (!this.keys[ii].equals(other.keys[ii]))
                    return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            final int PRIME = 31;

            // size not relevant for the distribution
            int result = 0;

            for (int ii = 0; ii < size; ii++)
                result = PRIME * result + keys[ii].hashCode();

            return result;
        }
    }

}
