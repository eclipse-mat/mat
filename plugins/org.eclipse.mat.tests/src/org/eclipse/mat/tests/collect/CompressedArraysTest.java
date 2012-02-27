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
package org.eclipse.mat.tests.collect;

import java.util.Random;

import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.ArrayLongCompressed;
import org.junit.Assert;
import org.junit.Test;

public class CompressedArraysTest
{
    static final long SEED = 1;
    @Test
    public void testIntArrayCompressed()
    {
        Random rand = new Random(SEED);
        int INTS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= INTS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                int[] ints = new int[i];
                for (int k = 0; k < ints.length; k++)
                    ints[k] = rand.nextInt();

                ArrayIntCompressed array = new ArrayIntCompressed(ints);
                byte[] bytes = array.toByteArray();
                ArrayIntCompressed array2 = new ArrayIntCompressed(bytes);
                int[] ints2 = new int[ints.length];
                for (int k = 0; k < ints.length; k++)
                {
                    ints2[k] = array2.get(k);
                }
                Assert.assertArrayEquals(ints, ints2);
            }
        }
    }

    @Test
    public void testLongArrayCompressed()
    {
        Random rand = new Random(SEED);
        int LONGS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= LONGS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                long[] longs = new long[i];
                for (int k = 0; k < longs.length; k++)
                    longs[k] = rand.nextLong() & 0x00ffffffffff7ff8L;

                ArrayLongCompressed array = new ArrayLongCompressed(longs);
                byte[] bytes = array.toByteArray();
                ArrayLongCompressed array2 = new ArrayLongCompressed(bytes);
                long[] longs2 = new long[longs.length];
                for (int k = 0; k < longs.length; k++)
                {
                    longs2[k] = array2.get(k);
                }
                Assert.assertArrayEquals(longs, longs2);
            }
        }
    }

    /**
     * Test that an array of written as ints can be read by the long reader
     */
    @Test
    public void testIntAndLongArrayCompressed()
    {
        Random rand = new Random(SEED);
        int INTS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= INTS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                int[] ints = new int[i];
                for (int k = 0; k < ints.length; k++)
                    ints[k] = rand.nextInt();

                ArrayIntCompressed array = new ArrayIntCompressed(ints);
                byte[] bytes = array.toByteArray();
                ArrayLongCompressed array2 = new ArrayLongCompressed(bytes);
                int[] ints2 = new int[ints.length];
                for (int k = 0; k < ints.length; k++)
                {
                    ints2[k] = (int)array2.get(k);
                }
                Assert.assertArrayEquals(ints, ints2);
            }
        }
    }

    
    /**
     * Test that an array of written as longs can be read by the int reader
     */
    @Test
    public void testLongAndIntArrayCompressed()
    {
        Random rand = new Random(SEED);
        int INTS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= INTS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                long[] longs = new long[i];
                for (int k = 0; k < longs.length; k++)
                    longs[k] = rand.nextLong() & 0xffffffffL;

                ArrayLongCompressed array = new ArrayLongCompressed(longs);
                byte[] bytes = array.toByteArray();
                ArrayIntCompressed array2 = new ArrayIntCompressed(bytes);
                long[] longs2 = new long[longs.length];
                for (int k = 0; k < longs.length; k++)
                {
                    longs2[k] = array2.get(k) & 0xffffffffL;
                }
                Assert.assertArrayEquals(longs, longs2);
            }
        }
    }
}
