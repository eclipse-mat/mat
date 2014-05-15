/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

    /**
     * Test for grouping dominator tree by class loader
     * Enable test when fix is available
     * @throws SnapshotException
     */
    @Test
    public void testDominatorByLoader() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree -groupby BY_CLASSLOADER", snapshot);
        IResultTree t = (IResultTree) query.execute(new VoidProgressListener());
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
        RefinedResultBuilder builder = query.refine(new VoidProgressListener());
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
        RefinedResultBuilder builder = query.refine(new VoidProgressListener());
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
    

    /**
     * Test for sorting of incompatible items
     * @throws SnapshotException
     */
    @Test
    public void testOQLSorting() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse(OQL_MIXED_RESULT, snapshot);
        RefinedResultBuilder builder = query.refine(new VoidProgressListener());
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
        Histogram t = (Histogram)query.execute(new VoidProgressListener());
        assertTrue(t != null);
        assertEquals(17, t.getRowCount());
    }
}
