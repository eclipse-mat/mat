/*******************************************************************************
 * Copyright (c) 2011, 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;


import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.osgi.framework.Bundle;

import com.ibm.icu.text.NumberFormat;

public class QueriesTest
{
    private static final String OQL_MIXED_RESULT = "oql \"SELECT s.value FROM java.lang.Integer s" +
                    " UNION (SELECT s.value FROM java.lang.Character s )" + // No Characters in snapshot so use another below
                    " UNION (SELECT s.slash FROM java.io.WinNTFileSystem s )" +
                    " UNION (SELECT s.value FROM java.lang.Boolean s )" +
                    " UNION (SELECT s.value FROM java.lang.String s )" +
                    " UNION (SELECT s.value FROM java.lang.Long s )";
    ISnapshot snapshot;

    @Before
    public void setUp() throws Exception
    {
        snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
    }

    @After
    public void tearDown() throws Exception
    {}

    @Rule
    public ErrorCollector collector = new ErrorCollector();
    static class CheckedProgressListener extends VoidProgressListener
    {
        ErrorCollector collector = new ErrorCollector();
        public CheckedProgressListener(ErrorCollector collector)
        {
            this.collector = collector;
        }
        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {
            if (exception != null && severity != Severity.INFO)
                collector.addError(exception);
            collector.checkThat(message, severity, lessThan(Severity.WARNING));
        }
    }; 

    static class CheckedWorkProgressListener extends CheckedProgressListener
    {
        int total;
        int work;
        public CheckedWorkProgressListener(ErrorCollector collector)
        {
            super(collector);
        }
        @Override
        public void beginTask(String s, int total)
        {
            collector.checkThat("Total work should be non-negative", total, greaterThanOrEqualTo(0));
            this.total = total;
        }
        @Override
        public void worked(int w)
        {
            collector.checkThat("Work should be non-negative", w, greaterThanOrEqualTo(0));
            work += w;
        }
    }

    /**
     * Test for grouping dominator tree by class loader
     * Enable test when fix is available
     * @throws SnapshotException
     */
    @Test
    public void testDominatorByLoader() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree -groupby BY_CLASSLOADER", snapshot);
        IResultTree t = (IResultTree) query.execute(new CheckedProgressListener(collector));
        // class loaders
        for (Object o : t.getElements())
        {
            IContextObject co = t.getContext(o);
            // classes
            for (Object o1 : t.getChildren(o))
            {
                // objects
                for (Object o2 : t.getChildren(o1))
                {
                    IContextObject co2 = t.getContext(o2);
                    IObject obj = snapshot.getObject(co2.getObjectId());
                    int loaderId;
                    if (obj instanceof IClass)
                        loaderId = ((IClass) obj).getClassLoaderId();
                    else if (obj instanceof IClassLoader)
                        loaderId = ((IClassLoader) obj).getObjectId();
                    else
                        loaderId = snapshot.getClassOf(co2.getObjectId()).getClassLoaderId();
                    assertEquals(obj.toString(), co.getObjectId(), loaderId);
                }
            }
        }
    }
    
    /**
     * Test for formatting of incompatible items
     * @throws SnapshotException
     */
    @Test
    public void testOQLFormatting() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        RefinedTable table = (RefinedTable) builder.build();
        
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            // Check non-numeric item can be formatted in some fashion
            String val2 = table.getFormattedColumnValue(row, 0);
            assertNotNull(val2);
        }
    }

    /**
     * Test for filtering of incompatible items
     * @throws SnapshotException
     */
    @Test
    public void testOQLFiltering() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, ">-9E99");
        RefinedTable table = (RefinedTable) builder.build();
        
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 0);
            // Check no non-numeric item
            assertTrue(val instanceof Number);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering2() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, ">");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering3() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, ">=");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering4() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "<=");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering5() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "<");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering6() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "<>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering7() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "!=");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering8() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "..");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOQLFiltering9() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setFilter(0, "!");
    }

    /**
     * Test for filtering of sizes of items
     * @throws SnapshotException
     */
    @Test
    public void testFiltering10() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        builder.setFilter(2, ">=0");
        RefinedTable table = (RefinedTable) builder.build();

        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            assertTrue("Type: "+val.getClass(), val instanceof Number || val instanceof Bytes);
            ++found;
        }
        assertEquals("All entries should have a size >= 0", table.getRowCount(), found);
    }

    /**
     * Test for filtering of incompatible items
     * @throws SnapshotException
     */
    @Test
    public void testFiltering11ge() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, ">="+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz - 1;
            assertThat(v, greaterThanOrEqualTo(sz));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11gt() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, ">"+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz - 1;
            assertThat(v, greaterThan(sz));
            ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11lt() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "<"+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz + 1;
            assertThat(v, lessThan(sz));
            ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11le() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "<="+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz + 1;
            assertThat(v, lessThanOrEqualTo(sz));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11eq() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, ""+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz + 1;
            assertThat(v, equalTo(sz));
            ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11ne() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "!="+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz;
            assertThat(v, not(equalTo(sz)));
            ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11ne2() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "<>"+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz;
            assertThat(v, not(equalTo(sz)));
            ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11rangea() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, sz+"..");
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz - 1;
            assertThat(v, greaterThanOrEqualTo(sz));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11rangeb() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, ".."+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz + 1;
            assertThat(v, lessThanOrEqualTo(sz));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11rangeab() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sza = 32;
        long szb = 256;
        builder.setFilter(2, sza+".."+szb);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eqa = 0;
        int eqb = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sza - 1;
            assertThat(v, greaterThanOrEqualTo(sza));
            assertThat(v, lessThanOrEqualTo(szb));
            ++found;
            if (v == sza)
                ++eqa;
            if (v == szb)
                ++eqb;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eqa, greaterThanOrEqualTo(1));
        assertThat(eqb, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11urangea() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "U\\"+sz+"..");
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz + 1;
            assertThat(v, not(greaterThanOrEqualTo(sz)));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, equalTo(0));
    }

    @Test
    public void testFiltering11urangeb() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sz = 32;
        builder.setFilter(2, "U\\.."+sz);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eq = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sz - 1;
            assertThat(v, not(lessThanOrEqualTo(sz)));
            ++found;
            if (v == sz)
                ++eq;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eq, equalTo(0));
    }


    @Test
    public void testFiltering11urangeab() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sza = 32;
        long szb = 256;
        builder.setFilter(2, "U\\"+sza+".."+szb);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eqa = 0;
        int eqb = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 2);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sza + 1;
            assertFalse(val instanceof Double && Double.isNaN((Double)val));
            assertFalse(val instanceof Float && Double.isNaN((Float)val));
            assertThat(v, not(allOf(greaterThanOrEqualTo(sza), lessThanOrEqualTo(szb))));
            ++found;
            if (v == sza)
                ++eqa;
            if (v == szb)
                ++eqb;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eqa, equalTo(0));
        assertThat(eqb, equalTo(0));
    }

    @Test
    public void testOQLFiltering11urangeabnan() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sza = 32;
        long szb = 256;
        builder.setFilter(0, "U\\"+sza+".."+szb);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eqa = 0;
        int eqb = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 0);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
                v = sza + 1;
            assertFalse(val instanceof Double && Double.isNaN((Double)val));
            assertFalse(val instanceof Float && Double.isNaN((Float)val));
            assertThat(v, not(allOf(greaterThanOrEqualTo(sza), lessThanOrEqualTo(szb))));
            ++found;
            if (v == sza)
                ++eqa;
            if (v == szb)
                ++eqb;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eqa, equalTo(0));
        assertThat(eqb, equalTo(0));
    }

    @Test
    public void testOQLFiltering11notrangeabnan() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable
        long sza = 32;
        long szb = 256;
        builder.setFilter(0, "!"+sza+".."+szb);
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        int eqa = 0;
        int eqb = 0;
        int nan = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 0);
            // Check no non-numeric item
            long v;
            if (val instanceof Number)
                v = ((Number)val).longValue();
            else if (val instanceof Bytes)
                v = ((Bytes)val).getValue();
            else
            {
                v = sza - 1; // unconverted, so make outside the range
                ++nan;
            }

            assertThat(v, not(allOf(greaterThanOrEqualTo(sza), lessThanOrEqualTo(szb))));
            ++found;
            if (v == sza)
                ++eqa;
            if (v == szb)
                ++eqb;
        }
        assertThat(found, greaterThanOrEqualTo(1));
        assertThat(eqa, equalTo(0));
        assertThat(eqb, equalTo(0));
        assertThat(nan, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering11regex() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check size is filterable

        builder.setFilter(0, "java.lang");
        RefinedTable table = (RefinedTable) builder.build();
        int found = 0;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 0);
            assertThat(val.toString(), containsString("java.lang"));
             ++found;
        }
        assertThat(found, greaterThanOrEqualTo(1));
    }

    @Test
    public void testFiltering12percent() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        // Use default locale
        double comp = 0.03;
        String num = NumberFormat.getPercentInstance().format(comp);
        builder.setFilter(3, ">=" + num);
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, greaterThanOrEqualTo(0.03));
        }
        assertThat(found, greaterThan(0));
    }

    @Test
    public void testFiltering12percentNumPercent() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        // Use default locale
        double comp = 0.03;
        String num = NumberFormat.getNumberInstance().format(comp * 100);
        builder.setFilter(3, ">=" + num + "%");
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, greaterThanOrEqualTo(comp));
        }
        assertThat(found, greaterThan(0));
    }

    @Test
    public void testFiltering12percentFr() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        builder.getColumns().get(3).formatting(NumberFormat.getPercentInstance(Locale.FRENCH));
        double comp = 0.03;
        String p3_0 = builder.getColumns().get(3).getFormatter().format(comp);
        builder.setFilter(3, ">=" + p3_0);
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, greaterThanOrEqualTo(comp));
        }
        assertThat(found, greaterThan(0));
    }

    @Test
    public void testFiltering12percentAsNum() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        // Allow for current locale by using formatting
        double comp = 0.03;
        String num = NumberFormat.getNumberInstance().format(comp);
        builder.setFilter(3, "<" + num);
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, lessThan(comp));
        }
        assertThat(found, greaterThan(0));
    }


    @Test
    public void testFiltering12percentAsNumNoFr() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        // Ignore this formatting locale as don't use percent sign in filter
        builder.getColumns().get(3).formatting(NumberFormat.getPercentInstance(Locale.FRENCH));
        // Allow for current locale by using formatting
        double comp = 0.03;
        String num = NumberFormat.getNumberInstance().format(comp);
        builder.setFilter(3, "<" + num);
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, lessThan(comp));
        }
        assertThat(found, greaterThan(0));
    }

    @Test
    public void testFiltering12percentAsNumFr() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree", snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        // Check percentage is filterable
        builder.getColumns().get(3).formatting(NumberFormat.getPercentInstance(Locale.FRENCH));
        // Allow for current locale by using formatting, rather than French for the percentage
        double comp = 0.03;
        String num = NumberFormat.getNumberInstance().format(comp);
        builder.setFilter(3, "<" + num);
        RefinedTree tree = (RefinedTree) builder.build();
        int found = 0;
        for (Object row : tree.getElements())
        {
            Object val = tree.getColumnValue(row, 3);
            ++found;
            assertThat((Double)val, lessThan(comp));
        }
        assertThat(found, greaterThan(0));
    }

    /**
     * Test for sorting of incompatible items
     * @throws SnapshotException
     */
    @Test
    public void testOQLSorting() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new CheckedProgressListener(collector));
        builder.setSortOrder(0, Column.SortDirection.ASC);
        RefinedTable table = (RefinedTable) builder.build();
        
        Object prev = null;
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            Object row = table.getRow(i);
            Object val = table.getColumnValue(row, 0);
            // Check non-numeric item can be formatted in some fashion
            String val2 = table.getFormattedColumnValue(row, 0);
            assertNotNull(val2);
            // nulls first
            if (prev == null)
            {}
            else
            {
                assertNotNull(val);
                // then Comparables
                if (prev instanceof Comparable)
                {
                    try
                    {
                        // Sorted by compareTo
                        int compare = ((Comparable)prev).compareTo(val);
                        assertTrue(compare <= 0);
                    }
                    catch (ClassCastException e)
                    {
                        // try Number Comparables
                        if (prev instanceof Number)
                        {
                            if (val instanceof Number)
                            {
                                // Numbers in order
                                int compare = Double.compare(((Number) prev).doubleValue(), ((Number) val).doubleValue());
                                assertTrue(compare <= 0);
                            }
                            else
                            {
                                // okay - Numbers before non-Numbers
                            }
                        }
                        else
                        {
                            assertFalse(val instanceof Number);

                            // or by type name
                            int compare = prev.getClass().getName().compareTo(val.getClass().getName());
                            assertTrue(compare <= 0);
                        }
                    }
                }
                else
                {
                    // sorted by string
                    assertFalse(val instanceof Comparable);
                    int compare = String.valueOf(prev).compareTo(String.valueOf(val));
                    assertTrue(compare <= 0);
                }
            }
            prev = val;
        }
    }
    
    /**
     * Test parsing of multiple arguments
     * @throws SnapshotException
     */
    @Test
    public void testCustomRetainedSet() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("customized_retained_set -x java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent; 0x2ca48ee8", snapshot);
        Histogram t = (Histogram)query.execute(new CheckedProgressListener(collector));
        assertTrue(t != null);
        assertEquals(17, t.getRowCount());
    }

    /**
     * Test find Strings
     * @throws SnapshotException
     */
    @Test
    public void testFindStrings() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("find_strings java.lang.String -pattern p0", snapshot);
        CheckedWorkProgressListener listener = new CheckedWorkProgressListener(collector);
        IResultTree t = (IResultTree)query.execute(listener);
        assertTrue(t != null);
        assertThat(t.getElements().size(), equalTo(28));
        assertThat("Total work should match", listener.work, equalTo(listener.total));
        assertThat("Should be some work done", listener.work, greaterThan(0));
    }

    /**
     * Test find Strings
     * @throws SnapshotException
     */
    @Test
    public void testFindStrings2() throws SnapshotException
    {
        // 3 object arguments
        SnapshotQuery query = SnapshotQuery.parse("find_strings select * from java.lang.String; java.lang.String select * from java.lang.String; -pattern p0", snapshot);
        CheckedWorkProgressListener listener = new CheckedWorkProgressListener(collector);
        IResultTree t = (IResultTree)query.execute(listener);
        assertTrue(t != null);
        assertThat(t.getElements().size(), equalTo(28 * 3));
        assertThat("Total work should match", listener.work, equalTo(listener.total));
        assertThat("Should be some work done", listener.work, greaterThan(0));
        // As we have an OQL query, the total work is unknown and so set to this
        assertThat("Total work should match", listener.work, equalTo(1000000));
    }

    /**
     * Test parsing of class pattern argument
     * @throws SnapshotException
     */
    @Test
    public void testHeapArguments1() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("histogram .*", snapshot);
        Histogram t = (Histogram)query.execute(new CheckedProgressListener(collector));
        long objs = 0;
        for (ClassHistogramRecord chr : t.getClassHistogramRecords())
        {
            objs += chr.getNumberOfObjects();
        }
        assertEquals("Number of objects", objs, snapshot.getSnapshotInfo().getNumberOfObjects());
    }

    /**
     * Test parsing of OQL class pattern argument
     * @throws SnapshotException
     */
    @Test
    public void testHeapArguments2() throws SnapshotException
    {
        // Escape the quotes in the command line to pass them to OQL
        SnapshotQuery query = SnapshotQuery.parse("histogram select * from \\\".*\\\"", snapshot);
        Histogram t = (Histogram)query.execute(new CheckedProgressListener(collector));
        long objs = 0;
        for (ClassHistogramRecord chr : t.getClassHistogramRecords())
        {
            objs += chr.getNumberOfObjects();
        }
        assertEquals("Number of objects", objs, snapshot.getSnapshotInfo().getNumberOfObjects());
    }

    /**
     * Test running a report defined in a plugin, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testExportHprofReport() throws SnapshotException, IOException
    {
        File samp = new File(snapshot.getSnapshotInfo().getPrefix());
        File tmpdir = TestSnapshots.createGeneratedName(samp.getName(), null);
        File fn = new File(tmpdir, samp.getName() + ".hprof");
        try
        {
            SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.hprof:export -params output="+fn.getPath(), snapshot);
            IResult t = query.execute(new CheckedProgressListener(collector));
            assertNotNull(t);
            ISnapshot newSnapshot = SnapshotFactory.openSnapshot(fn, Collections.<String,String>emptyMap(), new CheckedProgressListener(collector));
            assertNotNull(newSnapshot);
            SnapshotFactory.dispose(newSnapshot);
            assertThat(fn.toString(), fn.delete(), equalTo(true));
        }
        finally
        {
            // Rest of the directory will be deleted later
            if (fn.exists() && !fn.delete())
                System.err.println("unable to delete " + fn);
        }
    }

    /**
     * Test running an external report from a report file, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testExportHprofReportFile() throws SnapshotException, IOException
    {
        // Create a temporary report file
        File rep = File.createTempFile("exporthprof", ".xml"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            // We happen to know where the exporthprof report lives, so copy it
            Bundle bundle = Platform.getBundle("org.eclipse.mat.hprof"); //$NON-NLS-1$
            URL url = bundle.getResource("reports/exporthprof.xml"); //$NON-NLS-1$
            assertNotNull(url);
            InputStream is = url.openStream();
            OutputStream os = new FileOutputStream(rep);
            try
            {
                try
                {
                    int r;
                    while ((r = is.read()) != -1)
                    {
                        os.write(r);
                    }
                }
                finally
                {
                    os.close();
                }
            }
            finally
            {
                is.close();
            }

            // Run the report
            File samp = new File(snapshot.getSnapshotInfo().getPrefix());
            File tmpdir = TestSnapshots.createGeneratedName(samp.getName(), null);
            File fn = new File(tmpdir, samp.getName() + ".hprof");
            try
            {
                SnapshotQuery query = SnapshotQuery.parse(
                                "create_report "+rep.getPath()+" -params output=" + fn.getPath(), snapshot);
                IResult t = query.execute(new CheckedProgressListener(collector));
                assertNotNull(t);
                ISnapshot newSnapshot = SnapshotFactory.openSnapshot(fn, Collections.<String, String> emptyMap(),
                                new CheckedProgressListener(collector));
                assertNotNull(newSnapshot);
                SnapshotFactory.dispose(newSnapshot);
                assertThat(fn.toString(), fn.delete(), equalTo(true));
            }
            finally
            {
                // Rest of the directory will be deleted later
                if (fn.exists() && !fn.delete())
                    System.err.println("unable to delete " + fn);
            }
            assertThat(rep.toString(), rep.delete(), equalTo(true));
        }
        finally
        {
            if (rep.exists() && !rep.delete())
                System.err.println("unable to delete " + rep);
        }
    }

    /**
     * Test running the compare snapshots query, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testCompareQuery() throws SnapshotException, IOException
    {
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false);
        SnapshotQuery query = SnapshotQuery.parse("delta_histogram -snapshot2 "+snapshot2.getSnapshotInfo().getPath(), snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        int intplus = 0;
        int intminus = 0;
        if (t instanceof IResultTable)
        {
            IResultTable tr = (IResultTable)t;
            int rc = tr.getRowCount();
            for (int i = 0; i < rc; ++i)
            {
                Object rw = tr.getRow(i);
                // columns are String, Long, Bytes
                Object v = tr.getColumnValue(rw, 1);
                if (v instanceof Integer)
                {
                    int in = (Integer)v;
                    if (in < 0)
                        ++intminus;
                    else if (in > 0)
                        ++intplus;
                }
                else if (v instanceof Long)
                {
                    long ln = (Long)v;
                    if (ln < 0)
                        ++intminus;
                    else if (ln > 0)
                        ++intplus;
                }
            }
            if (snapshot.getSnapshotInfo().getNumberOfObjects() == snapshot2.getSnapshotInfo().getNumberOfObjects())
            {
                // Check we have no pluses and minuses
                assertThat(intplus, equalTo(0));
                assertThat(intminus, equalTo(0));
            }
            else
            {
                // Check we have some pluses and minuses
                assertThat(intplus, greaterThan(0));
                assertThat(intminus, greaterThan(0));
            }
        }
    }

    /**
     * Test running the compare snapshots report defined in a plugin, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testCompareReport() throws SnapshotException, IOException
    {
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false); // Do not dispose this as shared
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:compare -params snapshot2="+snapshot2.getSnapshotInfo().getPath(), snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        if (t instanceof DisplayFileResult)
        {
            // If an error occurred the file might be short
            assertThat(((DisplayFileResult) t).getFile().length(), greaterThan(2000L));
        }
    }

    /**
     * Test running the overview compare snapshots report defined in a plugin, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testOverview2Report() throws SnapshotException, IOException
    {
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false); // Do not dispose this as shared
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:overview2 -params baseline="+snapshot.getSnapshotInfo().getPath(), snapshot2);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        if (t instanceof DisplayFileResult)
        {
            // If an error occurred the file might be short
            assertThat(((DisplayFileResult) t).getFile().length(), greaterThan(2000L));
        }
    }

    /**
     * Test running the snapshot compare snapshots report defined in a plugin, with parameters.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testSuspects2Report() throws SnapshotException, IOException
    {
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false); // Do not dispose this as shared
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:suspects2 -params baseline="+snapshot.getSnapshotInfo().getPath(), snapshot2);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
    }

    /**
     * Comparison of two JDK6 dumps
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testLeakHunter2ReportJDK6() throws SnapshotException, IOException
    {
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false); // Do not dispose this as shared
        testLeakHunter2Report(snapshot, snapshot2, 4, 1, 12);
    }

    /**
     * Comparison of dump with itself - should not be a leak
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testLeakHunter2ReportJDK6none() throws SnapshotException, IOException
    {
        testLeakHunter2Report(snapshot, snapshot, 0, 0, 0);
    }

    /**
     * Comparison of two JDK11 dumps
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testLeakHunter2ReportIBM8_7() throws SnapshotException, IOException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false); // Do not dispose this as shared
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK8_64BIT_SYSTEM, false); // Do not dispose this as shared
        testLeakHunter2Report(snapshot1, snapshot2, 5, 1, 16);
    }

    /**
     * Comparison of two JDK11 dumps
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testLeakHunter2ReportJDK11() throws SnapshotException, IOException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, false); // Do not dispose this as shared
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false); // Do not dispose this as shared
        testLeakHunter2Report(snapshot1, snapshot2, 9, 1, 22);
    }

    /**
     * Reverse comparison - should not be a leak
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void testLeakHunter2ReportJDK11none() throws SnapshotException, IOException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, false); // Do not dispose this as shared
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false); // Do not dispose this as shared
        testLeakHunter2Report(snapshot2, snapshot1, 0, 0, 0);
    }

    public void testLeakHunter2Report(ISnapshot snapshot1, ISnapshot snapshot2, int expectedProbs, int expectedDomTree, int expectedSubCommands) throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("leakhunter2 -baseline "+snapshot1.getSnapshotInfo().getPath(), snapshot2);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        if (expectedProbs == 0)
        {
            assertThat(t, instanceOf(TextResult.class));
            return;
        }
        // Should be 4 suspects
        SectionSpec ss = (SectionSpec)t;
        int probs = 0;
        int domtree = 0;
        int subcommands = 0;
        for (Spec s : ss.getChildren())
        {
            if (s.getName().contains("Problem"))
                ++probs;
            if (s instanceof QuerySpec)
            {
                // This is the find_leaks2 query
                QuerySpec qs = (QuerySpec)s;
                if (qs.getName().equals("Compared Dominator Trees"))
                {
                    assertThat(qs.getCommand(), startsWith("find_leaks2 "));
                    SnapshotQuery query2 = SnapshotQuery.parse(qs.getCommand(), snapshot2);
                    IResult t2 = query2.execute(new CheckedProgressListener(collector));
                    assertNotNull(t2);
                    ++domtree;
                }
                IResult t3 = qs.getResult();
                if (t3 instanceof CompositeResult)
                {
                    CompositeResult cr = (CompositeResult)t3;
                    for (CompositeResult.Entry e : cr.getResultEntries())
                    {
                        if (e.getResult() instanceof QuerySpec)
                        {
                            QuerySpec qs2 = (QuerySpec)e.getResult();
                            if (qs2.getCommand() != null)
                            {
                                SnapshotQuery query2 = SnapshotQuery.parse(qs2.getCommand(), snapshot2);
                                IResult t2 = query2.execute(new CheckedProgressListener(collector));
                                assertNotNull(t2);
                                ++subcommands;
                            }
                        }
                    }
                }
            }
        }
        assertThat("Expect problems section", probs, equalTo(expectedProbs));
        assertThat("Expected dominator tree section", domtree, equalTo(expectedDomTree));
        assertThat("Expected subqueries with commands", subcommands, equalTo(expectedSubCommands));
    }
}
