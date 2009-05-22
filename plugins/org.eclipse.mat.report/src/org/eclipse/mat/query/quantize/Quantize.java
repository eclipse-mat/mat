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
package org.eclipse.mat.query.quantize;

import com.ibm.icu.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.quantize.Quantize.Function.Factory;
import org.eclipse.mat.report.internal.Messages;

/**
 * Create a value or frequency distribution out of arbitrary values.
 */
public final class Quantize
{
    private static class BucketImpl
    {
        Object key;
        ArrayInt objectIds;
        Function[] functions;

        BucketImpl(Object key, Function[] functions)
        {
            this.key = key;
            this.objectIds = new ArrayInt();
            this.functions = functions;
        }

        Function[] getFunctions()
        {
            return functions;
        }

        public Object getKey()
        {
            return key;
        }

        public ArrayInt getObjectIds()
        {
            return objectIds;
        }
    }

    /**
     * A function used to aggregate values into one bucket, e.g. adding or
     * averaging numbers. There exists one instance per bucket.
     */
    public interface Function
    {
        /**
         * Interface for a function factory.
         */
        public interface Factory
        {
            /**
             * Builds a new function.
             */
            Function build() throws Exception;

            /**
             * Creates a new column to display the values of a function.
             */
            Column column(String label);
        }

        /**
         * Called when an object is added to the bucket.
         */
        void add(Object value);

        /**
         * Called to retrieve the function value. If it is expensive to
         * calculate, this value must be cached.
         */
        Object getValue();
    }

    /**
     * Function to count values.
     */
    public static final Function.Factory COUNT = new FnFactoryImpl(Count.class, Integer.class, false);
    /**
     * Function to add values as doubles.
     */
    public static final Function.Factory SUM = new FnFactoryImpl(Sum.class, Double.class, false);
    /**
     * Function to add values as longs.
     */
    public static final Function.Factory SUM_LONG = new FnFactoryImpl(SumLong.class, Long.class, false);
    /**
     * Function to find the minimum double value.
     */
    public static final Function.Factory MIN = new FnFactoryImpl(Min.class, Double.class, true);
    /**
     * Function to find the minimum long value.
     */
    public static final Function.Factory MIN_LONG = new FnFactoryImpl(MinLong.class, Long.class, true);
    /**
     * Function to find the maximum double value.
     */
    public static final Function.Factory MAX = new FnFactoryImpl(Max.class, Double.class, true);
    /**
     * Function to find the maximum long value.
     */
    public static final Function.Factory MAX_LONG = new FnFactoryImpl(MaxLong.class, Long.class, true);
    /**
     * Function to find the average value.
     */
    public static final Function.Factory AVERAGE = new FnFactoryImpl(Average.class, Double.class, true);
    /**
     * Function to find the average long value.
     * 
     * @since 0.8
     */
    public static final Function.Factory AVERAGE_LONG = new FnFactoryImpl(AverageLong.class, Double.class, true);

    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    /**
     * Creates a quantize {@link Builder} for a value distribution, i.e. rows
     * are grouped by identical values. Rows can be grouped by one ore more
     * values.
     */
    public static Builder valueDistribution(String... label)
    {
        KeyCalculator key = label.length > 1 ? new KeyCalculator.MultipleKeys(label.length) : new KeyCalculator();
        Builder builder = new Builder(new Quantize(key));
        for (String l : label)
            builder.addKeyColumn(new Column(l));
        return builder;
    }

    /**
     * Creates a quantize {@link Builder} for a value distribution, i.e. rows
     * are grouped by identical values. Rows can be grouped by one ore more
     * values. This constructor uses the given columns and their formatting and
     * alignment to display the results.
     */
    public static Builder valueDistribution(Column... column)
    {
        KeyCalculator key = column.length > 1 ? new KeyCalculator.MultipleKeys(column.length) : new KeyCalculator();
        Builder builder = new Builder(new Quantize(key));
        for (Column c : column)
            builder.addKeyColumn(c);
        return builder;
    }

    /**
     * Creates a quantize {@link Builder} for a linear frequency distribution on
     * double values.
     * <p>
     * Basically, one can answer questions like how many collections have a fill
     * ratio between 0 and 20%, between 20% and 40%, etc.
     * 
     * @param label
     *            Name of the first column
     * @param lowerBound
     *            The lower bound of the distribution
     * @param upperBound
     *            The upper bound of the distribution
     * @param step
     *            The size of the buckets in the distribution
     */
    public static Builder linearFrequencyDistribution(String label, double lowerBound, double upperBound, double step)
    {
        return new Builder(new Quantize(new KeyCalculator.LinearDistributionDouble(lowerBound, upperBound, step)))
                        .addKeyColumn(new Column(label, Double.class).formatting(new DecimalFormat("<= #,##0.00")) //$NON-NLS-1$
                                        .noTotals());
    }

    /**
     * Creates a quantize {@link Builder} for a linear frequency distribution on
     * long values.
     * {@link Quantize#linearFrequencyDistribution(String, double, double, double)}
     */
    public static Builder linearFrequencyDistribution(String label, long lowerBound, long upperBound, long step)
    {
        return new Builder(new Quantize(new KeyCalculator.LinearDistributionLong(lowerBound, upperBound, step)))
                        .addKeyColumn(new Column(label, Long.class).formatting(new DecimalFormat("<= #,##0.00")) //$NON-NLS-1$
                                        .noTotals());
    }

    // //////////////////////////////////////////////////////////////
    // builder
    // //////////////////////////////////////////////////////////////

    /**
     * {@link Quantize} factory
     */
    public static final class Builder
    {
        Quantize quantize;
        ResultMetaData.Builder metaDataBuilder;

        private Builder(Quantize quantize)
        {
            this.quantize = quantize;
            this.metaDataBuilder = new ResultMetaData.Builder();
        }

        Builder addKeyColumn(Column col)
        {
            quantize.columns.add(col);
            quantize.keyLength++;

            return this;
        }

        /**
         * Add a column identified by label and function.
         */
        public Builder column(String label, Function.Factory function)
        {
            return column(label, function, null);
        }

        /**
         * Add a column identified by label and function and sort the result in
         * the given sort direction.
         */
        public Builder column(String label, Factory function, SortDirection sortDirection)
        {
            Column col = function.column(label).sorting(sortDirection);

            quantize.columns.add(col);
            quantize.functions.add(function);

            return this;
        }

        public Builder addDerivedData(ContextDerivedData.DerivedOperation operation)
        {
            metaDataBuilder.addDerivedData(operation);

            return this;
        }

        /**
         * Creates the Quanitze object.
         */
        public Quantize build()
        {
            Quantize answer = quantize;
            quantize = null; // builder must not change quantize

            answer.resultMetaData = metaDataBuilder.build();

            answer.init();
            return answer;
        }

    }

    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    private ResultMetaData resultMetaData;
    private KeyCalculator keyCalculator;
    private int keyLength;
    private Map<Object, BucketImpl> key2bucket;
    private List<Column> columns;
    private List<Function.Factory> functions;

    private Quantize(KeyCalculator keyCalculator)
    {
        this.keyCalculator = keyCalculator;
        this.columns = new ArrayList<Column>();
        this.functions = new ArrayList<Function.Factory>();
    }

    protected void init()
    {
        key2bucket = new HashMap<Object, BucketImpl>();
    }

    /**
     * Add one value to the quantize function representing one heap object.
     * 
     * @param objectId
     *            the heap object represented by this value
     * @param columnValues
     *            the column values
     */
    public void addValue(int objectId, Object... columnValues) throws SnapshotException
    {
        BucketImpl bucket = internalAddValue(columnValues);
        if (objectId >= 0)
            bucket.objectIds.add(objectId);
    }

    /**
     * Add one value to the quantize function representing a set of objects.
     * 
     * @param objectIds
     *            the heap objects represented by this value
     * @param columnValues
     *            the column values
     */
    public void addValue(int[] objectIds, Object... columnValues) throws SnapshotException
    {
        BucketImpl bucket = internalAddValue(columnValues);
        if (objectIds != null)
            bucket.objectIds.addAll(objectIds);
    }

    private BucketImpl internalAddValue(Object[] columnValues) throws SnapshotException
    {
        if (columnValues.length != columns.size())
            throw new UnsupportedOperationException(Messages.Quantize_Error_MismatchArgumentsColumns);

        try
        {
            Object key = keyCalculator.getKey(columnValues);

            BucketImpl bucket = key2bucket.get(key);
            if (bucket == null)
            {
                Function[] fx = new Function[columns.size() - keyLength];
                for (int ii = 0; ii < fx.length; ii++)
                {
                    fx[ii] = functions.get(ii).build();
                }
                bucket = new BucketImpl(key, fx);
                key2bucket.put(key, bucket);
            }

            for (int ii = 0; ii < bucket.functions.length; ii++)
            {
                bucket.functions[ii].add(columnValues[ii + keyLength]);
            }

            return bucket;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }

    }

    /**
     * Returns the {@link IResult} build by the Quantize object.
     */
    public IResult getResult()
    {
        List<BucketImpl> list = new ArrayList<BucketImpl>(key2bucket.values());
        try
        {
            Collections.sort(list, new Comparator<BucketImpl>()
            {
                @SuppressWarnings("unchecked")
                public int compare(BucketImpl o1, BucketImpl o2)
                {
                    Comparable key1 = (Comparable) o1.getKey();
                    Comparable key2 = (Comparable) o2.getKey();

                    if (key1 == null && key2 == null)
                        return 0;
                    else if (key1 == null)
                        return -1;
                    else if (key2 == null)
                        return 1;

                    return key1.compareTo(key2);
                }
            });
        }
        catch (ClassCastException ignore)
        {
            // $JL-EXC$
            // if keys do not implement Comparable, leave list unsorted
        }

        return new QuantizedResult(resultMetaData, list, columns, keyLength);
    }

    /* package */final static class QuantizedResult implements IResultTable
    {
        private ResultMetaData resultMetaData;
        private List<BucketImpl> values;
        private List<Column> columns;
        private int keyLength;

        private QuantizedResult(ResultMetaData resultMetaData, List<BucketImpl> values, List<Column> columns,
                        int keyLength)
        {
            this.resultMetaData = resultMetaData;
            this.values = values;
            this.columns = columns;
            this.keyLength = keyLength;
        }

        public ResultMetaData getResultMetaData()
        {
            return resultMetaData;
        }

        public Column[] getColumns()
        {
            return columns.toArray(new Column[0]);
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            BucketImpl bucket = (BucketImpl) row;

            if (columnIndex >= keyLength)
            {
                return bucket.functions[columnIndex - keyLength].getValue();
            }
            else if (keyLength == 1)
            {
                return bucket.getKey();
            }
            else
            {
                return ((KeyCalculator.CompositeKey) bucket.getKey()).keys[columnIndex];
            }
        }

        public BucketImpl getRow(int rowId)
        {
            return values.get(rowId);
        }

        public int getRowCount()
        {
            return values.size();
        }

        public IContextObject getContext(Object row)
        {
            final BucketImpl bucket = (BucketImpl) row;

            if (bucket.objectIds.size() == 1)
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return bucket.objectIds.get(0);
                    }

                };
            }
            else if (bucket.objectIds.size() > 1)
            {
                return new IContextObjectSet()
                {

                    public int[] getObjectIds()
                    {
                        return bucket.objectIds.toArray();
                    }

                    public String getOQL()
                    {
                        return null;
                    }

                    public int getObjectId()
                    {
                        return -1;
                    }

                };
            }
            else
            {
                return null;
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // default function implementations
    // //////////////////////////////////////////////////////////////

    /* package */static class Count implements Function
    {
        int count;

        public void add(Object object)
        {
            count++;
        }

        public Object getValue()
        {
            return count;
        }
    }

    /* package */static class Sum implements Function
    {
        double sum;

        public void add(Object object)
        {
            if (object != null)
                sum += ((Number) object).doubleValue();
        }

        public Object getValue()
        {
            return sum;
        }
    }

    /* package */static class SumLong implements Function
    {
        long sum;

        public void add(Object object)
        {
            if (object != null)
                sum += ((Number) object).longValue();
        }

        public Object getValue()
        {
            return sum;
        }
    }

    /* package */static class Min implements Function
    {
        boolean hasValue = false;
        double min;

        public void add(Object object)
        {
            if (object == null)
                return;

            if (hasValue)
            {
                min = Math.min(min, ((Number) object).doubleValue());
            }
            else
            {
                min = ((Number) object).doubleValue();
                hasValue = true;
            }
        }

        public Object getValue()
        {
            return min;
        }
    }

    /* package */static class MinLong implements Function
    {
        boolean hasValue = false;
        long min;

        public void add(Object object)
        {
            if (object == null)
                return;

            if (hasValue)
            {
                min = Math.min(min, ((Number) object).longValue());
            }
            else
            {
                min = ((Number) object).longValue();
                hasValue = true;
            }
        }

        public Object getValue()
        {
            return min;
        }
    }

    /* package */static class Max implements Function
    {
        boolean hasValue = false;
        double max;

        public void add(Object object)
        {
            if (object == null)
                return;

            if (hasValue)
            {
                max = Math.max(max, ((Number) object).doubleValue());
            }
            else
            {
                max = ((Number) object).doubleValue();
                hasValue = true;
            }
        }

        public Object getValue()
        {
            return max;
        }
    }

    /* package */static class MaxLong implements Function
    {
        boolean hasValue = false;
        long max;

        public void add(Object object)
        {
            if (object == null)
                return;

            if (hasValue)
            {
                max = Math.max(max, ((Number) object).longValue());
            }
            else
            {
                max = ((Number) object).longValue();
                hasValue = true;
            }
        }

        public Object getValue()
        {
            return max;
        }
    }

    /* package */static class Average implements Function
    {
        int count;
        double sum;

        public void add(Object object)
        {
            if (object == null)
                return;

            sum += ((Number) object).doubleValue();
            count++;
        }

        public Object getValue()
        {
            return count > 0 ? sum / count : 0;
        }
    }

    /* package */static class AverageLong implements Function
    {
        int count;
        long sum;

        public void add(Object object)
        {
            if (object == null)
                return;

            sum += ((Number) object).longValue();
            count++;
        }

        public Object getValue()
        {
            return count > 0 ? sum / count : 0L;
        }
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private static class FnFactoryImpl implements Function.Factory
    {
        Class<? extends Function> functionClass;
        Class<?> type;
        boolean noTotals;

        public FnFactoryImpl(Class<? extends Function> functionClass, Class<?> type, boolean noTotals)
        {
            this.functionClass = functionClass;
            this.type = type;
            this.noTotals = noTotals;
        }

        public Column column(String label)
        {
            Column column = new Column(label, type);
            return noTotals ? column.noTotals() : column;
        }

        public Function build() throws Exception
        {
            return functionClass.newInstance();
        }

    }
}
