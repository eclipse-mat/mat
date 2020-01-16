/*******************************************************************************
 * Copyright (c) 2016, 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - lots of extra tests including all Java 7 collections
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.tests.TestSnapshots;

public class ExtractCollectionEntriesTest2 extends ExtractCollectionEntriesBase
{
    String snapfile;

    public ExtractCollectionEntriesTest2(String file)
    {
        this.snapfile = file;
    }

    boolean skipTest(NamedReference nr, String onlyClass) throws SnapshotException
    {
        if (onlyClass != null)
        {
            IClass cls2 = nr.getObject().getClazz();
            if (!cls2.getName().equals(onlyClass))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Test Lists
     * @param onlyClass only test this class
     */
    public void testCollections1(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.ListCollectionTestData.class.getName(), false)) {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = collectionArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    Integer numEntriesI = (Integer)cls.getSuperClass().resolveValue("COUNT");
                    int numEntries;
                    if (numEntriesI == null) {
                        numEntries = org.eclipse.mat.tests.CreateCollectionDump.ListCollectionTestData.COUNT;
                    } else {
                        numEntries = numEntriesI;
                    }
                    String name = nr.getObject().getClazz().getName();
                    if (name.contains("Singleton"))
                        numEntries = 1;
                    checkList(objAddress, numEntries, checkVals, snapshot);
                }
            }
        }
        if (onlyClass != null)
        {
            assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
        }
    }

    private IArray collectionArray(IObject obj) throws SnapshotException
    {
        return readArrayField(obj, "collections", "java.util.Collection[]");
    }

    private IArray mapArray(IObject obj) throws SnapshotException
    {
        return readArrayField(obj, "maps", "java.util.Map[]");
    }

    private IArray valueArray(IObject obj) throws SnapshotException
    {
        return readArrayField(obj, "values", "java.lang.String[]");
    }

    private IArray readArrayField(IObject obj, String fieldName, String valueType) throws SnapshotException
        {
        IArray a = (IArray)obj.resolveValue(fieldName);
        ISnapshot snapshot = obj.getSnapshot();
        if (a == null) {
            for (int i : snapshot.getOutboundReferentIds(obj.getObjectId()))
            {
                if (snapshot.isArray(i))
                {
                    IObject o = snapshot.getObject(i);
                    if (o instanceof IArray)
                    {
                        IArray a1 = (IArray)o;
                        if (a1.getClazz().getName().equals(valueType)) {
                            a = a1;
                            break;
                        }
                    }
                }
            }
        }
        return a;
    }

    /**
     * Test non-Lists
     * @param onlyClass only test this class
     */
    public void testCollections2(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.NonListCollectionTestData.class.getName(), false))
        {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = collectionArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences()) {
                    if (nr.getName().startsWith("<")) continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    Integer numEntriesI = (Integer)cls.getSuperClass().resolveValue("COUNT");
                    int numEntries;
                    if (numEntriesI == null) {
                        numEntries = org.eclipse.mat.tests.CreateCollectionDump.NonListCollectionTestData.COUNT;
                    } else {
                        numEntries = numEntriesI;
                    }
                    String name = nr.getObject().getClazz().getName();
                    if (name.contains("Singleton"))
                        numEntries = 1;
                    if (name.equals("javax.print.attribute.standard.JobStateReasons"))
                    {
                        numEntries = 1;
                        checkCollectionSize(objAddress, numEntries, snapshot);
                    }
                    else
                    {
                        checkCollectionSize(objAddress, numEntries, snapshot);
                        if (!name.startsWith("java.util.concurrent.LinkedBlocking") &&
                                        !name.startsWith("java.util.concurrent.LinkedTransfer") &&
                                        !name.startsWith("java.util.concurrent.SynchronousQueue") &&
                                        !name.startsWith("java.util.concurrent.ConcurrentLinked"))
                        {
                            checkCollectionFillRatio(objAddress, numEntries, snapshot);
                        }
                        if (!name.contains("Array") && !name.contains("Queue") && !name.contains("Deque")
                            && !(name.startsWith("java.util.Collections") && (name.endsWith("Collection") || name.endsWith("SingletonSet"))))
                        {
                            checkHashEntries(objAddress, numEntries, snapshot, true, false);
                            checkMapCollisionRatio(objAddress, numEntries, snapshot);
                            if (!name.contains("EntrySet"))
                            {
                                checkHashSetObjects(objAddress, numEntries, checkVals, snapshot);
                            }
                        }
                        else
                        {
                            // Other queries also work with list_entries
                            checkList(objAddress, numEntries, checkVals, snapshot);
                        }
                    }
                }
            }
        }
        if (onlyClass != null)
        {
            assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
        }
    }

    /**
     * Test empty Lists
     * @param onlyClass only test this class
     */
    public void testCollections3(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyListCollectionTestData.class.getName(), false))
        {
            for (int o : cls.getObjectIds())
            {
                IObject oo = snapshot.getObject(o);
                IArray a = collectionArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences())
                {
                    if (nr.getName().startsWith("<")) continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    checkList(objAddress, numEntries, checkVals, snapshot);
                }
            }
        }
        if (onlyClass != null)
        {
            assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
        }
    }

    /**
     * Test empty non-Lists
     * @param onlyClass only test this class
     */
    public void testCollections4(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyNonListCollectionTestData.class.getName(), false))
        {
            for (int o : cls.getObjectIds())
            {
                IObject oo = snapshot.getObject(o);
                IArray a = collectionArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences())
                {
                    if (nr.getName().startsWith("<")) continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    checkCollectionSize(objAddress, numEntries, snapshot);
                    checkHashEntries(objAddress, numEntries, snapshot, true, false);
                    String name = nr.getObject().getClazz().getName();
                    if (!name.startsWith("java.util.concurrent.LinkedBlocking") &&
                                    !name.startsWith("java.util.concurrent.LinkedTransfer") &&
                                    !name.startsWith("java.util.concurrent.SynchronousQueue") &&
                                    !name.startsWith("java.util.concurrent.ConcurrentLinked"))
                    {
                        checkCollectionFillRatio(objAddress, numEntries, snapshot);
                    }
                    if (!name.contains("Array") && !name.contains("Queue") && !name.contains("Deque")
                        && !(name.startsWith("java.util.Collections") && (name.endsWith("Collection") || name.endsWith("SingletonSet"))))
                    {
                        checkHashEntries(objAddress, numEntries, snapshot, true, false);
                        checkMapCollisionRatio(objAddress, numEntries, snapshot);
                        checkHashSetObjects(objAddress, numEntries, checkVals, snapshot);
                    }
                    else
                    {
                        // Other queries also work with list_entries
                        checkList(objAddress, numEntries, checkVals, snapshot);
                    }
                }
            }
        }
        if (onlyClass != null)
        {
            assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
        }
    }

    /**
     * Test Maps
     * @param onlyClass only test this class
     */
    public void testCollections5(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.MapTestData.class.getName(), false))
        {
            for (int o : cls.getObjectIds())
            {
                IObject oo = snapshot.getObject(o);
                IArray a = mapArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences())
                {
                    if (nr.getName().startsWith("<"))
                        continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    Integer numEntriesI = (Integer)cls.resolveValue("COUNT");
                    int numEntries;
                    if (numEntriesI == null) {
                        numEntries = org.eclipse.mat.tests.CreateCollectionDump.MapTestData.COUNT;
                    } else {
                        numEntries = numEntriesI;
                    }
                    String nm = nr.getObject().getClazz().getName();
                    if (nm.contains("Singleton"))
                        numEntries = 1;
                    if (nm.equals("javax.print.attribute.standard.PrinterStateReasons")
                                    || nm.equals("java.util.jar.Attributes"))
                    {
                        String snapshotName = snapshot.getSnapshotInfo().getPath();
                        if (onlyClass == null && (snapshotName.contains((new File(TestSnapshots.ORACLE_JDK7_21_64BIT)).getName()))
                                        || snapshotName.contains((new File(TestSnapshots.IBM_JDK8_64BIT_SYSTEM)).getName())
                                        || snapshotName.contains((new File(TestSnapshots.ORACLE_JDK8_05_64BIT)).getName()))
                        {
                            // For the older pre-existing dumps only had one of this sort of class
                            numEntries = 1;
                        }
                        // These maps don't have string keys and values
                        checkMap(objAddress, numEntries, snapshot);
                    }
                    else
                    {
                        checkHashEntries(objAddress, numEntries, snapshot, true, true);
                        checkMap(objAddress, numEntries, snapshot);
                    }
                }
            }
        }
        assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
    }

    /**
     * Test Empty Maps
     * @param onlyClass only test this class
     */
    public void testCollections6(ISnapshot snapshot, String onlyClass) throws SnapshotException
    {
        int objectsFound = 0;
        for (IClass cls : snapshot.getClassesByName(org.eclipse.mat.tests.CreateCollectionDump.EmptyMapTestData.class.getName(), false))
        {
            for (int o : cls.getObjectIds()) {
                IObject oo = snapshot.getObject(o);
                IArray a = mapArray(oo);
                IArray v = valueArray(oo);
                boolean checkVals = v != null;
                for (NamedReference nr : a.getOutboundReferences())
                {
                    if (nr.getName().startsWith("<")) continue;
                    if (skipTest(nr, onlyClass)) continue;
                    ++objectsFound;
                    long objAddress = nr.getObjectAddress();
                    int numEntries = 0;
                    checkHashEntries(objAddress, numEntries, snapshot, true, true);
                    checkMap(objAddress, numEntries, snapshot);
                }
            }
        }
        if (onlyClass != null)
        {
            assertThat("At least 1 object found"+(onlyClass != null ? " of type "+onlyClass : ""), objectsFound, greaterThanOrEqualTo(1));
        }
    }
}
