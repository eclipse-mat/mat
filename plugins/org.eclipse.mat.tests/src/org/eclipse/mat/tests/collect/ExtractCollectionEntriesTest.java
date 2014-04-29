/*******************************************************************************
 * Copyright (c) 2010, 2014 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

/**
 * Tests for the HashEntriesQuery
 * hash_entries
 * collections_grouped_by_size
 * map_collision_ratio
 * extract_list_values
 * hash_set_values
 * 
 * @author ktsvetkov
 * 
 */
public class ExtractCollectionEntriesTest
{
    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Test
    public void testHashMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241957d0, 60, snapshot);
    }

    @Test
    public void testHashMapEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcb1d88, 454, snapshot);
    }

    @Test
    public void testHashMapEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c671a8, 54, snapshot);
    }

    @Test
    public void testHashMapSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcb1d88, 454, snapshot);
    }

    @Test
    public void testHashMapSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16b7470, 54, snapshot);
    }

    @Test
    public void testHashMapEntries_IBM_JDK142() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK142_32BIT_SYSTEM, false);
        checkCollection(0xbcb688, 469, snapshot);
    }

    @Test
    public void testLinkedHashMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        // 0x24166638, 68 entries has null keys and values
        checkCollection(0x24166668, 45, snapshot);
    }

    // TODO figure out how to extract entries
    public void testIdentityHashMapEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xd048c8, 454, snapshot);
    }

    public void testIdentityHashMapEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c66b30, 2, snapshot);
    }

    @Test
    public void testHashSetEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241abf40, 2, snapshot);
    }

    // TODO add HashSet tests for IBM dumps pre-JDK7
    @Test
    public void testHashSetEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c69748, 8, snapshot);
    }

    @Test
    public void testLinkedHashSetEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416f090, 23, snapshot);
    }

    // TODO add LinkedHashSet tests for IBM dumps

    @Test
    public void testHashtableEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416b168, 19, snapshot);
    }

    @Test
    public void testHashtableEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcf7808, 26, snapshot);
    }

    @Test
    public void testHashtableEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c69320, 36, snapshot);
    }

    @Test
    public void testHashtableSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcf7808, 26, snapshot);
    }

    @Test
    public void testHashtableSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16b96e8, 36, snapshot);
    }

    @Test
    public void testHashtableEntries_IBM_JDK142() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK142_32BIT_SYSTEM, false);
        // 0xbb9648 has identical keys and values, so is harder to test
        checkCollection(0xbb8a10, 2, snapshot);
    }

    @Test
    public void testPropertiesEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24120b38, 53, snapshot);
    }

    @Test
    public void testPropertiesEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcb1808, 71, snapshot);
    }

    @Test
    public void testPropertiesEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c66400, 72, snapshot);
    }

    @Test
    public void testPropertiesSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcb1808, 71, snapshot);
    }

    @Test
    public void testPropertiesSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16b6468, 72, snapshot);
    }

    @Test
    public void testPropertiesEntries_IBM_JDK142() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK142_32BIT_SYSTEM, false);
        checkCollection(0xbbb4c8, 56, snapshot);
    }

    @Test
    public void testWeakHashMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416b9f0, 11, snapshot);
    }

    // TODO add WeakHashMap tests for IBM dumps pre-JDK7
    @Test
    public void testWeakHashMapEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c69590, 4, snapshot);
    }

    @Test
    public void testThreadLocalMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241a10b8, 2, snapshot);
    }

    @Test
    public void testThreadLocalMapEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcb1c10, 2, snapshot);
    }

    @Test
    public void testThreadLocalMapEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c85540, 2, snapshot);
    }

    @Test
    public void testThreadLocalMapSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcb1c10, 2, snapshot);
    }

    @Test
    public void testThreadLocalMapSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16d5f88, 2, snapshot);
    }

    @Test
    public void testThreadLocalMapEntries_IBM_JDK142() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK142_32BIT_SYSTEM, false);
        checkCollection(0xbc2e58, 2, snapshot);
    }

    @Test
    public void testConcurrentHashMapSegmentEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24196fd8, 8, snapshot);
    }

    @Test
    public void testConcurrentHashMapSegmentEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcb4958, 5, snapshot);
    }

    @Test
    public void testConcurrentHashMapSegmentEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c83398, 3, snapshot);
    }

    @Test
    public void testConcurrentHashMapSegmentSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcb4958, 5, snapshot);
    }	

    @Test
    public void testConcurrentHashMapSegmentSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16d39e0, 3, snapshot);
    }   

    @Test
    public void testConcurrentHashMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2419d490, 65, snapshot);
    }

    @Test
    public void testConcurrentHashMapEntries_IBM_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, false);
        checkCollection(0xcb4578, 19, snapshot);
    }

    @Test
    public void testConcurrentHashMapEntries_IBM_JDK7() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_SYSTEM, false);
        checkCollection(0x2c6efd8, 19, snapshot);
    }

    @Test
    public void testConcurrentHashMapSize_IBM_JDK6_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, false);
        checkMap(0xcb4578, 19, snapshot);
    }

    @Test
    public void testConcurrentHashMapSize_IBM_JDK7_PHD() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.IBM_JDK7_64BIT_HEAP_AND_JAVA, false);
        checkMap(0x16bf3a0, 19, snapshot);
    }

    @Test
    public void testTreeMapEntries_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24185f68, 16, snapshot);

        checkCollection(0x24196458, 0, snapshot); // test zero-sized map
    }

    // TODO add TreeMap tests for IBM dumps

    @Test
    public void testCustomCollection_Sun_JDK6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        long objAddress = 0x241957d0;
        int numEntries = 60;

        SnapshotQuery query = SnapshotQuery
                        .parse("hash_entries 0x" + Long.toHexString(objAddress) + " -collection java.util.HashMap -array_attribute table -key_attribute key -value_attribute value", snapshot); //$NON-NLS-1$
        IResult result = query.execute(new VoidProgressListener());
        IResultTable table = (IResultTable) result;
        int rowCount = table.getRowCount();

        assert rowCount == numEntries : MessageUtil.format("Expected to extract {0} entries from collection 0x{1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                        numEntries, Long.toHexString(objAddress), snapshot.getSnapshotInfo().getPath(), rowCount);
    }

    @Test
    public void testCollections1_Oracle_JDK7() throws SnapshotException
    {
        testCollections1(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections1_Oracle_JDK8() throws SnapshotException
    {
        testCollections1(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test Lists
     */
    public void testCollections1(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.ListCollectionTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("collections");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = org.eclipse.mat.tests.CreateCollectionDump.ListCollectionTestData.COUNT;
                    checkList(objAddress, numEntries, snapshot);
                }
            }
        }
    }

    @Test
    public void testCollections2_Oracle_JDK7() throws SnapshotException
    {
        testCollections2(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections2_Oracle_JDK8() throws SnapshotException
    {
        testCollections2(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test non-Lists
     */
    public void testCollections2(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.NonListCollectionTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("collections");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = org.eclipse.mat.tests.CreateCollectionDump.NonListCollectionTestData.COUNT;
                    checkCollectionSize(objAddress, numEntries, snapshot);
                    IObject o2 = snapshot.getObject(snapshot.mapAddressToId(objAddress));
                    String name = o2.getClazz().getName();
                    if (!name.startsWith("java.util.concurrent.LinkedBlocking"))
                    {
                        checkCollectionFillRatio(objAddress, numEntries, snapshot);
                    }
                    if ((name.contains("Set") || name.contains("Map")) &&
                                    !(name.contains("Array")))
                    {
                        checkHashEntries(objAddress, numEntries, snapshot, false);
                        checkMapCollisionRatio(objAddress, numEntries, snapshot);
                        checkHashSetObjects(objAddress, numEntries, snapshot);
                    }
                }
            }
        }
    }

    @Test
    public void testCollections3_Oracle_JDK7() throws SnapshotException
    {
        testCollections3(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections3_Oracle_JDK8() throws SnapshotException
    {
        testCollections3(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test empty Lists
     */
    public void testCollections3(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyListCollectionTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("collections");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    checkList(objAddress, numEntries, snapshot);
                }
            }
        }
    }

    @Test
    public void testCollections4_Oracle_JDK7() throws SnapshotException
    {
        testCollections4(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections4_Oracle_JDK8() throws SnapshotException
    {
        testCollections4(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test empty non-Lists
     */
    public void testCollections4(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyNonListCollectionTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("collections");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    IObject o2 = snapshot.getObject(snapshot.mapAddressToId(objAddress));
                    System.out.println("coll 4 "+o2);
                    checkCollectionSize(objAddress, numEntries, snapshot);
                    checkHashEntries(objAddress, numEntries, snapshot, true);
                    if (!o2.getClazz().getName().contains("Queue"))
                    {
                        checkCollectionFillRatio(objAddress, numEntries, snapshot);
                        checkHashSetObjects(objAddress, numEntries, snapshot);
                    }
                }
            }
        }
    }

    @Test
    public void testCollections5_Oracle_JDK7() throws SnapshotException
    {
        testCollections5(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections5_Oracle_JDK8() throws SnapshotException
    {
        testCollections5(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test Maps
     */
    public void testCollections5(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.MapTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("maps");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = org.eclipse.mat.tests.CreateCollectionDump.MapTestData.COUNT;
                    checkHashEntries(objAddress, numEntries, snapshot, true);
                    checkMap(objAddress, numEntries, snapshot);
                }
            }
        }
    }

    @Test
    public void testCollections6_Oracle_JDK7() throws SnapshotException
    {
        testCollections6(TestSnapshots.ORACLE_JDK7_21_64BIT);
    }

    @Test
    public void testCollections6_Oracle_JDK8() throws SnapshotException
    {
        testCollections6(TestSnapshots.ORACLE_JDK8_05_64BIT);
    }

    /**
     * Test Empty Maps
     */
    public void testCollections6(String snapshotFile) throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotFile, false);
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyMapTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = (IArray)oo.resolveValue("maps");
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    checkHashEntries(objAddress, numEntries, snapshot, true);
                    checkMap(objAddress, numEntries, snapshot);
                }
            }
        }
    }
    private void checkListObjects(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
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
    
    private void checkHashSetObjects(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
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

    private void checkCollection(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkHashEntries(objAddress, numEntries, snapshot, false);
        checkMap(objAddress, numEntries, snapshot);
    }

    private void checkHashEntries(long objAddress, int numEntries, ISnapshot snapshot, boolean checkKeyString) throws SnapshotException
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
    private void checkCollectionSize(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
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

    private void checkMap(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkCollectionSize(objAddress, numEntries, snapshot);
        checkCollectionFillRatio(objAddress, numEntries, snapshot);
        checkMapCollisionRatio(objAddress, numEntries, snapshot);
    }

    private void checkList(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
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
    private void checkCollectionFillRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        SnapshotQuery query2 = SnapshotQuery.parse("collection_fill_ratio 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        String className = snapshot.getClassOf(snapshot.mapAddressToId(objAddress)).getName();
        System.out.println("Class name "+className+" 0x" + Long.toHexString(objAddress)+" "+rowCount2);
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
                System.out.println("col "+table2.getColumnValue(row, 0));
                System.out.println("col "+table2.getColumnValue(row, 1));
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
    private void checkMapCollisionRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
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
