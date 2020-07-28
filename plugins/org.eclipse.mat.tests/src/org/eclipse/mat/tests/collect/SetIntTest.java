/*******************************************************************************
 * Copyright (c) 2010, 2020 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.SetInt;
import org.junit.Ignore;
import org.junit.Test;


public class SetIntTest
{
    /** Needs huge heap, tests arithmetic overflow */
    private static final int HUGE_SIZE_BIG = Integer.MAX_VALUE / 100 * 76;
    private static final int HUGE_SIZE_SMALL = Integer.MAX_VALUE / 500 * 1;
    private static final boolean USE_HUGE = false;
    private static final int HUGE_SIZE = USE_HUGE ? HUGE_SIZE_BIG : HUGE_SIZE_SMALL;
    private static final int KEYS = 3000;
    private static final int INITIAL_SIZE = 30;
    private static final int COUNT = 10000;

    /**
     * Basic test - no unexpected ArrayIndexOutOfBoundsException
     */
    @Test
    public void testSetInt0() {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            int t = 0;
            SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
            for (int j = 0; j < KEYS; ++j) {
                int v = r.nextInt(KEYS);
                t += ss.add(v) ? 1 : 0;
            }
            assertTrue("At least one item should have been added", t > 0); //$NON-NLS-1$
            assertEquals("Added items should equal size", t, ss.size()); //$NON-NLS-1$
        }
    }
    
    /**
     * Check that set contains everything it says it has
     */
    @Test
    public void testSetInt1()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testOneSetInt(r);
        }
    }

    private void testOneSetInt(Random r)
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        for (IteratorInt ii = ss.iterator(); ii.hasNext(); ){
            assertTrue("every key should be contained", ss.contains(ii.next())); //$NON-NLS-1$
        }
    }

    /**
     * Check the number of contained items is the size
     */
    @Test
    public void testSetInt2()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testTwoSetInt(r);
        }
    }

    private void testTwoSetInt(Random r)
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        int t = 0;
        for (int k = 0; k < KEYS; ++k) {
            t += ss.contains(k) ? 1 : 0;
        }
        assertEquals("contained items should equals the size", ss.size(), t); //$NON-NLS-1$
    }
    

    /**
     * Check remove works as expected
     */
    @Test
    public void testSetInt3()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testThreeSetInt(r);
        }
    }

    private void testThreeSetInt(Random r)
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        for (int k = 0; k < KEYS; ++k) {
            boolean b1 = ss.contains(k);
            boolean b2 = ss.remove(k);
            assertEquals("remove should only succeed if key is contained", b1, b2); //$NON-NLS-1$
            assertFalse("after a remove the key should not be contained", ss.contains(k)); //$NON-NLS-1$
        }
    }

    /**
     * Check remove works as expected and that remaining entries
     * are not affected.
     */
    @Test
    public void testSetInt3a()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT / 5; ++i) {
            testThreeSetIntA(r);
        }
    }

    private void testThreeSetIntA(Random r)
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        // Add some initial entries so the targets might be chained
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt());
        }
        // The targets for removal
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        // Add some final entries possibly chained from the targets
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt());
        }
        int all[] = ss.toArray();
        for (int k = 0; k < KEYS; ++k) {
            boolean b1 = ss.contains(k);
            boolean b2 = ss.remove(k);
            assertEquals("remove should only succeed if key is contained", b1, b2); //$NON-NLS-1$
            assertFalse("after a remove the key should not be contained", ss.contains(k)); //$NON-NLS-1$
        }
        for (int k : all)
        {
            boolean b1 = !ss.contains(k);
            boolean b2 = k >= 0 && k < KEYS;
            assertEquals("Only entries from 0..KEYS-1 should have been removed for entry "+k, b1, b2); //$NON-NLS-1$
        }
    }

    /**
     * Check add works as expected
     */
    @Test
    public void testSetInt4()
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testFourSetInt(r);
        }
    }

    private void testFourSetInt(Random r)
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt(KEYS));
        }
        for (int k = 0; k < KEYS; ++k) {
            boolean b1 = ss.contains(k);
            boolean b2 = ss.add(k);
            assertEquals("add should not succeed if the key is already there", b1, !b2); //$NON-NLS-1$
            assertTrue("key should be contained after an add", ss.contains(k)); //$NON-NLS-1$
        }
    }

    /**
     * Check performance is as expected.
     */
    @Test
    @Ignore
    public void testSetInt5()
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
     * Test performance of SetInt
     * @param n number of entries
     * @param m number of clumps
     * @param c number of times to repeat
     * @return time in milliseconds
     */
    private long perf(int n, int m, int c)
    {
        SetInt ss = new SetInt();
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
                int key = i;
                if (ss.contains(key))
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
    public void testSetInt6() throws ClassNotFoundException, IOException
    {
        Random r = new Random(1);
        for (int i = 0; i < COUNT; ++i) {
            testSixSetInt(r);
        }
    }

    private void testSixSetInt(Random r) throws IOException, ClassNotFoundException
    {
        SetInt ss = new SetInt(r.nextInt(INITIAL_SIZE));
        for (int j = 0; j < KEYS; ++j) {
            ss.add(r.nextInt());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(ss);
        oos.close();
        baos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        SetInt ss2 = (SetInt)ois.readObject();
        ois.close();
        bais.close();
        for (IteratorInt ii = ss.iterator(); ii.hasNext(); ){
            assertTrue("every key should be contained in the deserialized version", ss2.contains(ii.next())); //$NON-NLS-1$
        }
        for (IteratorInt ii = ss2.iterator(); ii.hasNext(); ){
            assertTrue("every deserialized key should be contained", ss.contains(ii.next())); //$NON-NLS-1$
        }
    }

    /**
     * Test huge sizes for overflow problems.
     */
    @Test
    public void testSetInt7() {
        int s1 = HUGE_SIZE;
        SetInt huge = new SetInt(s1);
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.add(i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
        for (int i = 0; i < s2; ++i) {
            boolean removed = huge.remove(i * 2);
            assertTrue("Should have removed " + (i * 2), removed); //$NON-NLS-1$
        }
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
    }
}
