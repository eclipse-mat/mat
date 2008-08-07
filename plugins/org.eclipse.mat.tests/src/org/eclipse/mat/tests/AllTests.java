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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import org.eclipse.mat.tests.collect.CompressedArraysTest;
import org.eclipse.mat.tests.collect.PrimitiveArrayTests;
import org.eclipse.mat.tests.collect.PrimitiveMapTests;
import org.eclipse.mat.tests.snapshot.DominatorTreeTest;

@RunWith(Suite.class)
@SuiteClasses( { CompressedArraysTest.class, PrimitiveArrayTests.class, PrimitiveMapTests.class, DominatorTreeTest.class })
public class AllTests
{

}
