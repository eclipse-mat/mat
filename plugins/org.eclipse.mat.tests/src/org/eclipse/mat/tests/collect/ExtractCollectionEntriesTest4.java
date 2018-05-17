/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corportation/Andrew Johnson - move out dump tests into separate class
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
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
                        // { TestSnapshots.IBM_JDK8_64BIT_HEAP_AND_JAVA }, currently problems with PHD collections
                        { TestSnapshots.ORACLE_JDK8_05_64BIT } };
        return Arrays.asList(data);
    }
    
    @Test
    public void testCollections1() throws SnapshotException
    {
        testCollections1(TestSnapshots.getSnapshot(snapfile, false), null);
    }
    
    @Test
    public void testCollections2() throws SnapshotException
    {
        testCollections2(TestSnapshots.getSnapshot(snapfile,false), null);
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
        testCollections5(TestSnapshots.getSnapshot(snapfile,false), null);
    }
    
    @Test
    public void testCollections6() throws SnapshotException
    {
        testCollections6(TestSnapshots.getSnapshot(snapfile,false), null);
    }
}
