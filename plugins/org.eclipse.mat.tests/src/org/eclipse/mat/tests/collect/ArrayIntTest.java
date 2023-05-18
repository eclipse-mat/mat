/*******************************************************************************
 * Copyright (c) 2010, 2023 IBM Corporation
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.IteratorInt;
import org.junit.Test;


public class ArrayIntTest
{
    /** Needs huge heap, tests arithmetic overflow */
    private static final int HUGE_SIZE_BIG = Integer.MAX_VALUE / 100 * 76;
    private static final int HUGE_SIZE_SMALL = Integer.MAX_VALUE / 500 * 1;
    private static final boolean USE_HUGE = false;
    private static final int HUGE_SIZE = USE_HUGE ? HUGE_SIZE_BIG : HUGE_SIZE_SMALL;
    private static final int KEYS = 3000;
    private static final int INITIAL_SIZE = 30;
    private static final int COUNT = 1000;

    /**
     * Basic test - no unexpected ArrayIndexOutOfBoundsException
     */
    @Test
    public void testArrayInt0() {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            int t = 0;
            ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
            for (int j = 0; j < KEYS; ++j) {
                int v = r.nextInt(KEYS);
                ss.add(v);
                t += 1;
            }
            assertThat("At least one item should have been added", t, greaterThan(0)); //$NON-NLS-1$
            assertThat("Size should equal added items", ss.size(), equalTo(t)); //$NON-NLS-1$
            assertFalse("Not empty", ss.isEmpty());
        }
    }

    /**
     * Check that set contains everything it says it has
     */
    @Test
    public void testArrayInt1()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testOneArrayInt(r);
        }
    }

    private void testOneArrayInt(Random r)
    {
        ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
            ss.set(j, r.nextInt(KEYS));
        }
        int i = 0;
        for (IteratorInt ii = ss.iterator(); ii.hasNext(); ){
            assertThat("the iterator should match each entry", ii.next(), equalTo(ss.get(i++))); //$NON-NLS-1$
        }
    }

    /**
     * Check the number of contained items is the size
     */
    @Test
    public void testArrayInt2()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testTwoArrayInt(r);
        }
    }

    private void testTwoArrayInt(Random r)
    {
        ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS) + 1);
        }
        int t = 0;
        for (int k = 0; k < KEYS; ++k) {
            t += ss.get(k) > 0 ? 1 : 0;
        }
        assertThat("contained items should equals the size", t, equalTo(ss.size())); //$NON-NLS-1$
    }

    /**
     * Check that array contains everything it says it has
     */
    @Test
    public void testArrayInt3()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testThreeArrayInt(r);
        }
    }

    private void testThreeArrayInt(Random r)
    {
        ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        ArrayInt ss2 = new ArrayInt(ss);
        int i = 0;
        for (IteratorInt ii = ss.iterator(); ii.hasNext(); ){
            assertThat("the iterator should match each entry", ii.next(), equalTo(ss2.get(i++))); //$NON-NLS-1$
        }
    }

    /**
     * Check add works as expected
     */
    @Test
    public void testArrayInt4()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testFourArrayInt(r);
        }
    }

    private void testFourArrayInt(Random r)
    {
        ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS) + 1);
        }
        for (int k = 0; k < KEYS; ++k) {
            assertThat(ss.get(k), greaterThan(0));
        }
    }

    /**
     * Check performance is as expected.
     */
    @Test
    public void testArrayInt5()
    {
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        int worstj = 0, bestj = 0;
        for (int j = 1; j <= 6; ++j) {
            long time = perf(31545, j, 500);
            if (time < best) {
                best = time;
                bestj = j;
            }
            if (time > worst) {
                worst = time;
                worstj = j;
            }
        }
        assertThat("Worst more for group " + worstj + " than 10 times best for group " + bestj, worst, lessThanOrEqualTo(Math.max(100, 10 * best))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test performance of ArrayInt
     * @param n number of entries
     * @param m number of clumps
     * @param c number of times to repeat
     * @return time in milliseconds
     */
    private long perf(int n, int m, int c)
    {
        ArrayInt ss = new ArrayInt();
        long then = System.currentTimeMillis();
        for (int i = 0; i < n / m; ++i)
        {
            for (int j = 0; j < m; ++j)
            {
                int key = i + n * 2 * j;
                ss.add(key);
            }
        }
        int cc = 0;
        for (int j = 0; j < c; ++j) {
            for (int i = 0; i < n; ++i) {
                int key = j * n + i;
                if (key >= n / m * m)
                    break;
                if (ss.get(key) >= 0)
                    ++cc;
            }
        }
        long now = System.currentTimeMillis();
        return (now - then) + ((cc == n) ? 0 : 1);
    }

    /**
     * Check serialize/deserialize works as expected
     * @throws IOException 
     * @throws ClassNotFoundException 
     */
    @Test
    public void testArrayInt6() throws ClassNotFoundException, IOException
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testSixArrayInt(r);
        }
    }

    private void testSixArrayInt(Random r) throws IOException, ClassNotFoundException
    {
        ArrayInt ss = new ArrayInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt());
        }
        byte b[];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);)
        {
            oos.writeObject(ss.toArray());
            oos.flush();
            b = baos.toByteArray();
        }
        ArrayInt ss2;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(b);
                        ObjectInputStream ois = new ObjectInputStream(bais);)
        {
            int a[] = (int[]) ois.readObject();
            ss2 = new ArrayInt(a);
        }
        int i = 0;
        for (IteratorInt ii = ss.iterator(); ii.hasNext(); ){
            assertThat("every key should be contained in the deserialized version", ii.next(), equalTo(ss2.get(i++))); //$NON-NLS-1$
        }
        i = 0;
        for (IteratorInt ii = ss2.iterator(); ii.hasNext(); ){
            assertThat("every deserialized key should be contained", ii.next(), equalTo(ss.get(i++))); //$NON-NLS-1$
        }
    }

    /**
     * Test huge sizes for overflow problems.
     */
    @Test
    public void testArrayInt7() {
        int s1 = HUGE_SIZE;
        ArrayInt huge = new ArrayInt(s1);
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.add(i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
    }
}
