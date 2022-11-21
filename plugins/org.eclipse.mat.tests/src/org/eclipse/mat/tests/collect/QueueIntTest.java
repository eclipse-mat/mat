/*******************************************************************************
 * Copyright (c) 2010, 2022 IBM Corporation
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import org.eclipse.mat.collect.QueueInt;
import org.eclipse.mat.collect.IteratorInt;
import org.junit.Test;


public class QueueIntTest
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
    public void testQueueInt0() {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            int t = 0;
            QueueInt ss = new QueueInt(r.nextInt(INITIAL_SIZE));
            for (int j = 0; j < KEYS; ++j) {
                int v = r.nextInt(KEYS);
                ss.put(v);
                t += 1;
            }
            assertTrue("At least one item should have been added", t > 0); //$NON-NLS-1$
            assertEquals("Added items should equal size", t, ss.size()); //$NON-NLS-1$
        }
    }
    
    /**
     * Check that set contains everything it says it has
     */
    @Test
    public void testQueueInt1()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testOneQueueInt(r);
        }
    }

    private void testOneQueueInt(Random r)
    {
        QueueInt ss = new QueueInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.put(r.nextInt(KEYS) + 1);
        }
        int n = ss.size();
        for (int i = 0; i < n; ++i) 
        {
            assertThat("every entry should be in the iterator", ss.get(), greaterThan(0)); //$NON-NLS-1$
        }
    }

    /**
     * Check the number of contained items is the size
     */
    @Test
    public void testQueueInt2()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testTwoQueueInt(r);
        }
    }

    private void testTwoQueueInt(Random r)
    {
        QueueInt ss = new QueueInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.put(r.nextInt(KEYS) + 1);
        }
        int t = 0;
        for (int k = 0; k < KEYS; ++k) {
            t += ss.get() > 0 ? 1 : 0;
        }
        assertEquals("contained items should equals the size", KEYS, t); //$NON-NLS-1$
    }

    /**
     * Check add works as expected
     */
    @Test
    public void testQueueInt4()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testFourQueueInt(r);
        }
    }

    private void testFourQueueInt(Random r)
    {
        QueueInt ss = new QueueInt(r.nextInt(INITIAL_SIZE));
        ss.put(23);
        assertThat(ss.get(), equalTo(23));
        for (int j = 0; j < KEYS; ++j) {
            ss.put(r.nextInt(KEYS) + 1);
        }
        for (int k = 0; k < KEYS; ++k) {
            assertThat(ss.get(), greaterThan(0));
        }
    }

    /**
     * Check performance is as expected.
     */
    @Test
    public void testQueueInt5()
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
     * Test performance of QueueInt
     * @param n number of entries
     * @param m number of clumps
     * @param c number of times to repeat
     * @return time in milliseconds
     */
    private long perf(int n, int m, int c)
    {
        QueueInt ss = new QueueInt(INITIAL_SIZE);
        long then = System.currentTimeMillis();
        for (int i = 0; i < n / m; ++i)
        {
            for (int j = 0; j < m; ++j)
            {
                int key = i + n * 2 * j;
                ss.put(key);
            }
        }
        int cc = 0;
        for (int j = 0; j < c; ++j) {
            for (int i = 0; i < n; ++i) {
                int key = j * n + i;
                if (key >= n / m * m)
                    break;
                if (ss.get() >= 0)
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
    public void testQueueInt6() throws ClassNotFoundException, IOException
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testSixQueueInt(r);
        }
    }

    private void testSixQueueInt(Random r) throws IOException, ClassNotFoundException
    {
        QueueInt ss = new QueueInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.put(r.nextInt());
        }
        byte b[];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);)
        {
            oos.writeInt(ss.size());
            while (ss.size() > 0)
            {
                oos.writeInt(ss.get());
            }
            oos.flush();
            b = baos.toByteArray();
        }
        QueueInt ss2;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(b);
                        ObjectInputStream ois = new ObjectInputStream(bais);)
        {
            int n = ois.readInt();
            ss2 = new QueueInt(INITIAL_SIZE);
            for (int i = 0; i < n; ++i)
            {
                ss2.put(ois.readInt());
            }
        }
        assertThat(ss2.size(), equalTo(KEYS));
    }

    /**
     * Test huge sizes for overflow problems.
     */
    @Test
    public void testQueueInt7() {
        int s1 = HUGE_SIZE;
        QueueInt huge = new QueueInt(s1);
        assertThat(huge.size(), equalTo(0));
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.put(i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
    }
}
