/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.eclipse.mat.collect.ArrayUtils;
import org.junit.Test;

/**
 * Test the sorting routines
 */
public class SortTest
{

    // A way of mapping the key to a value
    public int val(int v)
    {
        return v * (v + 5) + 7;
    }

    // A way of mapping the key to a value
    public int val(long v)
    {
        return (int) (v * (v + 5L) * 0x1234567891234567L * (v - 13L) + 7L);
    }

    /**
     * Test whether the sort function works with random data
     */
    @Test
    public void testSort()
    {
        // Generate random data
        int n = 29792349;
        // int n = 6000001;
        Random r = new Random(1);
        int k = n;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        for (int i = 0; i < key.length; ++i)
        {
            key0[i] = key[i] = r.nextInt();
            value[i] = val(key[i]);
        }
        // Sort the data
        ArrayUtils.sort(key, value);
        // A known good sort
        Arrays.sort(key0);
        // See if in order and with the expected values
        check(key, key0, value, n);
    }

    void check(int key[], int key0[], int value[], int n)
    {
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < n; ++i)
        {
            // Check keys match
            assertEquals("key", key0[i], key[i]);
            // Check values have been moved
            assertEquals("value", value[i], val(key[i]));
            // Check in order
            assertTrue(prev <= key[i]);
            prev = key[i];
        }
    }

    /**
     * Test whether the sort function works with worst case data for median of 3
     * E.g. 8 6 4 2 1 3 5 7 9 Median 8,1,9 = 8 Partition 6 4 2 1 3 5 7 8 9
     * Median 6,1,7 = 6 repeat
     */
    @Test
    public void testSort2()
    {
        // > 5000000 so that Quicksort is used
        int n = 6500001;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        for (int i = 0; i < n / 2; ++i)
        {
            key[i] = n - 1 - 2 * i;
        }

        for (int i = n / 2; i < n; ++i)
        {
            key[i] = i - n / 2 + 1;
            ;
        }
        for (int i = 0; i < n; ++i)
        {
            key0[i] = key[i];
            value[i] = val(key[i]);
        }
        // Sort the data
        ArrayUtils.sort(key, value);
        // A known good sort
        Arrays.sort(key0);
        // See if in order and with the expected values
        check(key, key0, value, n);
    }

    /**
     * Test whether the descending sort function works with random data
     */
    @Test
    public void testSortDesc()
    {
        int n = 29792349;
        // int n = 6000001;
        Random r = new Random(1);
        int k = n;
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        for (int i = 0; i < key.length; ++i)
        {
            key0[i] = key[i] = r.nextInt() * 0x82345679L + r.nextInt();
            value[i] = val(key[i]);
        }
        // Sort the data
        ArrayUtils.sortDesc(key, value);
        // A known good sort
        Arrays.sort(key0);
        // See if in order and with the expected values
        check(key, key0, value, n);
    }

    void check(long key[], long key0[], int value[], int n)
    {
        long prev = Long.MAX_VALUE;
        for (int i = 0; i < n; ++i)
        {
            // Check keys match
            assertEquals("key "+i, key0[n - 1 - i], key[i]);
            // Check values have been moved
            assertEquals("value", value[i], val(key[i]));
            // Check in order
            assertTrue(prev >= key[i]);
            prev = key[i];
        }
    }

    /**
     * Test whether the sort function works with worst case data for median of 3
     * E.g. 8 6 4 2 1 3 5 7 9 Median 8,1,9 = 8 Partition 6 4 2 1 3 5 7 8 9
     * Median 6,1,7 = 6 repeat
     */
    @Test
    public void testSortDesc2()
    {
        // > 5000000 so that Quicksort is used
        int n = 6500001;
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        for (int i = 0; i < n / 2; ++i)
        {
            key[i] = n - 1 - 2 * i;
        }

        for (int i = n / 2; i < n; ++i)
        {
            key[i] = i - n / 2 + 1;
            ;
        }
        for (int i = 0; i < n; ++i)
        {
            key0[i] = key[i];
            value[i] = val(key[i]);
        }
        // Sort the data
        ArrayUtils.sortDesc(key, value);
        // A known good sort
        Arrays.sort(key0);
        // See if in order and with the expected values
        check(key, key0, value, n);
    }
}
