/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.query;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapObjectLong;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

import com.ibm.icu.text.DecimalFormat;

/**
 * Extract retained size information.
 * Used for quantization.
 */
public class RetainedSizeDerivedData extends ContextDerivedData
{
    /** Indicates approximate retained size. Sum of retained sizes of each object. */
    public static final DerivedOperation APPROXIMATE = new DerivedOperation("APPROXIMATE", //$NON-NLS-1$
                    Messages.RetainedSizeDerivedData_Label_Approximate);
    /** Indicates exact retained size. Shallow size of retained set of the objects. */
    public static final DerivedOperation PRECISE = new DerivedOperation("PRECISE", //$NON-NLS-1$
                    Messages.RetainedSizeDerivedData_Label_Precise);

    private static final DerivedColumn COLUMN = new DerivedColumn(Messages.Column_RetainedHeap, APPROXIMATE, PRECISE);

    private ISnapshot snapshot;

    /**
     * Initial constructor.
     * @param snaphot
     */
    public RetainedSizeDerivedData(ISnapshot snaphot)
    {
        this.snapshot = snaphot;
    }

    /**
     * Get the extra column with the retained size data.
     */
    @Override
    public DerivedColumn[] getDerivedColumns()
    {
        return new DerivedColumn[] { COLUMN };
    }

    /**
     * Get the label for the extra column.
     * Based on the column name plus information from the provider as to name of the set of objects.
     */
    @Override
    public String labelFor(DerivedColumn derivedColumn, ContextProvider provider)
    {
        return provider.getLabel() == null ? derivedColumn.getLabel() : derivedColumn.getLabel() + " - " //$NON-NLS-1$
                        + provider.getLabel();
    }

    /**
     * Get a column for the retained size with the right calculator.
     */
    @Override
    public Column columnFor(DerivedColumn derivedColumn, IResult result, ContextProvider provider)
    {
        if (derivedColumn != COLUMN)
            throw new IllegalArgumentException();

        String label = labelFor(derivedColumn, provider);

        DerivedCalculator calculator = null;
        if (result instanceof Histogram)
            calculator = new AllClasses(snapshot, provider, result);
        else if (result instanceof Histogram.ClassLoaderTree)
            calculator = new AllClasses(snapshot, provider, result);
        else if (result instanceof Histogram.PackageTree)
            calculator = new AllClasses(snapshot, provider, result);
        else if (result instanceof Histogram.SuperclassTree)
            calculator = new AllClasses(snapshot, provider, result);
        else
            calculator = new DerivedCalculatorImpl(snapshot, provider);

        Column column = new Column(label, long.class) //  
                        .comparing(new RetainedSizeComparator(calculator))//
                        .formatting(new RetainedSizeFormat()) //
                        .noTotals();

        column.setData(Filter.ValueConverter.class, new Filter.ValueConverter()
        {
            public double convert(double source)
            {
                return Math.abs(source);
            }
        });

        column.setData(DerivedCalculator.class, calculator);
        column.setData(DerivedColumn.class, derivedColumn);

        return column;
    }

    private static class DerivedCalculatorImpl implements DerivedCalculator
    {
        protected final ISnapshot snapshot;
        protected final ContextProvider provider;
        protected final HashMapObjectLong<Object> values;

        /* package */DerivedCalculatorImpl(ISnapshot snaphot, ContextProvider provider)
        {
            this.snapshot = snaphot;
            this.provider = provider;
            this.values = new HashMapObjectLong<Object>();
        }

        public Object lookup(Object row)
        {
            try
            {
                return values.get(row);
            }
            catch (NoSuchElementException e)
            {
                // $JL-EXC$
                return null;
            }
        }

        public void calculate(DerivedOperation operation, Object row, IProgressListener listener)
                        throws SnapshotException
        {
            IContextObject contextObject = provider.getContext(row);

            // nothing to calculate
            if (contextObject == null)
                return;

            try
            {
                long v = values.get(row);
                if (v > 0 || operation == APPROXIMATE)
                    return;
            }
            catch (NoSuchElementException e)
            {
                // $JL-EXC$
            }

            if (contextObject instanceof IContextObjectSet)
            {
                int retainedSet[] = ((IContextObjectSet) contextObject).getObjectIds();

                if (retainedSet != null)
                {
                    if (retainedSet.length == 1 && retainedSet[0] == -1)
                    {
                        String msg = Messages.RetainedSizeDerivedData_ErrorMsg_IllegalContextObject;
                        Logger.getLogger(getClass().getName()).log(
                                        Level.SEVERE,
                                        MessageUtil.format(msg, provider.getClass().getName(),
                                                        row.getClass().getName(), row.toString()));
                        return;
                    }
                    else
                    {
                        long retainedSize = 0;

                        if (retainedSet.length == 1)
                        {
                            retainedSize = snapshot.getRetainedHeapSize(retainedSet[0]);
                        }
                        else
                        {
                            if (operation == APPROXIMATE)
                            {
                                retainedSize = snapshot.getMinRetainedSize(retainedSet, listener);
                                retainedSize = -retainedSize;
                            }
                            else
                            {
                                retainedSet = snapshot.getRetainedSet(retainedSet, listener);
                                retainedSize = snapshot.getHeapSize(retainedSet);
                            }
                        }

                        values.put(row, retainedSize);
                    }
                }

            }
            else
            {
                int objectId = contextObject.getObjectId();
                if (objectId < 0)
                {
                    String msg = Messages.RetainedSizeDerivedData_ErrorMsg_IllegalObjectId;
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                    MessageUtil.format(msg, provider.getClass().getName(), row.toString()));
                }
                else
                {
                    long retainedSize = snapshot.getRetainedHeapSize(contextObject.getObjectId());
                    values.put(row, retainedSize);
                }
            }

        }
    }

    private static class AllClasses extends DerivedCalculatorImpl
    {

        public AllClasses(ISnapshot snaphot, ContextProvider provider, IResult result)
        {
            super(snaphot, provider);

            // fill in pre-calculated values
            if (result instanceof Histogram)
            {
                try
                {
                    VoidProgressListener listener = new VoidProgressListener();
                    Histogram histogram = (Histogram) result;
                    for (ClassHistogramRecord r : histogram.getClassHistogramRecords())
                        r.calculateRetainedSize(snaphot, false, true, listener);
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else if (result instanceof Histogram.ClassLoaderTree)
            {
                try
                {
                    VoidProgressListener listener = new VoidProgressListener();
                    Histogram.ClassLoaderTree classLoaderTree = (Histogram.ClassLoaderTree) result;
                    for (Object element : classLoaderTree.getElements())
                        ((ClassLoaderHistogramRecord) element).calculateRetainedSize(snapshot, false, true, listener);
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else if (result instanceof Histogram.PackageTree)
            {
                try
                {
                    VoidProgressListener listener = new VoidProgressListener();
                    Histogram.PackageTree packageTree = (Histogram.PackageTree) result;

                    LinkedList<Object> nodes = new LinkedList<Object>();
                    nodes.addAll(packageTree.getElements());

                    while (!nodes.isEmpty())
                    {
                        Object child = nodes.removeFirst();
                        if (packageTree.hasChildren(child))
                            nodes.addAll(packageTree.getChildren(child));

                        if (child instanceof ClassHistogramRecord)
                            ((ClassHistogramRecord) child).calculateRetainedSize(snaphot, false, true, listener);
                    }
                }
                catch (SnapshotException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public Object lookup(Object row)
        {
            if (row instanceof ClassHistogramRecord)
            {
                long size = ((ClassHistogramRecord) row).getRetainedHeapSize();
                return size != 0 ? size : null;
            }
            else if (row instanceof ClassLoaderHistogramRecord)
            {
                long size = ((ClassLoaderHistogramRecord) row).getRetainedHeapSize();
                return size != 0 ? size : null;
            }
            else
            {
                return super.lookup(row);
            }
        }

        @Override
        public void calculate(DerivedOperation operation, Object row, IProgressListener listener)
                        throws SnapshotException
        {
            if (row instanceof ClassHistogramRecord)
            {
                ((ClassHistogramRecord) row).calculateRetainedSize(snapshot, true, operation == APPROXIMATE, listener);
            }
            else if (row instanceof ClassLoaderHistogramRecord)
            {
                ((ClassLoaderHistogramRecord) row).calculateRetainedSize(snapshot, true, operation == APPROXIMATE,
                                listener);
            }
            else
            {
                super.calculate(operation, row, listener);
            }
        }
    }

    private static final class RetainedSizeFormat extends Format
    {
        private static final long serialVersionUID = 1L;
        private static final Format formatter = new DecimalFormat("#,##0"); //$NON-NLS-1$

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
        {
            Long v = (Long) obj;

            if (v.longValue() < 0)
            {
                toAppendTo.append(">= "); //$NON-NLS-1$
                formatter.format(-v.longValue(), toAppendTo, pos);
            }
            else
            {
                formatter.format(v, toAppendTo, pos);
            }

            return toAppendTo;
        }

        @Override
        public Object parseObject(String source, ParsePosition pos)
        {
            return null;
        }
    }

    private static final class RetainedSizeComparator implements Comparator<Object>
    {
        private DerivedCalculator calculator;

        public RetainedSizeComparator(DerivedCalculator calculator)
        {
            this.calculator = calculator;
        }

        public int compare(Object o1, Object o2)
        {
            Long retainedSize_o1 = (Long) calculator.lookup(o1);
            Long retainedSize_o2 = (Long) calculator.lookup(o2);

            if (retainedSize_o1 == null)
                return retainedSize_o2 == null ? 0 : -1;
            else if (retainedSize_o2 == null)
                return 1;
            else
            {
                long retained_o1 = retainedSize_o1.longValue();
                long retained_o2 = retainedSize_o2.longValue();

                if (retained_o1 < 0)
                    retained_o1 = -retained_o1;
                if (retained_o2 < 0)
                    retained_o2 = -retained_o2;
                long diff = retained_o1 - retained_o2;
                return ((diff == 0) ? 0 : ((diff > 0) ? +1 : -1));
            }
        }
    }

}
