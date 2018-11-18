/*******************************************************************************
 * Copyright (c) 2010, 2018 SAP AG and IBM Corporation
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.annotations.descriptors.IArgumentDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

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
        System.out.println(Arrays.toString(r2.getColumns()));
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
        System.out.println(Arrays.toString(r2.getColumns()));
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
