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
