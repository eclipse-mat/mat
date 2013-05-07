/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.tests.TestSnapshots;
import org.junit.Test;

public class MultipleSnapshots
{

    /**
     * Simple test that without an option multiple snapshots cause an exception
     */
    @Test
    public void testException()
    {
        try
        {
            ISnapshot fail = TestSnapshots.getSnapshot(TestSnapshots.ORACLE_JDK7_21_64BIT_HPROFAGENT, true);
            fail("Expected a MultipleSnapshotsException");
        }
        catch (RuntimeException e)
        {
            Throwable f = e.getCause();
            assertTrue(f instanceof MultipleSnapshotsException);
            MultipleSnapshotsException s = (MultipleSnapshotsException)f;
            assertEquals(2, s.getRuntimes().size());
            List<MultipleSnapshotsException.Context>ctxs = s.getRuntimes();
            assertEquals("#1", ctxs.get(0).getRuntimeId());
            assertEquals("#2", ctxs.get(1).getRuntimeId());
        }
    }

    /**
     * Simple test that the two snapshots are different
     */
    @Test
    public void testDump1()
    {
        Map<String, String> options = new HashMap<String, String>();
        options.put("runtime_identifier", "#1");
        ISnapshot snapshot1 = TestSnapshots.getSnapshot(TestSnapshots.ORACLE_JDK7_21_64BIT_HPROFAGENT, options, true);
        options.put("runtime_identifier", "#2");
        ISnapshot snapshot2 = TestSnapshots.getSnapshot(TestSnapshots.ORACLE_JDK7_21_64BIT_HPROFAGENT, options, true);
        assertTrue(snapshot1.getSnapshotInfo().getNumberOfObjects() != snapshot2.getSnapshotInfo().getNumberOfObjects());

    }
}
