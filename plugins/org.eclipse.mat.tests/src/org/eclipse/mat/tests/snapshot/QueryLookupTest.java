/*******************************************************************************
 * Copyright (c) 2010, 2020 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - comparison queries
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.Format;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.annotations.descriptors.IArgumentDescriptor;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

@SuppressWarnings("nls")
public class QueryLookupTest
{
    @Test
    public void testLookup() throws SnapshotException
    {
        String queryId = "histogram";
        String argumentName = "objects";
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);

        Collection<IClass> classes = snapshot.getClassesByName("java.lang.String", false);
        assertNotNull(classes);
        assertFalse(classes.isEmpty());
        int[] objectIDs = classes.iterator().next().getObjectIds();

        SnapshotQuery query = SnapshotQuery.lookup(queryId, snapshot);

        IAnnotatedObjectDescriptor queryDescriptor = query.getDescriptor();
        assert queryDescriptor != null : "query.getDescriptor() shouldn't return null";

        assert queryDescriptor.getIdentifier() != null : "query.getDescriptor().getIdentifier() shouldn't return null";
        assert queryDescriptor.getIdentifier().equals(queryId) : "query.getDescriptor().getIdentifier() must be equal to " + queryId;

        assert queryDescriptor.getHelp() != null && queryDescriptor.getHelp().length() > 0 : "Help for query " + queryId + " shouldn't be empty";

        assert queryDescriptor.getIcon() != null : "Icon for query " + queryId + " shouldn't be null";

        assert queryDescriptor.getName() != null && queryDescriptor.getName().length() > 0 : "Name for query " + queryId + " shouldn't be empty";

        List<? extends IArgumentDescriptor> arguments = query.getArguments();
        boolean foundObjectsArg = false;
        for (IArgumentDescriptor iArgumentDescriptor : arguments)
        {
            if (iArgumentDescriptor.getName().equals(argumentName))
            {
                foundObjectsArg = true;
                break;
            }
        }
        assert foundObjectsArg : "Could not find an argument named " + argumentName + " for query " + queryId;

        query.setArgument(argumentName, objectIDs);
        IResult result = query.execute(new VoidProgressListener());

        assert result != null : "The " + queryId + " query must return a non-null result";
    }

    @Test
    public void testCompare() throws SnapshotException
    {
        String queryId = "comparetablesquery";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        IResult result1 = query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("histogram", snapshot2);
        IResult result2 = query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.lookup(queryId, snapshot1);

        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        query3.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);
    }

    /**
     * Test that subtraction comparisons are done, even for sizes.
     */
    @Test
    public void testCompareDiffPrevious() throws SnapshotException
    {
        String queryId = "comparetablesquery";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        IResult result1 = query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("histogram", snapshot2);
        IResult result2 = query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.parse(queryId+" -mode DIFF_TO_PREVIOUS", snapshot1);

        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        query3.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);
        //System.out.println(Arrays.toString(r2.getColumns()));
        int count = 0;
        for (int i = 0; i < r2.getRowCount(); ++i)
        {
            Object v0 = r2.getColumnValue(r2.getRow(i), 1);
            for (int j = 1; j < r2.getColumns().length - 1; j += 2)
            {
                Object v1 = r2.getColumnValue(r2.getRow(i), j);
                Object v2 = r2.getColumnValue(r2.getRow(i), j + 1);
                // With a difference, if there is a value from table 1 then the difference has a value
                if (v1 != null)
                {
                    if (v2 != null)
                        ++count;
                }
            }
        }
        assertThat(count, greaterThan(0));
    }

    /**
     * Test that subtraction comparisons are done, even for sizes.
     */
    @Test
    public void testCompareDiffRatioFirst() throws SnapshotException
    {
        String queryId = "comparetablesquery";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        IResult result1 = query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("histogram", snapshot2);
        IResult result2 = query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.parse(queryId+" -mode DIFF_RATIO_TO_FIRST", snapshot1);


        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        query3.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);
        //System.out.println(Arrays.toString(r2.getColumns()));
        int count1 = 0;
        int count2 = 0;
        for (int i = 0; i < r2.getRowCount(); ++i)
        {
            Object v0 = r2.getColumnValue(r2.getRow(i), 1);
            for (int j = 1; j < r2.getColumns().length - 2; j += 3)
            {
                Object v1 = r2.getColumnValue(r2.getRow(i), j);
                Object v2 = r2.getColumnValue(r2.getRow(i), j + 1);
                Object v3 = r2.getColumnValue(r2.getRow(i), j + 2);
                // With a difference, if there is a value from table 1 then the difference has a value
                if (v1 != null)
                {
                    if (v2 != null)
                        ++count1;
                    if (v3 != null)
                        ++count2;
                }
            }
        }
        assertThat(count1, greaterThan(0));
        assertThat(count2, greaterThan(0));
    }

    /**
     * Test that duplicate records are recorded.
     */
    @Test
    public void testCompareDuplicateRecords1() throws SnapshotException
    {
        String queryId = "comparetablesquery -setop ALL";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        IResultTable result1 = (IResultTable)query1.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.parse(queryId, snapshot1);

        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        query3.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);

        assertThat(r2.getRowCount(), equalTo(result1.getRowCount()));
        OQLTest.checkGetOQL(r2, TestSnapshots.OPENJDK_JDK11_04_64BIT);
    }

    /**
     * Test that duplicate rows are recorded for two dumps.
     */
    @Test
    public void testCompareDuplicateRecords2() throws SnapshotException
    {
        String queryId = "comparetablesquery -setop ALL";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        IResultTable result1 = (IResultTable)query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("histogram", snapshot2);
        IResultTable result2 = (IResultTable)query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.parse(queryId, snapshot1);


        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        query3.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);

        checkTable(result1, r2, true);
        checkTable(result2, r2, false);
        OQLTest.checkGetOQL(r2, TestSnapshots.OPENJDK_JDK11_04_64BIT);
    }

    /**
     * Test that duplicate rows are recorded for two dumps.
     */
    @Test
    public void testCompareDuplicateRecords3() throws SnapshotException
    {
        String queryId = "comparetablesquery -mode DIFF_TO_PREVIOUS -prefix -mask \"\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$\" -x java.util.HashMap$Node:key java.util.Hashtable$Entry:key java.util.WeakHashMap$Entry:referent java.util.concurrent.ConcurrentHashMap$Node:key";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.parse("merge_shortest_paths .*  -excludes java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent", snapshot1);
        IResultTree result1 = (IResultTree)query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.parse("merge_shortest_paths .*  -excludes java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent", snapshot2);
        IResultTree result2 = (IResultTree)query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.parse(queryId, snapshot1);

        List<IStructuredResult> r = new ArrayList<IStructuredResult>();
        r.add(result1);
        r.add(result2);
        query3.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        query3.setArgument("snapshots", snapshots);
        IResultTree r2 = (IResultTree) query3.execute(new VoidProgressListener());
        assertTrue(r2 != null);

        //checkTable(result1, r2, true);
        //checkTable(result2, r2, false);
        checkOQL(snapshot1, r2);
    }

    /**
     * Test that OQL works for compared table
     */
    @Test
    public void testCompareOQL() throws SnapshotException
    {
        String queryId = "comparetablesquery -setop ALL";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.lookup("oql", snapshot1);
        query1.setArgument("queryString", "SELECT c.@name, c.@numberOfObjects AS Objects, c.@objectId FROM java.lang.Class c WHERE (${snapshot}.isClass(c.@objectId) and ((eval((c.@objectId / 4)).intValue() * 4) != c.@objectId))");
        IResultTable result1 = (IResultTable)query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("oql", snapshot1);
        query2.setArgument("queryString", "SELECT c.@name, c.@numberOfObjects AS Objects, c.@objectId FROM OBJECTS INSTANCEOF java.lang.Object c WHERE (${snapshot}.isClass(c.@objectId) and ((eval(((c.@objectId) / 5)).intValue() * 5) != c.@objectId))");
        IResultTable result2 = (IResultTable)query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.lookup("oql", snapshot1);
        query3.setArgument("queryString", "SELECT c.@name, c.@numberOfObjects AS Objects, c.@objectId FROM OBJECTS INSTANCEOF java.lang.Object c WHERE (${snapshot}.isClass(c.@objectId) and ((eval((c.@objectId / 3)).intValue() * 3) != c.@objectId))");
        IResultTable result3 = (IResultTable)query3.execute(new VoidProgressListener());

        SnapshotQuery queryc = SnapshotQuery.parse(queryId, snapshot1);

        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        r.add((IResultTable) result3);
        queryc.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot1);
        snapshots.add(snapshot1);
        queryc.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) queryc.execute(new VoidProgressListener());
        assertTrue(r2 != null);

        // Check any OQL from IContextObjectSet is valid
        //OQLTest.checkGetOQL(r2, TestSnapshots.OPENJDK_JDK11_04_64BIT);
        checkTable(result1, r2, true);
        checkTable(result2, r2, false);
        checkTable(result3, r2, false);
        checkOQL(snapshot1, r2);
    }

    private void checkOQL(ISnapshot snapshot1, IStructuredResult r2) throws OQLParseException, SnapshotException
    {
        int rc;
        if (r2 instanceof IResultTable)
            rc = ((IResultTable)r2).getRowCount();
        else if (r2 instanceof IResultTree)
            rc = ((IResultTree)r2).getElements().size();
        else
            rc = 0;
        for (int i = 0; i < rc; ++i)
        {
            processRow(snapshot1, r2, i, null, 3, 50, 0.15);
        }
    }

    void processRow(ISnapshot snapshot1, IStructuredResult r2, int i, Object rn, int depth, int max, double decay) throws SnapshotException
    {
        Object row;
        if (r2 instanceof IResultTable)
            row = ((IResultTable)r2).getRow(i);
        else if (r2 instanceof IResultTree)
            if (rn == null)
                row = ((IResultTree)r2).getElements().get(i);
            else
                row = ((IResultTree)r2).getChildren(rn).get(i);
        else
            return;
        IContextObject context = r2.getContext(row);
        if ((context instanceof IContextObjectSet))
        {

            IContextObjectSet ic = (IContextObjectSet)context;
            String oql = ic.getOQL();
            IOQLQuery query = SnapshotFactory.createQuery(oql);
            //System.out.println(oql);
            Object ret = query.execute(snapshot1, new VoidProgressListener());
            assertNotNull(oql, ret);
        }
        for (ContextProvider cp : r2.getResultMetaData().getContextProviders())
        {
            //if (cp.getLabel().equals("Union of Table 1, Table 2 and Table 3") && row.toString().equals("java.lang.invoke.LambdaForm$MH:[1019, 513, 715]"))
            //    System.out.println(cp.getLabel());
            IContextObject context2 = cp.getContext(row);
            if (context2 instanceof IContextObjectSet)
            {
                IContextObjectSet ic = (IContextObjectSet)context2;
                int os[] = ic.getObjectIds();
                String oql = ic.getOQL();
                IOQLQuery query = SnapshotFactory.createQuery(oql);
                if (cp.getLabel().length() >= 0)
                {
                    //System.out.println(cp.getLabel()+" "+oql);
                    Object ret = query.execute(snapshot1, new VoidProgressListener());
                    String message = cp.getLabel()+" "+row+" "+oql;
                    if (os.length > 0)
                    {
                        // Results expected
                        assertNotNull(message, ret);
                        if (ret instanceof int[])
                            assertThat(message, ((int[])ret).length, equalTo(os.length));
                        else
                            assertThat(message, ((IResultTable)ret).getRowCount(), equalTo(os.length));
                    }
                    else
                    {
                        // No results expected
                        if (ret instanceof int[])
                            assertThat(message, ((int[])ret).length, equalTo(os.length));
                        else if (ret != null)
                            assertThat(message, ((IResultTable)ret).getRowCount(), equalTo(os.length));
                    }
                }
            }
        }
        if (depth > 1 && r2 instanceof IResultTree)
        {
            IResultTree tree = (IResultTree)r2;
            if (tree.hasChildren(row))
            {
                int done = 0;
                List<?> children = tree.getChildren(row);
                for (int j = 0; j < children.size(); ++j)
                {
                    if ((long)done * children.size() < (long)j * max)
                    {
                        processRow(snapshot1, r2, j, row, depth - 1, (int)(max*decay), decay);
                        ++done;
                    }
                }
            }
        }
    }

    @Test
    public void testCompareDiffRatioPreviousRetained1() throws SnapshotException, ParseException
    {
        testCompareDiffRatioPreviousRetained("histogram .*");
    }

    @Test
    public void testCompareDiffRatioPreviousRetained2() throws SnapshotException, ParseException
    {
        testCompareDiffRatioPreviousRetained("oql \"select toString(c), c.@objectId, c.@objectAddress, c.@objectId.shortValue(), c.@objectId.byteValue(), c.@objectId.floatValue(), c.@objectId.doubleValue() from java.lang.Integer c\"");
    }

    public void testCompareDiffRatioPreviousRetained(String query) throws SnapshotException, ParseException
    {
        boolean verbose = true;
        String queryId = "comparetablesquery";
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_64BIT, false);
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        //ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        //ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, false);

        SnapshotQuery query1 = SnapshotQuery.parse(query, snapshot1);
        RefinedResultBuilder rb1 = query1.refine(new VoidProgressListener());
        rb1.setInlineRetainedSizeCalculation(true);
        rb1.addDefaultContextDerivedColumn(RetainedSizeDerivedData.APPROXIMATE);
        RefinedTable result1 = (RefinedTable)rb1.build();
        List<Object>elements1 = new ArrayList<Object>();
        // Calculate some retained sizes exactly
        for (int i = 0; i < result1.getRowCount() - 3; i += 4)
        {
            elements1.add(result1.getRow(i + 1));
            elements1.add(result1.getRow(i + 3));
        }
        result1.calculate(result1.getJobs().get(0).getContextProvider(), RetainedSizeDerivedData.PRECISE, elements1, null, new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.parse(query, snapshot2);
        RefinedResultBuilder rb2 = query2.refine(new VoidProgressListener());
        rb2.setInlineRetainedSizeCalculation(true);
        rb2.addDefaultContextDerivedColumn(RetainedSizeDerivedData.APPROXIMATE);
        RefinedTable result2 = (RefinedTable)rb2.build();
        // Calculate some retained sizes exactly
        List<Object>elements2 = new ArrayList<Object>();
        for (int i = 0; i < result2.getRowCount() - 3; i += 4)
        {
            elements2.add(result2.getRow(i + 2));
            elements2.add(result2.getRow(i + 3));
        }
        result2.calculate(result2.getJobs().get(0).getContextProvider(), RetainedSizeDerivedData.PRECISE, elements2, null, new VoidProgressListener());

        // Another query without retained sizes
        SnapshotQuery query3 = SnapshotQuery.parse(query, snapshot1);
        RefinedResultBuilder rb3 = query3.refine(new VoidProgressListener());
        //rb3.setInlineRetainedSizeCalculation(true);
        rb3.addDefaultContextDerivedColumn(RetainedSizeDerivedData.APPROXIMATE);
        RefinedTable result3 = (RefinedTable)rb3.build();
        // Calculate some retained sizes exactly
        List<Object>elements3 = new ArrayList<Object>();
        for (int i = 0; i < result3.getRowCount() - 3; i += 4)
        {
            elements3.add(result3.getRow(i + 1));
            elements3.add(result3.getRow(i + 2));
        }
        result3.calculate(result3.getJobs().get(0).getContextProvider(), RetainedSizeDerivedData.PRECISE, elements3, null, new VoidProgressListener());

        SnapshotQuery queryc = SnapshotQuery.parse(queryId+" -mode DIFF_RATIO_TO_PREVIOUS", snapshot1);

        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        r.add((IResultTable) result3);
        queryc.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot2);
        snapshots.add(snapshot1);
        queryc.setArgument("snapshots", snapshots);
        RefinedResultBuilder rbc = queryc.refine(new VoidProgressListener());
        rbc.setSortOrder(4, null);
        IResultTable r2 = (IResultTable)rbc.build();
        assertTrue(r2 != null);
        //System.out.println(Arrays.toString(r2.getColumns()));
        int count1 = 0;
        int count2 = 0;
        for (int i = 0; i < r2.getRowCount(); ++i)
        {
            Object v0 = r2.getColumnValue(r2.getRow(i), 0);
            for (int j = 1; j < r2.getColumns().length - 2; j += 5)
            {
                /*
                 * 3 tables, 5 results.
                 * Table 1
                 * Table 2 - Table 1
                 * Table 2 / Table 1
                 * Table 3 - Table 2
                 * Table 3 / Table 2
                 */
                Object v1 = r2.getColumnValue(r2.getRow(i), j);
                Object v2 = r2.getColumnValue(r2.getRow(i), j + 1);
                Object v3 = r2.getColumnValue(r2.getRow(i), j + 2);
                Object v4 = r2.getColumnValue(r2.getRow(i), j + 3);
                Object v5 = r2.getColumnValue(r2.getRow(i), j + 4);
                Column cols[] = r2.getColumns();
                if (verbose) System.out.println("Row "+i+" "+cols[0].getLabel()+" "+cols[j].getLabel()+" "+cols[j+1].getLabel()+" "+cols[j+2].getLabel()+" "+cols[j+3].getLabel()+" "+cols[j+4].getLabel());
                if (verbose) System.out.println("Row "+i+" "+v0+" "+v1+" "+v2+" "+v3+" "+v4+" "+v5);
                // With a difference, if there is a value from table 1 then the difference has a value
                if (v1 != null)
                {
                    if (v2 != null)
                        ++count1;
                    if (v3 != null)
                        ++count2;
                }
                /*
                 * Check formatting and parsing.
                 */
                if (v1 != null)
                {
                    Format formatter1 = r2.getColumns()[j].getFormatter();
                    if (formatter1 != null)
                    {
                        String fv1 = formatter1.format(v1);
                        if (verbose) System.out.println(fv1);
                        assertNotNull(fv1);
                        Object vo1 = formatter1.parseObject(fv1);
                        if (v1 instanceof Double)
                        {
                            if (vo1 instanceof Long)
                                assertThat(fv1, ((Long)vo1).doubleValue(), closeTo((Double)v1, 0.01));
                            else
                                assertThat(fv1, (Double)vo1, closeTo((Double)v1, 0.01));
                        }
                        else if (v1 instanceof Number)
                        {
                            // A Bytes formatter can receive long, but return bytes
                            if (vo1 instanceof Bytes)
                                assertThat(fv1, ((Bytes)vo1).getValue(), equalTo(((Number)v1).longValue()));
                            else
                                assertThat(fv1, ((Number)vo1).doubleValue(), equalTo(((Number)v1).doubleValue()));
                        }
                        else
                            assertThat(fv1, vo1, equalTo(v1));
                        checkFormat(formatter1,r2.getColumns()[j],j);
                    }
                }
                if (v2 != null)
                {
                    Format formatter2 = r2.getColumns()[j + 1].getFormatter();
                    String fv2 = formatter2.format(v2);
                    if (verbose) System.out.println(fv2);
                    assertNotNull(fv2);
                    Object vo2 = formatter2.parseObject(fv2);
                    if (v2 instanceof Double)
                    {
                        if (vo2 instanceof Long)
                            assertThat(fv2, ((Long)vo2).doubleValue(), closeTo((Double)v2, 0.01));
                        else
                        {
                            // E.g. percent can be returned as com.ibm.icu.math.BigDecimal
                            assertThat(fv2, vo2, instanceOf(Double.class));
                            assertThat(fv2, (Double)vo2, closeTo((Double)v2, 0.01));
                        }
                    }
                    else if (v2 instanceof Number)
                    {
                        // A Bytes formatter can receive long, but return bytes
                        if (vo2 instanceof Bytes)
                            assertThat(fv2, ((Bytes)vo2).getValue(), equalTo(((Number)v2).longValue()));
                        else
                            assertThat(fv2, ((Number)vo2).doubleValue(), equalTo(((Number)v2).doubleValue()));
                    }
                    else
                        assertThat(fv2, vo2, equalTo(v2));
                    checkFormat(formatter2, r2.getColumns()[j+1],j+1);
                }
                if (v3 != null)
                {
                    Format formatter3 = r2.getColumns()[j + 2].getFormatter();
                    String fv3 = formatter3.format(v3);
                    if (verbose) System.out.println(fv3);
                    assertNotNull(fv3);
                    Object vo3 = formatter3.parseObject(fv3);
                    if (v3 instanceof Double)
                    {
                        // E.g. percent can be returned as com.ibm.icu.math.BigDecimal
                        assertThat(fv3, vo3, instanceOf(Number.class));
                        // Hamcrest closeTo matcher problem with infinity
                        if (Double.isInfinite((Double)v3))
                            assertThat(fv3, vo3, equalTo(v3));
                        else
                            assertThat(fv3, ((Number)vo3).doubleValue(), closeTo((Double)v3, 0.01));
                    }
                    else
                        assertThat(fv3, vo3, equalTo(v3));
                    checkFormat(formatter3, r2.getColumns()[j+2],j+2);
                }
                if (v4 != null)
                {
                    Format formatter4 = r2.getColumns()[j + 3].getFormatter();
                    String fv4 = formatter4.format(v4);
                    if (verbose) System.out.println(fv4);
                    assertNotNull(fv4);
                    Object vo4 = formatter4.parseObject(fv4);
                    if (v4 instanceof Double)
                    {
                        if (vo4 instanceof Long)
                            assertThat(fv4, ((Long)vo4).doubleValue(), closeTo((Double)v4, 0.01));
                        else
                        {
                            // E.g. percent can be returned as com.ibm.icu.math.BigDecimal
                            assertThat(fv4, vo4, instanceOf(Double.class));
                            assertThat(fv4, (Double)vo4, closeTo((Double)v4, 0.01));
                        }
                    }
                    else if (v4 instanceof Number)
                    {
                        // A Bytes formatter can receive long, but return bytes
                        if (vo4 instanceof Bytes)
                            assertThat(fv4, ((Bytes)vo4).getValue(), equalTo(((Number)v4).longValue()));
                        else
                            assertThat(fv4, ((Number)vo4).doubleValue(), equalTo(((Number)v4).doubleValue()));
                    }
                    else
                        assertThat(fv4, vo4, equalTo(v4));
                    checkFormat(formatter4, r2.getColumns()[j+3],j+3);
                }
                if (v5 != null)
                {
                    Format formatter5 = r2.getColumns()[j + 4].getFormatter();
                    String fv5 = formatter5.format(v5);
                    if (verbose) System.out.println(fv5);
                    assertNotNull(fv5);
                    Object vo5 = formatter5.parseObject(fv5);
                    if (v5 instanceof Double)
                    {
                        // E.g. percent can be returned as com.ibm.icu.math.BigDecimal
                        assertThat(fv5, vo5, instanceOf(Number.class));
                        // Hamcrest closeTo matcher problem with infinity
                        if (Double.isInfinite((Double)v5))
                            assertThat(fv5, vo5, equalTo(v5));
                        else
                            assertThat(fv5, ((Number)vo5).doubleValue(), closeTo((Double)v5, 0.01));
                    }
                    else
                        assertThat(fv5, vo5, equalTo(v5));
                    checkFormat(formatter5, r2.getColumns()[j+4],j+4);
                }
            }
        }
        assertThat(count1, greaterThan(0));
        assertThat(count2, greaterThan(0));
    }

    void checkFormat(Format f, Column c, int colIdx) throws ParseException
    {
        final long special = 1000000000000000L;
        long values[]= {Long.MIN_VALUE, -(1L<<53), -(1L<<52), -(1L<<51), -(1L<<50), -special*3, -special*2, -special, 0, special, special * 2, special * 3, 1L>>50, 1L>>51, 1L>>52, 1L>>53, Long.MAX_VALUE};
        long dl[] = {-special, -special + 1, -1, 0, 1, special - 1, special};
        Filter.ValueConverter vc = (Filter.ValueConverter)c.getData(Filter.ValueConverter.class);
        for (long v1 : values)
        {
            for (long v2 : dl)
            {
                long v = v1 + v2;
                String s = f.format(v);
                Object vx = f.parseObject(s);
                //System.out.println("formatted "+v+" as "+s);
                if (vx instanceof Bytes)
                    assertThat(f.toString() + " " + s, ((Bytes)vx).getValue(), equalTo(((Number)v).longValue()));
                else
                    assertThat(f.toString() + " " + s, ((Number)vx).longValue(), equalTo(v));
                if (vc != null)
                {
                    // After conversion should be an ordinary number
                    double d2 = vc.convert(v);
                    String s2 = f.format(d2);
                    // but some still might not convert
                    long limit = 4000000000000000L;
                    /*
                     * approximate values outside limit range can't be
                     * converted to a plain number which can be printed
                     * without conversion.
                     */
                    if (v < limit && v >= -limit || !s.contains("\u2248"))
                        assertTrue(colIdx+":"+c.getLabel()+" "+f.toString() +" " + vc + "\n" + v + "\n" + d2 + "\n" + s + "\n" + s2, s2.matches("[+-]?[0-9,]+(\\.[0-9]+)?(\\s?%)?"));
                }
            }
        }
    }

    /**
     * Test that set operations intersection is done.
     */
    @Test
    public void testCompareSetOperationsIntersection() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 0, 60);
        int o2[] = Arrays.copyOfRange(s, 20, 80);
        int o3[] = Arrays.copyOfRange(s, 10, 50);
        int e1[] = Arrays.copyOfRange(s, 20, 60);
        int e2[] = Arrays.copyOfRange(s, 20, 50);
        testCompareSetOperations(snapshot1, "INTERSECTION" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations union is done.
     */
    @Test
    public void testCompareSetOperationsUnion() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 60);
        int o2[] = Arrays.copyOfRange(s, 20, 80);
        int o3[] = Arrays.copyOfRange(s, 10, 85);
        int e1[] = Arrays.copyOfRange(s, 5, 80);
        int e2[] = Arrays.copyOfRange(s, 5, 85);
        testCompareSetOperations(snapshot1, "UNION" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations symmetric difference is done.
     */
    @Test
    public void testCompareSetOperationsSymDiff() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 50);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 20, 50);
        int e1[] = Arrays.copyOfRange(s, 5, 20);
        int e2[] = Arrays.copyOfRange(s, 5, 50);
        testCompareSetOperations(snapshot1, "SYMMETRIC_DIFFERENCE" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations difference is done.
     */
    @Test
    public void testCompareSetOperationsDiff1() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 50);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 10, 60);
        int e1[] = Arrays.copyOfRange(s, 5, 20);
        int e2[] = Arrays.copyOfRange(s, 5, 10);
        testCompareSetOperations(snapshot1, "DIFFERENCE" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations difference is done.
     */
    @Test
    public void testCompareSetOperationsDiff2() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 50);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 5, 30);
        int e1[] = Arrays.copyOfRange(s, 5, 20);
        int e2[] = Arrays.copyOfRange(s, 30, 50);
        testCompareSetOperations(snapshot1, "DIFFERENCE -mode  DIFF_TO_FIRST" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations difference is done.
     */
    @Test
    public void testCompareSetOperationsDiff3() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 50);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 5, 30);
        int e1[] = Arrays.copyOfRange(s, 5, 20);
        int e2[] = Arrays.copyOfRange(s, 30, 50);
        testCompareSetOperations(snapshot1, "DIFFERENCE -mode DIFF_TO_PREVIOUS" , o1, o2, o3, 0, e1, 1, e2);
    }

    /**
     * Test that set operations difference is done.
     * Reverse difference
     */
    @Test
    public void testCompareSetOperationsDiff4() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 30);
        int o2[] = Arrays.copyOfRange(s, 20, 40);
        int o3[] = Arrays.copyOfRange(s, 10, 60);
        int e1[] = Arrays.copyOfRange(s, 30, 40);
        int e2[] = Arrays.copyOfRange(s, 40, 60);
        testCompareSetOperations(snapshot1, "ALL" , o1, o2, o3, 5, e1, 11, e2);
    }

    /**
     * Test that set operations difference is done.
     * Reverse difference
     */
    @Test
    public void testCompareSetOperationsDiff5() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 30);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 10, 40);
        int e1[] = Arrays.copyOfRange(s, 30, 50);
        int e2[] = Arrays.copyOfRange(s, 30, 40);
        testCompareSetOperations(snapshot1, "ALL -mode DIFF_TO_FIRST" , o1, o2, o3, 5, e1, 11, e2);
    }

    /**
     * Test that set operations difference is done.
     */
    @Test
    public void testCompareSetOperationsDiff6() throws SnapshotException
    {
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.OPENJDK_JDK11_04_64BIT, false);
        int s[] = snapshot1.getClassesByName("java.lang.String", false).iterator().next().getObjectIds();
        int o1[] = Arrays.copyOfRange(s, 5, 30);
        int o2[] = Arrays.copyOfRange(s, 20, 50);
        int o3[] = Arrays.copyOfRange(s, 5, 30);
        int e1[] = Arrays.copyOfRange(s, 30, 50);
        int e2[] = Arrays.copyOfRange(s, 5, 20);
        testCompareSetOperations(snapshot1, "ALL -mode DIFF_TO_PREVIOUS" , o1, o2, o3, 5, e1, 11, e2);
    }

    /**
     * Test that set operations are done.
     * @throws IOException
     */
    public void testCompareSetOperations(ISnapshot snapshot1, String setOp, int o1[], int o2[], int o3[], int ei1, int e1[], int ei2, int e2[]) throws SnapshotException
    {
        String queryId = "comparetablesquery";

        SnapshotQuery query1 = SnapshotQuery.lookup("histogram", snapshot1);
        query1.setArgument("objects", o1);
        IResultTable result1 = (IResultTable)query1.execute(new VoidProgressListener());

        SnapshotQuery query2 = SnapshotQuery.lookup("histogram", snapshot1);
        query2.setArgument("objects", o2);
        IResultTable result2 = (IResultTable)query2.execute(new VoidProgressListener());

        SnapshotQuery query3 = SnapshotQuery.lookup("histogram", snapshot1);
        query3.setArgument("objects", o3);
        IResultTable result3 = (IResultTable)query3.execute(new VoidProgressListener());

        SnapshotQuery query4 = SnapshotQuery.parse(queryId + " -setop " + setOp, snapshot1);
        List<IResultTable> r = new ArrayList<IResultTable>();
        r.add((IResultTable) result1);
        r.add((IResultTable) result2);
        r.add((IResultTable) result3);
        query4.setArgument("tables", r);
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(snapshot1);
        snapshots.add(snapshot1);
        snapshots.add(snapshot1);
        query4.setArgument("snapshots", snapshots);
        IResultTable r2 = (IResultTable) query4.execute(new VoidProgressListener());
        assertTrue(r2 != null);

        /*
         * 0: table 1
         * 1: table 1 Op table 2
         * 2: table 2
         * 3: table 1 Op table 2, table 3
         * 4: table 3
         */
        //System.out.println(r2.getResultMetaData().getContextProviders());
        for (ContextProvider cp : r2.getResultMetaData().getContextProviders())
        {
            //System.out.println(cp+ " " + cp.getLabel());
            cp.getLabel();
            URL u = cp.getIcon();
            if (u != null)
            {
                try
                {
                    Object o = u.getContent();
                }
                catch (IOException e)
                {
                    throw new SnapshotException(e);
                }
                assertNotNull(u);
            }
        }

        ContextProvider cp1 = r2.getResultMetaData().getContextProviders().get(ei1);
        //System.out.println(cp1+ " " + cp1.getLabel());
        int c1[] =  ((IContextObjectSet)cp1.getContext(r2.getRow(0))).getObjectIds();
        int c1s[] = c1.clone();
        Arrays.sort(c1s);
        int e1s[] = e1.clone();
        Arrays.sort(e1s);
        assertThat(c1s, equalTo(e1s));

        ContextProvider cp3 = r2.getResultMetaData().getContextProviders().get(ei2);
        //System.out.println(cp3+ " " + cp3.getLabel());
        int c3[] =  ((IContextObjectSet)cp3.getContext(r2.getRow(0))).getObjectIds();
        int c3s[] = c3.clone();
        Arrays.sort(c3s);
        int e2s[] = e2.clone();
        Arrays.sort(e2s);
        assertThat(c3s, equalTo(e2s));

        // Histogram doesn't have OQL for set of objects
        //OQLTest.checkGetOQL(r2, TestSnapshots.OPENJDK_JDK11_04_64BIT);
    }

    private void checkTable(IResultTable result1, IResultTable r2, boolean check2)
    {
        assertThat(r2.getRowCount(), greaterThanOrEqualTo(result1.getRowCount()));
        HashMap<Object,List<Integer>> cnt1 = countKeys(result1);
        HashMap<Object,List<Integer>> cnt2 = countKeys(r2);
        for (Map.Entry<Object, List<Integer>> e : cnt1.entrySet())
        {
            assertThat(cnt2.get(e.getKey()).size(), greaterThanOrEqualTo(e.getValue().size()));
        }

        if (check2)
        {
            for (int i = 0; i < result1.getRowCount(); ++i)
            {
                boolean found = false;
                Object row1 = result1.getRow(i);
                IContextObject c1 = result1.getContext(row1);
                Object keyval = result1.getColumnValue(row1, 0);
                for (int j : cnt2.get(keyval))
                {
                    Object row2 = r2.getRow(j);
                    if (keyval.equals(r2.getColumnValue(row2, 0)))
                    {
                        IContextObject c2 = r2.getContext(row2);
                        if (c2 != null && c1.getObjectId() == c2.getObjectId())
                        {
                            found = true;
                            break;
                        }
                    }
                }
                // Either check exact match if context found
                // or at least as many as in the original table
                assertTrue("Row "+i+" "+keyval+" objId "+c1.getObjectId(), found);
            }
        }
    }

    private HashMap<Object,List<Integer>> countKeys(IResultTable r2)
    {
        HashMap<Object,List<Integer>>hm = new HashMap<Object,List<Integer>>();
        for (int j = 0; j < r2.getRowCount(); ++j)
        {
            Object row2 = r2.getRow(j);
            Object key = r2.getColumnValue(row2, 0);
            if (!hm.containsKey(key))
                hm.put(key, new ArrayList<Integer>());
            List<Integer>l = hm.get(key);
            l.add(j);
        }
        return hm;
    }

    @Test()
    public void testParse() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        SnapshotQuery query = SnapshotQuery.parse("leaking_bundles", snapshot);
        assertNotNull(query);
    }

    /**
     * BundleLoaderProxy not present
     * @throws SnapshotException
     */
    @Test(expected = SnapshotException.class)
    public void testSubjectsAnnotation1() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        SnapshotQuery query = SnapshotQuery.lookup("leaking_bundles", snapshot);
        assertNotNull(query);
    }

    /**
     * BundleRepository not present
     * @throws SnapshotException
     */
    @Test(expected = SnapshotException.class)
    public void testSubjectsAnnotation2() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        SnapshotQuery query = SnapshotQuery.lookup("bundle_registry", snapshot);
        assertNotNull(query);
    }

    /**
     * java.lang.System present
     * @throws SnapshotException
     */
    @Test()
    public void testSubjectsAnnotation3() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        SnapshotQuery query = SnapshotQuery.lookup("system_properties", snapshot);
        assertNotNull(query);
    }

    /**
     * char[] present
     * @throws SnapshotException
     */
    @Test()
    public void testSubjectsAnnotation4() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        SnapshotQuery query = SnapshotQuery.lookup("waste_in_char_arrays", snapshot);
        assertNotNull(query);
    }
}
