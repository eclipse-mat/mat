/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests;

import junit.framework.JUnit4TestAdapter;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses( { org.eclipse.mat.tests.collect.CompressedArraysTest.class, //
                org.eclipse.mat.tests.collect.PrimitiveArrayTests.class, //
                org.eclipse.mat.tests.collect.PrimitiveMapTests.class, //
                org.eclipse.mat.tests.collect.CommandTests.class, //
                org.eclipse.mat.tests.snapshot.DominatorTreeTest.class, //
                org.eclipse.mat.tests.snapshot.TestUnreachableObjects.class, //
                org.eclipse.mat.tests.snapshot.GeneralSnapshotTests.class, //
                org.eclipse.mat.tests.snapshot.TestInstanceSizes.class, //
                org.eclipse.mat.tests.snapshot.QueryLookupTest.class, //
                org.eclipse.mat.tests.snapshot.OQLTest.class })
public class AllTests
{

    /**
     * Use for Athena Builds
     * @return
     */
    public static junit.framework.Test suite() { 
        return new JUnit4TestAdapter(AllTests.class); 
    }

}
