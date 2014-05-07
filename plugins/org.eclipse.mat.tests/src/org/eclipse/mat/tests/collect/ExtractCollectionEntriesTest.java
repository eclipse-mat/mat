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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

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
public class ExtractCollectionEntriesTest extends ExtractCollectionEntriesBase
{
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
}
