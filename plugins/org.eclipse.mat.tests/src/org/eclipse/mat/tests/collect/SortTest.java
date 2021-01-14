/*******************************************************************************
 * Copyright (c) 2009, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
 * Test the sorting routines.
 * Tests have timeouts in case performance of quicksort goes quadradic in performance.
 */
public class SortTest
{

    private static final int TIMEOUT1 = 10*18*1000;
    private static final int TIMEOUT2 = 10*3*1000;
    /* Using a full length test takes too long on the Hudson build machine */
    private static final boolean longTest = false;
    /* Bigger than ArrayUtils.USE_RADIX */
    private static final int SHORTTEST = 2000001;

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
    @Test(timeout=TIMEOUT1)
    public void testSort()
    {
        // Generate random data
        int n = longTest ? 29792349 : SHORTTEST;
        Random r = new Random(1);
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
    @Test(timeout=TIMEOUT2)
    public void testSort2()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 6600001 : SHORTTEST;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        for (int i = 0; i < n / 2; ++i)
        {
            key[i] = n - 1 - 2 * i;
        }

        for (int i = n / 2; i < n; ++i)
        {
            key[i] = (i - n / 2) * 2 + 1;
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
     * Test whether the sort function works with worst case data for median of 3
     * swapping median with first
     * E.g. 9 1 2 3 4 5 6 7 8 Median 9,4,8 = 8 Partition 8 1 2 3 4 5 6 7 9 
     * swap Partition 7 1 2 3 4 5 6 8 9
     * Median 7,4,9 = 6 repeat
     */
    @Test(timeout=TIMEOUT2)
    public void testSort3()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 5200001 : SHORTTEST;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        key[0] = n;
        for (int i = 1; i < n; ++i)
        {
            key[i] = i;
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
     * Test whether the sort function works with worst case data for median of 3
     * when swapping median with last
     * E.g. 2 3 4 5 6 7 8 9 1 Median 2,6,1 = 2 Partition 1 3 4 5 6 7 8 9 2 
     * swap Partition 1 2 4 5 6 7 8 9 3
     * Median 4,7,3 = 4 repeat
     */
    @Test(timeout=TIMEOUT2)
    public void testSort4()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 5200001 : SHORTTEST;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        for (int i = 0; i < n - 1; ++i)
        {
            key[i] = i + 2;
        }
        key[n - 1] = 0;

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
     * Test whether the sort function works with random data with duplicates
     */
    @Test(timeout=TIMEOUT2)
    public void testSort5()
    {
        // Generate random data
        int n = longTest ? 29792349 : SHORTTEST;
        Random r = new Random(1);
        int k = n;
        int[] key0 = new int[n];
        int[] key = new int[n];
        int[] value = new int[n];
        for (int i = 0; i < key.length; ++i)
        {
            key0[i] = key[i] = r.nextInt(9); // Expect some duplicates
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
    @Test(timeout=TIMEOUT1)
    public void testSortDesc()
    {
        int n = longTest ? 29792349 : SHORTTEST;
        Random r = new Random(1);
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
     * E.g. 1 3 5 7 9 8 6 4 2 Median 1,9,2 = 2 Partition 3 5 7 9 8 6 4 2 1
     * Median 3,9,4 = 4 repeat
     */
    @Test(timeout=TIMEOUT2)
    public void testSortDesc2()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 6600001 : SHORTTEST;
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        for (int i = 0; i < n / 2; ++i)
        {
            key[i] = 2 * i + 1;
        }

        for (int i = n / 2; i < n; ++i)
        {
            key[i] = (n - i) * 2;
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
    
    /**
     * Test whether the sort function works with worst case data for median of 3
     * when swapping median with first
     * E.g. 1 9 8 7 6 5 4 3 2 Median 1,6,2 = 2 Partition 2 9 8 7 6 5 4 3 1 
     * swap Partition 3 9 8 7 6 5 4 2 1
     * Median 3,7,4 = 4 repeat
     */
    @Test(timeout=TIMEOUT2)
    public void testSortDesc3()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 5200001 : SHORTTEST;
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        key[0] = 0;
        for (int i = 1; i < n; ++i)
        {
            key[i] = (n - i);
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

    /**
     * Test whether the sort function works with worst case data for median of 3
     * when swapping median with last
     * E.g. 8 7 6 5 4 3 2 1 9 Median 8,4,9 = 8 Partition 9 7 6 5 4 3 2 1 8 
     * swap Partition 9 8 6 5 4 3 2 1 7
     * Median 6,3,7 = 6 repeat
     */
    @Test(timeout=TIMEOUT2)
    public void testSortDesc4()
    {
        // > 5000000 so that Quicksort is used
        int n = longTest ? 5200001 : SHORTTEST;
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        for (int i = 0; i < n - 1; ++i)
        {
            key[i] = (n - 1 - i);
        }
        key[n - 1] = n - 1;
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
    
    /**
     * Test whether the descending sort function works with random data with duplicates
     */
    @Test(timeout=TIMEOUT2)
    public void testSortDesc5()
    {
        int n = longTest ? 29792349 : SHORTTEST;
        Random r = new Random(1);
        long[] key0 = new long[n];
        long[] key = new long[n];
        int[] value = new int[n];
        for (int i = 0; i < key.length; ++i)
        {
            key0[i] = key[i] = r.nextInt(15) * 0x82345679L * 0 + r.nextInt(17);
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
