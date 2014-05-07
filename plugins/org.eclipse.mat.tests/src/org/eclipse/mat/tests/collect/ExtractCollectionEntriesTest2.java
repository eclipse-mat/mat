/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - lots of extra tests including all Java 7 collections
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.tests.TestSnapshots;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = Parameterized.class)
public class ExtractCollectionEntriesTest2 extends ExtractCollectionEntriesBase
{
    private String snapfile;
    
    public ExtractCollectionEntriesTest2(String file) {
       this.snapfile = file;
    }

    @Parameters
    public static Collection<Object[]> data() {
      Object[][] data = new Object[][] { { TestSnapshots.ORACLE_JDK7_21_64BIT }, 
                                         { TestSnapshots.ORACLE_JDK8_05_64BIT }
                                       };
      return Arrays.asList(data);
    }
    
    @Test
    public void testCollections1() throws SnapshotException
    {
        testCollections1(snapfile);
    }
    
    @Test
    public void testCollections2() throws SnapshotException
    {
        testCollections2(snapfile);
    }
    
    @Test
    public void testCollections3() throws SnapshotException
    {
        testCollections3(snapfile);
    }
    
    @Test
    public void testCollections4() throws SnapshotException
    {
        testCollections4(snapfile);
    }
    
    @Test
    public void testCollections5() throws SnapshotException
    {
        testCollections5(snapfile);
    }
    
    @Test
    public void testCollections6() throws SnapshotException
    {
        testCollections6(snapfile);
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
}
