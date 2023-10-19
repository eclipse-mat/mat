/*******************************************************************************
 * Copyright (c) 2018,2023 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corportation/Andrew Johnson - move out dump tests into separate class
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeThat;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.tests.TestSnapshots;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = Parameterized.class)
public class ExtractCollectionEntriesTest4 extends ExtractCollectionEntriesTest2
{
    public ExtractCollectionEntriesTest4(String file)
    {
        super(file);
    }
    
    @Parameters
    public static Collection<Object[]> data()
    {
        Object[][] data = new Object[][] { { TestSnapshots.ORACLE_JDK7_21_64BIT },
                        { TestSnapshots.IBM_JDK8_64BIT_SYSTEM },
                        { TestSnapshots.IBM_JDK8_64BIT_HEAP_AND_JAVA }, // currently problems with PHD collections
                        { TestSnapshots.ORACLE_JDK8_05_64BIT },
                        { TestSnapshots.OPENJDK_JDK11_04_64BIT } };
        return Arrays.asList(data);
    }
    
    @Test
    public void testCollections1() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapfile, false);
        // works for PHD!
        //assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        testCollections1(snapshot, null);
    }
    
    @Test
    public void testCollections2() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapfile, false);
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        testCollections2(snapshot, null);
    }
    
    @Test
    public void testCollections3() throws SnapshotException
    {
        testCollections3(TestSnapshots.getSnapshot(snapfile,false), null);
    }
    
    @Test
    public void testCollections4() throws SnapshotException
    {
        testCollections4(TestSnapshots.getSnapshot(snapfile,false), null);
    }
    
    @Test
    public void testCollections5() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapfile, false);
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        testCollections5(snapshot, null);
    }
    
    @Test
    public void testCollections6() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(snapfile, false);
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        testCollections6(snapshot, null);
    }
}
