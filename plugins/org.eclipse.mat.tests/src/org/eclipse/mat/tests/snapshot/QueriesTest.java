/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;


import static org.junit.Assert.assertEquals;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class QueriesTest
{
    ISnapshot snapshot;

    @Before
    public void setUp() throws Exception
    {
        snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
    }

    @After
    public void tearDown() throws Exception
    {}

    /**
     * Test for grouping dominator tree by class loader
     * Enable test when fix is available
     * @throws SnapshotException
     */
    @Test
    public void testDominatorByLoader() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("dominator_tree -groupby BY_CLASSLOADER", snapshot);
        IResultTree t = (IResultTree) query.execute(new VoidProgressListener());
        // class loaders
        for (Object o : t.getElements())
        {
            IContextObject co = t.getContext(o);
            // classes
            for (Object o1 : t.getChildren(o))
            {
                // objects
                for (Object o2 : t.getChildren(o1))
                {
                    IContextObject co2 = t.getContext(o2);
                    IObject obj = snapshot.getObject(co2.getObjectId());
                    int loaderId;
                    if (obj instanceof IClass)
                        loaderId = ((IClass) obj).getClassLoaderId();
                    else if (obj instanceof IClassLoader)
                        loaderId = ((IClassLoader) obj).getObjectId();
                    else
                        loaderId = snapshot.getClassOf(co2.getObjectId()).getClassLoaderId();
                    assertEquals(obj.toString(), co.getObjectId(), loaderId);
                }
            }
        }
    }
}
