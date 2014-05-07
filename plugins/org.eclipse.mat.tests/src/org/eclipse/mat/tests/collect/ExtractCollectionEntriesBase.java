/*******************************************************************************
 * Copyright (c) 2010, 2014 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation - moved from ExtractCollectionEntriesTest
 *    Andrew Johnson - lots of extra tests including all Java 7 collections
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;

public class ExtractCollectionEntriesBase
{

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    void checkListObjects(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query = SnapshotQuery.parse("extract_list_values 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
    
        try
        {
            IResult result = query.execute(new VoidProgressListener());
            IResultTree table = (IResultTree) result;
            if (table != null) {
                int rowCount = table.getElements().size();
    
                collector.checkThat(MessageUtil.format("Expected to extract {0} entries from list {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                                numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount, equalTo(numEntries));
            }
        }
        catch (IllegalArgumentException e)
        {
            collector.addError(e);
        }
    }

    protected void checkHashSetObjects(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query = SnapshotQuery.parse("hash_set_values 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
    
        try
        {
            IResult result = query.execute(new VoidProgressListener());
            IResultTree table = (IResultTree) result;
            if (table != null) {
                int rowCount = table.getElements().size();
    
                collector.checkThat(MessageUtil.format("Expected to extract {0} entries from hash set {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                                numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount, equalTo(numEntries));
            }
        }
        catch (IllegalArgumentException e)
        {
            collector.addError(e);
        }
    }

    protected void checkCollection(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkHashEntries(objAddress, numEntries, snapshot, false);
        checkMap(objAddress, numEntries, snapshot);
    }

    protected void checkHashEntries(long objAddress, int numEntries, ISnapshot snapshot, boolean checkKeyString) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query = SnapshotQuery.parse("hash_entries 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
    
        IResult result = query.execute(new VoidProgressListener());
        IResultTable table = (IResultTable) result;
        int rowCount = table.getRowCount();
    
        collector.checkThat(MessageUtil.format("Expected to extract {0} entries from collection {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                        numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount, equalTo(numEntries));
        // Check that at least one key and value value differs
        boolean diff = rowCount == 0;
        boolean allnull = true;
        for (int i = 0; i < rowCount; ++i)
        {
            Object row = table.getRow(i);
            Object k1 = table.getColumnValue(row, 1);
            Object v1 = table.getColumnValue(row, 2);
            if (k1 != null ? !k1.equals(v1) : v1 != null)
            {
                diff = true;
            }
            if (k1 != null)
            {
                allnull = false;
                collector.checkThat("Key should be an String", k1 instanceof String, is(true));
                if (checkKeyString && k1 instanceof String)
                {
                    try
                    {
                        int t = Integer.parseInt((String)k1);
                    }
                    catch (NumberFormatException e)
                    {
                        collector.addError(e);
                    }
                }
            }
            if (v1 != null)
            {
                collector.checkThat("Value should be an String", v1 instanceof String, is(true));
                allnull = false;
            }
        }
        collector.checkThat(MessageUtil.format(
                        "Expected some differing keys and values {0} from collection {1} [{2}]", obj, snapshot
                        .getSnapshotInfo().getPath()), diff, is(true));
    }

    /**
     * Also run the size query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkCollectionSize(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query2 = SnapshotQuery.parse("collections_grouped_by_size 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        collector.checkThat("Size rows "+obj, rowCount2, equalTo(1));
        if (rowCount2 == 1)
        {
            Object row = table2.getRow(0);
            int sizeBucket = (Integer)table2.getColumnValue(row, 0);
            collector.checkThat("Size "+obj, sizeBucket, equalTo(numEntries));
        }
    }

    protected void checkMap(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkCollectionSize(objAddress, numEntries, snapshot);
        checkCollectionFillRatio(objAddress, numEntries, snapshot);
        checkMapCollisionRatio(objAddress, numEntries, snapshot);
    }

    protected void checkList(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkCollectionSize(objAddress, numEntries, snapshot);
        checkListObjects(objAddress, numEntries, snapshot);
    }

    /**
     * Run the fill ratio query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkCollectionFillRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query2 = SnapshotQuery.parse("collection_fill_ratio 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        String className = snapshot.getClassOf(snapshot.mapAddressToId(objAddress)).getName();
        if (className.equals("java.util.TreeMap") || className.equals("java.util.concurrent.ConcurrentSkipListMap") ||
            className.equals("java.util.TreeSet") || className.equals("java.util.concurrent.ConcurrentSkipListSet"))
        {
            // TreeMaps and ConcurrentSkipListMap don't appear in the fill ratio report as they
            // don't have a backing array
            collector.checkThat("Fill ratio rows "+obj, rowCount2, equalTo(0));
        }
        else
        {
            collector.checkThat("Fill ratio rows "+obj, rowCount2, equalTo(1));
            if (rowCount2 == 1) {
                Object row = table2.getRow(0);
                double v = (Double)table2.getColumnValue(row, 0);
                if (numEntries > 0)
                {
                    collector.checkThat("Fill ratio value > 0.0 "+v+" "+obj, v > 0.0, is(true));
                }
                else
                {
                    // 1.0 if the size == 0, capacity == 0, 0.0 if the size == 0, capacity > 0 
                    collector.checkThat("Fill ratio value == 0.0 or 1.0 "+v+" "+obj, v == 0.0 || v == 1.0, is(true));
                }
            }
        }
    }

    /**
     * Also run the map collision ratio query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkMapCollisionRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query2 = SnapshotQuery.parse("map_collision_ratio 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        collector.checkThat("Map collision rows "+obj, rowCount2, equalTo(1));
        if (rowCount2 == 1)
        {
            Object row = table2.getRow(0);
            double v = (Double)table2.getColumnValue(row, 0);
            collector.checkThat("Map collision value >= 0.0 "+v+" "+obj, v >= 0.0, is(true));
            // 100% collisions shouldn't be possible
            collector.checkThat("Map collision value < 1.0 "+v+" "+obj, v < 1.0, is(true));
            // No collisions possible if no entries
            if (numEntries == 0)
                collector.checkThat("Map collision ratio if no entries "+obj, v, equalTo(0.0));
        }
    }

}
