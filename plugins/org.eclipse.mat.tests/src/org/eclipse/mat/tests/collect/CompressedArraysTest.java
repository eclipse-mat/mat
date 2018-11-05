/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - test array overwrites, and different leading and trailing zeroes
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.junit.Assert.assertEquals;

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

    /**
     * Test that we can overwrite values in the Array.
     * Also test every combination of leading and trailing zeroes.
     */
    @Test
    public void testIntArrayCompressedOverwrite()
    {
        Random rand = new Random(SEED);
        int INTS = 100;
        for (int i = 1; i <= INTS; i++)
        {
            // size in bits
            for (int j1 = 1; j1 <= Integer.SIZE; j1++)
            {
                // trailing clear bits
                for (int j2 = 0; j1 + j2 <= Integer.SIZE; j2++)
                {
                    int[] ints = new int[i];
                    int order[] = new int[i];
                    for (int k = 0; k < ints.length; k++)
                    {
                        ints[k] = (rand.nextInt() >>> Integer.SIZE - j1) << j2;
                        order[k] = k;
                    }
                    // Shuffle order
                    for (int k = 0; k < order.length; ++k)
                    {
                        int l = rand.nextInt(order.length - k);
                        int t = order[k];
                        order[k] = order[l];
                        order[l] = t;
                    }

                    ArrayIntCompressed array = new ArrayIntCompressed(ints.length, Integer.SIZE - j1 - j2, j2);

                    // Random fill
                    for (int k = 0; k < order.length; ++k)
                    {
                        array.set(k, (rand.nextInt() >>> Integer.SIZE - j1) << j2);
                    }

                    String msg = "i=" + i + " j1=" + j1 + " j2=" + j2;
                    // Fill final values in random order
                    for (int k = 0; k < order.length; ++k)
                    {
                        int kx = order[k];
                        int prev = kx - 1 >= 0 ? array.get(kx - 1) : 0;
                        int next = kx + 1 < order.length ? array.get(kx + 1) : 0;
                        array.set(kx, ints[kx]);
                        int prev1 = kx - 1 >= 0 ? array.get(kx - 1) : 0;
                        int val = array.get(kx);
                        int next1 = kx + 1 < order.length ? array.get(kx + 1) : 0;
                        assertEquals(msg+" kx=" + kx, ints[kx], val);
                        assertEquals(msg+" prev kx=" + kx, prev, prev1);
                        assertEquals(msg+" next kx=" + kx, next, next1);
                    }
                    byte[] bytes = array.toByteArray();
                    int expectedLength = 2 + (j1 * ints.length + 7) / 8;
                    assertEquals(msg+" expected compressed length", expectedLength, bytes.length);
                    ArrayIntCompressed array2 = new ArrayIntCompressed(bytes);
                    int[] ints2 = new int[ints.length];
                    for (int k = 0; k < ints.length; k++)
                    {
                        ints2[k] = array2.get(k);
                    }
                    Assert.assertArrayEquals(msg, ints, ints2);
                }
            }
        }
    }

    /**
     * Test that we can overwrite values in the Array.
     * Also test every combination of leading and trailing zeroes.
     */
    @Test
    public void testLongArrayCompressedOverwrite()
    {
        Random rand = new Random(SEED);
        int LONGS = 100;
        for (int i = 1; i <= LONGS; i++)
        {
            // size in bits
            for (int j1 = 1; j1 <= Long.SIZE; j1++)
            {
                // trailing clear bits
                for (int j2 = 0; j1 + j2 <= Long.SIZE; j2++)
                {
                    long[] longs = new long[i];
                    int order[] = new int[i];
                    for (int k = 0; k < longs.length; k++)
                    {
                        longs[k] = (rand.nextLong() >>> Long.SIZE - j1) << j2;
                        order[k] = k;
                    }
                    // Shuffle order
                    for (int k = 0; k < order.length; ++k)
                    {
                        int l = rand.nextInt(order.length - k);
                        int t = order[k];
                        order[k] = order[l];
                        order[l] = t;
                    }

                    ArrayLongCompressed array = new ArrayLongCompressed(longs.length, Long.SIZE - j1 - j2, j2);

                    // Random fill
                    for (int k = 0; k < order.length; ++k)
                    {
                        array.set(k, (rand.nextLong() >>> Long.SIZE - j1) << j2);
                    }

                    String msg = "i=" + i + " j1=" + j1 + " j2=" + j2;
                    // Fill final values in random order
                    for (int k = 0; k < order.length; ++k)
                    {
                        int kx = order[k];
                        long prev = kx - 1 >= 0 ? array.get(kx - 1) : 0;
                        long next = kx + 1 < order.length ? array.get(kx + 1) : 0;
                        array.set(kx, longs[kx]);
                        long prev1 = kx - 1 >= 0 ? array.get(kx - 1) : 0;
                        long val = array.get(kx);
                        long next1 = kx + 1 < order.length ? array.get(kx + 1) : 0;
                        assertEquals(msg+" kx=" + kx, longs[kx], val);
                        assertEquals(msg+" prev kx=" + kx, prev, prev1);
                        assertEquals(msg+" next kx=" + kx, next, next1);
                    }
                    byte[] bytes = array.toByteArray();
                    int expectedLength = 2 + (j1 * longs.length + 7) / 8;
                    assertEquals(msg+" expected compressed length", expectedLength, bytes.length);
                    ArrayLongCompressed array2 = new ArrayLongCompressed(bytes);
                    long[] longs2 = new long[longs.length];
                    for (int k = 0; k < longs.length; k++)
                    {
                        longs2[k] = array2.get(k);
                    }
                    Assert.assertArrayEquals(msg, longs, longs2);
                }
            }
        }
    }
}
