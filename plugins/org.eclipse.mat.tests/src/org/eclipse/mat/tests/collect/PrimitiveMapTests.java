/*******************************************************************************
 * Copyright (c) 2008,2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - fix deprecated method
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapObjectLong;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.junit.Test;

public class PrimitiveMapTests
{
    /** Needs huge heap, tests arithmetic overflow */
    private static final int HUGE_SIZE_BIG = Integer.MAX_VALUE / 100 * 76;
    private static final int HUGE_SIZE_SMALL = Integer.MAX_VALUE / 1000 * 1;
    private static final boolean USE_HUGE = false;
    private static final int HUGE_SIZE = USE_HUGE ? HUGE_SIZE_BIG : HUGE_SIZE_SMALL;
    private static final int NUM_VALUES = 10000;
    private static final int NUM_VALUES2 = 31545;
    private static final int CAPACITY2 = 63096;
    private static final int PERF_COUNT = 2;
    private static final double PERF_FACTOR = 10.0;
    private static final double PERF_MIN_FOR_PROBLEM = 1000;

    // //////////////////////////////////////////////////////////////
    // HashMapIntLong
    // //////////////////////////////////////////////////////////////

    public void testIntLongMap(int num_values) throws ClassNotFoundException, IOException
    {
        Random r = getRandom(num_values);

        Integer[] keys = new Integer[num_values];
        Long[] values = new Long[num_values];

        for (int ii = 0; ii < num_values; ii++)
        {
            // make sure we have at least one duplicate
            if (ii == 11)
            {
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
            }
            else
            {
                keys[ii] = r.nextInt();
                values[ii] = r.nextLong();
            }
        }

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapIntLongBridge(new HashMapIntLong());
            }
        }.run();

        // needed to test alternative #getAllKeys() #getAllValues()
        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapIntLongBridge2(new HashMapIntLong());
            }
        }.run();

    }

    /**
     * Doesn't need to be random - just give a reproducible
     * sequence of scattered values.
     * @param seed for the sequence
     */
    private Random getRandom(int seed)
    {
        return new Random(seed);
    }

    @Test
    public void testIntLongMap() throws ClassNotFoundException, IOException
    {
        testIntLongMap(NUM_VALUES);
    }

    @Test
    public void testIntLongMapN() throws ClassNotFoundException, IOException
    {
        for (int i = 0; i < 100; ++i)
        {
            testIntLongMap(i);
        }
    }    

    @Test
    public void testIntLongMapPerf()
    {
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        int worstj = 0, bestj = 0;
        for (int j = 1; j <= 6; ++j) {
            long time = testIntLongMapPerf(NUM_VALUES2, j, PERF_COUNT);
            if (time < best)
            {
                best = time;
                bestj = j;
            }
            if (time > worst)
            {
                worst = time;
                worstj = j;
            }
        }
        assertThat("Worst for group " + worstj+" more than "+PERF_FACTOR+" times the best for group "+bestj, worst, lessThanOrEqualTo(expectedWorstTime(best))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Given a best time, what is the worse we can tolerate?
     * @param best
     * @return worst allowable time
     */
    private long expectedWorstTime(long best)
    {
        return (long)Math.max(PERF_FACTOR * best, PERF_MIN_FOR_PROBLEM);
    }

    public long testIntLongMapPerf(int n, int m, int c)
    {
        Random r = getRandom(n);

        Integer[] keys = new Integer[n / m * m];
        Long[] values = new Long[n / m * m];

        for (int ii = 0; ii < n / m; ii++)
        {
            for (int j = 0; j < m; ++j)
            {
                keys[ii * m + j] = ii + n * 2 * j;
                values[ii * m + j] = r.nextLong();
            }
        }

        long then = System.currentTimeMillis();
        for (int j = 0; j < c; ++j)
        {
            new TestStub<Integer, Long>(keys, values)
            {
                @Override
                protected Map<Integer, Long> createEmpty()
                {
                    return new MapIntLongBridge(new HashMapIntLong(CAPACITY2));
                }
            }.perfRun();
        }
        long now = System.currentTimeMillis();
        return now - then;
    }

    @Test
    public void testIntLongMapHuge()
    {
        int s1 = HUGE_SIZE;
        HashMapIntLong huge = new HashMapIntLong(s1);
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.put(i * 47, i * 2L);
        }
        assertThat(huge.size(), equalTo(s2));
        for (int i = 0; i < s2; ++i) {
            boolean removed = huge.remove(i * 47);
            assertNotNull(removed);
        }
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
    }

    static class MapIntLongBridge implements Map<Integer, Long>, Serializable
    {
        private static final long serialVersionUID = 1L;
        HashMapIntLong delegate;

        MapIntLongBridge(HashMapIntLong delegate)
        {
            this.delegate = delegate;
        }

        public void clear()
        {
            delegate.clear();
        }

        public boolean containsKey(Object key)
        {
            return delegate.containsKey((Integer) key);
        }

        public boolean containsValue(Object value)
        {
            long v = (Long) value;

            for (IteratorLong iter = delegate.values(); iter.hasNext();)
            {
                if (v == iter.next())
                    return true;
            }
            return false;
        }

        public Set<Entry<Integer, Long>> entrySet()
        {
            Set<Entry<Integer, Long>> answer = new HashSet<Entry<Integer, Long>>();
            for (Iterator<HashMapIntLong.Entry> iter = delegate.entries(); iter.hasNext();)
            {
                final HashMapIntLong.Entry entry = iter.next();
                answer.add(new Entry<Integer, Long>()
                {

                    public Integer getKey()
                    {
                        return entry.getKey();
                    }

                    public Long getValue()
                    {
                        return entry.getValue();
                    }

                    public Long setValue(Long value)
                    {
                        throw new UnsupportedOperationException();
                    }
                });
            }
            return answer;
        }

        public Long get(Object key)
        {
            return delegate.get((Integer) key);
        }

        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }

        public Set<Integer> keySet()
        {
            Set<Integer> answer = new HashSet<Integer>();
            for (IteratorInt iter = delegate.keys(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }

        public Long put(Integer key, Long value)
        {
            Long retValue = null;

            try
            {
                retValue = delegate.get(key);
            }
            catch (NoSuchElementException ignore)
            {}

            boolean replace = delegate.put(key, value);
            return replace ? retValue : null;
        }

        public void putAll(Map<? extends Integer, ? extends Long> t)
        {
            throw new UnsupportedOperationException();
        }

        public Long remove(Object key)
        {
            Long retValue = null;

            try
            {
                retValue = delegate.get((Integer) key);
            }
            catch (NoSuchElementException ignore)
            {}

            boolean removed = delegate.remove((Integer) key);
            return removed ? retValue : null;
        }

        public int size()
        {
            return delegate.size();
        }

        public Collection<Long> values()
        {
            List<Long> answer = new ArrayList<Long>();
            for (IteratorLong iter = delegate.values(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }
    }

    static class MapIntLongBridge2 extends MapIntLongBridge
    {
        private static final long serialVersionUID = 1L;

        public MapIntLongBridge2(HashMapIntLong delegate)
        {
            super(delegate);
        }

        public Set<Integer> keySet()
        {
            Set<Integer> answer = new HashSet<Integer>();
            int[] keys = delegate.getAllKeys();
            for (int ii = 0; ii < keys.length; ii++)
                answer.add(keys[ii]);
            return answer;
        }

        public Collection<Long> values()
        {
            List<Long> answer = new ArrayList<Long>();
            long[] values = delegate.getAllValues();
            for (int ii = 0; ii < values.length; ii++)
                answer.add(values[ii]);
            return answer;
        }
    }

    // //////////////////////////////////////////////////////////////
    // HashMapIntObject
    // //////////////////////////////////////////////////////////////

    public void testIntObjectMap(int num_values) throws ClassNotFoundException, IOException
    {
        Random r = getRandom(num_values);

        Integer[] keys = new Integer[num_values];
        Long[] values = new Long[num_values];

        for (int ii = 0; ii < num_values; ii++)
        {
            // make sure we have at least one duplicate
            if (ii == 11)
            {
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
            }
            else
            {
                keys[ii] = r.nextInt();
                values[ii] = r.nextLong();
            }
        }

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapIntObjectBridge<Long>(new HashMapIntObject<Long>());
            }
        }.run();

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapIntObjectBridge2<Long>(new HashMapIntObject<Long>());
            }
        }.run();

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapIntObjectBridge3<Long>(new HashMapIntObject<Long>());
            }
        }.run();

    }

    @Test
    public void testIntObjectMap() throws ClassNotFoundException, IOException
    {
        testIntObjectMap(NUM_VALUES);
    }

    @Test
    public void testIntObjectMapN() throws ClassNotFoundException, IOException
    {
        for (int i = 0; i < 100; ++i)
        {
            testIntObjectMap(i);
        }
    } 

    @Test
    public void testIntObjectMapPerf()
    {
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        int worstj = 0, bestj = 0;
        for (int j = 1; j <= 6; ++j) {
            long time = testIntObjectMapPerf(NUM_VALUES2, j, PERF_COUNT);
            if (time < best)
            {
                best = time;
                bestj = j;
            }
            if (time > worst)
            {
                worst = time;
                worstj = j;
            }
        }
        assertThat("Worst for group " + worstj+" more than "+PERF_FACTOR+" times the best for group "+bestj, worst, lessThanOrEqualTo(expectedWorstTime(best))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public long testIntObjectMapPerf(int n, int m, int c)
    {
        Random r = getRandom(n);

        Integer[] keys = new Integer[n / m * m];
        Long[] values = new Long[n / m * m];

        for (int ii = 0; ii < n / m; ii++)
        {
            for (int j = 0; j < m; ++j)
            {
                keys[ii * m + j] = ii + n * 2 * j;
                values[ii * m + j] = r.nextLong();
            }
        }

        long then = System.currentTimeMillis();
        for (int j = 0; j < c; ++j)
        {
            new TestStub<Integer, Long>(keys, values)
            {
                @Override
                protected Map<Integer, Long> createEmpty()
                {
                    return new MapIntObjectBridge<Long>(new HashMapIntObject<Long>(CAPACITY2));
                }
            }.perfRun();
        }
        long now = System.currentTimeMillis();
        return now - then;
    }

    @Test
    public void testIntObjectMapHuge()
    {
        int s1 = HUGE_SIZE;
        HashMapIntObject<Integer> huge = new HashMapIntObject<Integer>(s1);
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.put(i * 47, i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
        for (int i = 0; i < s2; ++i) {
            Integer removed = huge.remove(i * 47);
            assertNotNull(removed);
            assertThat("Should have removed " + (i * 47L), removed, equalTo(i * 2)); //$NON-NLS-1$
        }
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
    }

    // //////////////////////////////////////////////////////////////
    // bridges
    // //////////////////////////////////////////////////////////////

    static class MapIntObjectBridge<V> implements Map<Integer, V>, Serializable
    {
        private static final long serialVersionUID = 1L;
        HashMapIntObject<V> delegate;

        MapIntObjectBridge(HashMapIntObject<V> delegate)
        {
            this.delegate = delegate;
        }

        public void clear()
        {
            delegate.clear();
        }

        public boolean containsKey(Object key)
        {
            return delegate.containsKey((Integer) key);
        }

        public boolean containsValue(Object value)
        {
            for (Iterator<V> iter = delegate.values(); iter.hasNext();)
            {
                if (value.equals(iter.next()))
                    return true;
            }
            return false;
        }

        public Set<Entry<Integer, V>> entrySet()
        {
            Set<Entry<Integer, V>> answer = new HashSet<Entry<Integer, V>>();
            for (Iterator<HashMapIntObject.Entry<V>> iter = delegate.entries(); iter.hasNext();)
            {
                final HashMapIntObject.Entry<V> entry = iter.next();
                answer.add(new Entry<Integer, V>()
                {

                    public Integer getKey()
                    {
                        return entry.getKey();
                    }

                    public V getValue()
                    {
                        return entry.getValue();
                    }

                    public V setValue(V value)
                    {
                        throw new UnsupportedOperationException();
                    }
                });
            }
            return answer;
        }

        public V get(Object key)
        {
            return delegate.get((Integer) key);
        }

        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }

        public Set<Integer> keySet()
        {
            Set<Integer> answer = new HashSet<Integer>();
            for (IteratorInt iter = delegate.keys(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }

        public V put(Integer key, V value)
        {
            return delegate.put(key, value);
        }

        public void putAll(Map<? extends Integer, ? extends V> t)
        {
            throw new UnsupportedOperationException();
        }

        public V remove(Object key)
        {
            return delegate.remove((Integer) key);
        }

        public int size()
        {
            return delegate.size();
        }

        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            for (Iterator<V> iter = delegate.values(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }
    }

    static class MapIntObjectBridge2<V> extends MapIntObjectBridge<V>
    {
        private static final long serialVersionUID = 1L;

        public MapIntObjectBridge2(HashMapIntObject<V> delegate)
        {
            super(delegate);
        }

        public Set<Integer> keySet()
        {
            Set<Integer> answer = new HashSet<Integer>();
            int[] keys = delegate.getAllKeys();
            for (int ii = 0; ii < keys.length; ii++)
                answer.add(keys[ii]);
            return answer;
        }

        @SuppressWarnings("unchecked")
        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            Object[] values = delegate.getAllValues();
            for (int ii = 0; ii < values.length; ii++)
                answer.add((V) values[ii]);
            return answer;
        }
    }

    static class MapIntObjectBridge3<V> extends MapIntObjectBridge<V>
    {
        private static final long serialVersionUID = 1L;

        public MapIntObjectBridge3(HashMapIntObject<V> delegate)
        {
            super(delegate);
        }

        @SuppressWarnings("unchecked")
        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            Object[] values = delegate.getAllValues(new Object[delegate.size()]);
            for (int ii = 0; ii < values.length; ii++)
                answer.add((V) values[ii]);
            return answer;
        }
    }

    // //////////////////////////////////////////////////////////////
    // HashMapLongObject
    // //////////////////////////////////////////////////////////////

    public void testLongObjectMap(int num_values) throws ClassNotFoundException, IOException
    {
        Random r = getRandom(num_values);

        Long[] keys = new Long[num_values];
        Integer[] values = new Integer[num_values];

        for (int ii = 0; ii < num_values; ii++)
        {
            // make sure we have at least one duplicate
            if (ii == 11)
            {
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
            }
            else
            {
                keys[ii] = r.nextLong();
                values[ii] = r.nextInt();
            }
        }

        new TestStub<Long, Integer>(keys, values)
        {
            @Override
            protected Map<Long, Integer> createEmpty()
            {
                return new MapLongObjectBridge<Integer>(new HashMapLongObject<Integer>());
            }
        }.run();

        new TestStub<Long, Integer>(keys, values)
        {
            @Override
            protected Map<Long, Integer> createEmpty()
            {
                return new MapLongObjectBridge2<Integer>(new HashMapLongObject<Integer>());
            }
        }.run();

        new TestStub<Long, Integer>(keys, values)
        {
            @Override
            protected Map<Long, Integer> createEmpty()
            {
                return new MapLongObjectBridge3<Integer>(new HashMapLongObject<Integer>());
            }
        }.run();
    }

    @Test
    public void testLongObjectMap() throws ClassNotFoundException, IOException
    {
        testLongObjectMap(NUM_VALUES);
    }

    @Test
    public void testLongObjectMapN() throws ClassNotFoundException, IOException
    {
        for (int i = 0; i < 100; i++)
        {
            testLongObjectMap(i);
        }
    }

    @Test
    public void testLongObjectMapPerf()
    {
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        int worstj = 0, bestj = 0;
        for (int j = 1; j <= 6; ++j) {
            long time = testLongObjectMapPerf(NUM_VALUES2, j, PERF_COUNT);
            if (time < best)
            {
                best = time;
                bestj = j;
            }
            if (time > worst)
            {
                worst = time;
                worstj = j;
            }
        }
        assertThat("Worst for group " + worstj+" more than "+PERF_FACTOR+" times the best for group "+bestj, worst, lessThanOrEqualTo(expectedWorstTime(best))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public long testLongObjectMapPerf(int n, int m, int c)
    {
        Random r = getRandom(n);

        Long[] keys = new Long[n / m * m];
        Integer[] values = new Integer[n / m * m];

        for (int ii = 0; ii < n / m; ii++)
        {
            for (int j = 0; j < m; ++j)
            {
                keys[ii * m + j] = ii + n * 2L * j;
                values[ii * m + j] = r.nextInt();
            }
        }

        long then = System.currentTimeMillis();
        for (int j = 0; j < c; ++j)
        {
            new TestStub<Long, Integer>(keys, values)
            {
                @Override
                protected Map<Long, Integer> createEmpty()
                {
                    return new MapLongObjectBridge<Integer>(new HashMapLongObject<Integer>(CAPACITY2));
                }
            }.perfRun();
        }
        long now = System.currentTimeMillis();
        return now - then;
    }

    @Test
    public void testLongObjectMapHuge()
    {
        int s1 = HUGE_SIZE;
        HashMapLongObject<Integer> huge = new HashMapLongObject<Integer>(s1);
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.put(i * 47L, i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
        for (int i = 0; i < s2; ++i) {
            Integer removed = huge.remove(i * 47L);
            assertNotNull(removed);
            assertEquals("Should have removed " + (i * 47L), (int)removed, i * 2); //$NON-NLS-1$
        }
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
    }

    static class MapLongObjectBridge<V> implements Map<Long, V>, Serializable
    {
        private static final long serialVersionUID = 1L;
        HashMapLongObject<V> delegate;

        MapLongObjectBridge(HashMapLongObject<V> delegate)
        {
            this.delegate = delegate;
        }

        public void clear()
        {
            delegate.clear();
        }

        public boolean containsKey(Object key)
        {
            return delegate.containsKey((Long) key);
        }

        public boolean containsValue(Object value)
        {
            for (Iterator<V> iter = delegate.values(); iter.hasNext();)
            {
                if (value.equals(iter.next()))
                    return true;
            }
            return false;
        }

        public Set<Entry<Long, V>> entrySet()
        {
            Set<Entry<Long, V>> answer = new HashSet<Entry<Long, V>>();
            for (Iterator<HashMapLongObject.Entry<V>> iter = delegate.entries(); iter.hasNext();)
            {
                final HashMapLongObject.Entry<V> entry = iter.next();
                answer.add(new Entry<Long, V>()
                {

                    public Long getKey()
                    {
                        return entry.getKey();
                    }

                    public V getValue()
                    {
                        return entry.getValue();
                    }

                    public V setValue(V value)
                    {
                        throw new UnsupportedOperationException();
                    }
                });
            }
            return answer;
        }

        public V get(Object key)
        {
            return delegate.get((Long) key);
        }

        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }

        public Set<Long> keySet()
        {
            Set<Long> answer = new HashSet<Long>();
            for (IteratorLong iter = delegate.keys(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }

        public V put(Long key, V value)
        {
            return delegate.put(key, value);
        }

        public void putAll(Map<? extends Long, ? extends V> t)
        {
            throw new UnsupportedOperationException();
        }

        public V remove(Object key)
        {
            return delegate.remove((Long) key);
        }

        public int size()
        {
            return delegate.size();
        }

        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            for (Iterator<V> iter = delegate.values(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }
    }

    static class MapLongObjectBridge2<V> extends MapLongObjectBridge<V>
    {
        private static final long serialVersionUID = 1L;

        public MapLongObjectBridge2(HashMapLongObject<V> delegate)
        {
            super(delegate);
        }

        public Set<Long> keySet()
        {
            Set<Long> answer = new HashSet<Long>();
            long[] keys = delegate.getAllKeys();
            for (int ii = 0; ii < keys.length; ii++)
                answer.add(keys[ii]);
            return answer;
        }

        @SuppressWarnings("unchecked")
        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            Object[] values = delegate.getAllValues();
            for (int ii = 0; ii < values.length; ii++)
                answer.add((V) values[ii]);
            return answer;
        }
    }

    static class MapLongObjectBridge3<V> extends MapLongObjectBridge<V>
    {
        private static final long serialVersionUID = 1L;

        public MapLongObjectBridge3(HashMapLongObject<V> delegate)
        {
            super(delegate);
        }

        @SuppressWarnings("unchecked")
        public Collection<V> values()
        {
            List<V> answer = new ArrayList<V>();
            Object[] values = delegate.getAllValues(new Object[delegate.size()]);
            for (int ii = 0; ii < values.length; ii++)
                answer.add((V) values[ii]);
            return answer;
        }
    }

    // //////////////////////////////////////////////////////////////
    // HashMapObjectLong
    // //////////////////////////////////////////////////////////////

    public void testObjectLongMap(int num_values) throws ClassNotFoundException, IOException
    {
        Random r = getRandom(num_values);

        Integer[] keys = new Integer[num_values];
        Long[] values = new Long[num_values];

        for (int ii = 0; ii < num_values; ii++)
        {
            // make sure we have at least one duplicate
            if (ii == 11)
            {
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
            }
            // make sure we have at least one via equals
            else if (ii == 21)
            {
                // Deliberately construct new object
                keys[ii] = cloneInteger(keys[ii - 1]);
                // Deliberately construct new object
                values[ii] = cloneLong(values[ii - 1]);
            }
            else
            {
                keys[ii] = r.nextInt();
                values[ii] = r.nextLong();
            }
        }

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapObjectLongBridge<Integer>(new HashMapObjectLong<Integer>());
            }
        }.run();

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapObjectLongBridge2<Integer>(new HashMapObjectLong<Integer>());
            }
        }.run();

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapObjectLongBridge3<Integer>(new HashMapObjectLong<Integer>());
            }
        }.run();

        new TestStub<Integer, Long>(keys, values)
        {
            @Override
            protected Map<Integer, Long> createEmpty()
            {
                return new MapObjectLongBridge4<Integer>(new HashMapObjectLong<Integer>());
            }
        }.run();
    }

    @Test
    public void testObjectLongMap() throws ClassNotFoundException, IOException
    {
        testObjectLongMap(NUM_VALUES);
    }

    @Test
    public void testObjectLongMapN() throws ClassNotFoundException, IOException
    {
        for (int i = 0; i < 100; ++i)
        {
            testObjectLongMap(i);
        }
    }

    /**
     * Attempt to get a new Integer which is equal to the
     * input but a different object.
     * Avoids using the deprecated new Integer()
     * @param v the Integer to clone
     * @return probably a different Integer but equal to v
     */
    static Integer cloneInteger(Integer v)
    {
        Integer v1 = Integer.valueOf(v);
        if (v1 != v)
            return v1;
        // Attempt to clear any LRU cache
        int z = 0;
        for (int i = 0; i < 500; ++i)
        {
            z += Integer.valueOf(i * 0x1234567);
        }
        return Integer.valueOf(v + z * 0);
    }

    /**
     * Attempt to get a new Long which is equal to the
     * input but a different object.
     * Avoids using the deprecated new Long()
     * @param v the Long to clone
     * @return probably a different Long but equal to v
     */
    static Long cloneLong(Long v)
    {
        Long v1 = Long.valueOf(v);
        if (v1 != v)
            return v1;
        // Attempt to clear any LRU cache
        long z = 0;
        for (int i = 0; i < 500; ++i)
        {
            z += Integer.valueOf(i * 0x1234567);
        }
        return Long.valueOf(v + z * 0);
    }

    @Test
    public void testObjectLongMapPerf()
    {
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        int worstj = 0, bestj= 0;
        for (int j = 1; j <= 6; ++j) {
            long time = testObjectLongMapPerf(NUM_VALUES2, j, PERF_COUNT);
            if (time < best)
            {
                best = time;
                bestj = j;
            }
            if (time > worst)
            {
                worst = time;
                worstj = j;
            }
        }
        assertThat("Worst for group " + worstj+" more than "+PERF_FACTOR+" times the best for group "+bestj, worst, lessThanOrEqualTo(expectedWorstTime(best))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public long testObjectLongMapPerf(int n, int m, int c)
    {
        Random r = getRandom(n);

        Integer[] keys = new Integer[n / m * m];
        Long[] values = new Long[n / m * m];

        for (int ii = 0; ii < n / m; ii++)
        {
            for (int j = 0; j < m; ++j)
            {
                keys[ii * m + j] = ii + n * 2 * j;
                values[ii * m + j] = r.nextLong();
            }
        }

        long then = System.currentTimeMillis();
        for (int j = 0; j < c; ++j)
        {
            new TestStub<Integer, Long>(keys, values)
            {
                @Override
                protected Map<Integer, Long> createEmpty()
                {
                    return new MapObjectLongBridge<Integer>(new HashMapObjectLong<Integer>(CAPACITY2));
                }
            }.perfRun();
        }
        long now = System.currentTimeMillis();
        return now - then;
    }

    @Test
    public void testObjectLongMapHuge()
    {
        int s1 = HUGE_SIZE;
        HashMapObjectLong<Integer> huge = new HashMapObjectLong<Integer>(s1);
        int s2 = s1 / 3 * 2;
        for (int i = 0; i < s2; ++i) {
            huge.put(i * 47, i * 2);
        }
        assertThat(huge.size(), equalTo(s2));
        for (int i = 0; i < s2; ++i) {
            boolean removed = huge.remove(i * 47);
            assertNotNull(removed);
        }
        assertThat(huge.size(), equalTo(0));
        assertTrue(huge.isEmpty());
    }

    static class MapObjectLongBridge<K> implements Map<K, Long>, Serializable
    {
        private static final long serialVersionUID = 1L;
        HashMapObjectLong<K> delegate;

        MapObjectLongBridge(HashMapObjectLong<K> delegate)
        {
            this.delegate = delegate;
        }

        public void clear()
        {
            delegate.clear();
        }

        @SuppressWarnings("unchecked")
        public boolean containsKey(Object key)
        {
            return delegate.containsKey((K) key);
        }

        public boolean containsValue(Object value)
        {
            long v = (Long) value;

            for (IteratorLong iter = delegate.values(); iter.hasNext();)
            {
                if (v == iter.next())
                    return true;
            }
            return false;
        }

        public Set<Entry<K, Long>> entrySet()
        {
            Set<Entry<K, Long>> answer = new HashSet<Entry<K, Long>>();
            for (Iterator<HashMapObjectLong.Entry<K>> iter = delegate.entries(); iter.hasNext();)
            {
                final HashMapObjectLong.Entry<K> entry = iter.next();
                answer.add(new Entry<K, Long>()
                {

                    public K getKey()
                    {
                        return entry.getKey();
                    }

                    public Long getValue()
                    {
                        return entry.getValue();
                    }

                    public Long setValue(Long value)
                    {
                        throw new UnsupportedOperationException();
                    }
                });
            }
            return answer;
        }

        @SuppressWarnings("unchecked")
        public Long get(Object key)
        {
            return delegate.get((K) key);
        }

        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }

        public Set<K> keySet()
        {
            Set<K> answer = new HashSet<K>();
            for (Iterator<K> iter = delegate.keys(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }

        public Long put(K key, Long value)
        {
            Long retValue = null;

            try
            {
                retValue = delegate.get(key);
            }
            catch (NoSuchElementException ignore)
            {}

            boolean replace = delegate.put(key, value);
            return replace ? retValue : null;
        }

        public void putAll(Map<? extends K, ? extends Long> t)
        {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public Long remove(Object key)
        {
            Long retValue = null;

            try
            {
                retValue = delegate.get((K) key);
            }
            catch (NoSuchElementException ignore)
            {}

            boolean removed = delegate.remove((K) key);
            return removed ? retValue : null;
        }

        public int size()
        {
            return delegate.size();
        }

        public Collection<Long> values()
        {
            List<Long> answer = new ArrayList<Long>();
            for (IteratorLong iter = delegate.values(); iter.hasNext();)
                answer.add(iter.next());
            return answer;
        }
    }

    static class MapObjectLongBridge2<K> extends MapObjectLongBridge<K>
    {
        private static final long serialVersionUID = 1L;

        public MapObjectLongBridge2(HashMapObjectLong<K> delegate)
        {
            super(delegate);
        }

        @SuppressWarnings("unchecked")
        public Set<K> keySet()
        {
            Set<K> answer = new HashSet<K>();
            Object[] keys = delegate.getAllKeys();
            for (int ii = 0; ii < keys.length; ii++)
                answer.add((K) keys[ii]);
            return answer;
        }

        public Collection<Long> values()
        {
            List<Long> answer = new ArrayList<Long>();
            long[] values = delegate.getAllValues();
            for (int ii = 0; ii < values.length; ii++)
                answer.add(values[ii]);
            return answer;
        }
    }

    static class MapObjectLongBridge3<K> extends MapObjectLongBridge<K>
    {
        private static final long serialVersionUID = 1L;

        public MapObjectLongBridge3(HashMapObjectLong<K> delegate)
        {
            super(delegate);
        }

        @SuppressWarnings("unchecked")
        public Set<K> keySet()
        {
            Set<K> answer = new HashSet<K>();
            Object[] keys = delegate.getAllKeys(new Object[delegate.size()]);
            for (int ii = 0; ii < keys.length; ii++)
                answer.add((K) keys[ii]);
            return answer;
        }
    }

    static class MapObjectLongBridge4<K> extends MapObjectLongBridge<K>
    {
        private static final long serialVersionUID = 1L;

        public MapObjectLongBridge4(HashMapObjectLong<K> delegate)
        {
            super(delegate);
        }
        
        /**
         * check that remove is done by equality not identity
         */
        public Long remove(Object key)
        {
            if (key instanceof Integer)
            {
                // Deliberately construct new object
                key = cloneInteger((Integer)key);
            }
            return super.remove(key);
        }
    }

    // //////////////////////////////////////////////////////////////
    // map test stub
    // //////////////////////////////////////////////////////////////

    abstract class TestStub<K extends Comparable<K>, V extends Comparable<V>>
    {
        K[] keys;
        V[] values;

        public TestStub(K[] keys, V[] values)
        {
            this.keys = keys;
            this.values = values;
        }

        protected abstract Map<K, V> createEmpty();

        public void run() throws IOException, ClassNotFoundException
        {
            //TestSnapshots.testAssertionsEnabled();
            assertThat("Keys and values must have the same length", values.length, equalTo(keys.length)); //$NON-NLS-1$

            Map<K, V> subject = createEmpty();
            Map<K, V> reference = new HashMap<K, V>();

            verifyEmpty(subject);
            verifyInsert(subject, reference);
            verifyKeys(subject, reference);
            verifyValues(subject, reference);
            verifyGets(subject, reference);
            verifyContains(subject, reference);
            verifyReplace(subject, reference);
            verifyContains(subject, reference);
            byte b[] = serialize(subject);
            Map<K, V> subject2 = deserialize(b);
            verifyGets(subject2, reference);
            verifyRemove(subject, reference);
            verifyEmpty(subject);
        }

        /**
         * For performance testing
         */
        public void perfRun()
        {
            assertThat("Keys and values must have the same length", values.length, equalTo(keys.length)); //$NON-NLS-1$

            Map<K, V> subject = createEmpty();
            Map<K, V> reference = new HashMap<K, V>();
            verifyInsert(subject, reference);
            verifyContains(subject, reference);
        }

        private void verifyGets(Map<K, V> subject, Map<K, V> reference)
        {
            for (K key : keys)
            {
                V get = subject.get(key);
                V get2 = reference.get(key);

                assertThat("reference size "+reference.size(), get, equalTo(get2)); //$NON-NLS-1$
            }
        }

        private void verifyContains(Map<K, V> subject, Map<K, V> reference)
        {
            int i = 0;
            for (K key : keys)
            {
                boolean contains1 = subject.containsKey(key);
                boolean contains2 = reference.containsKey(key);

                assertThat(contains1, equalTo(contains2));
                
                if (key instanceof Integer)
                {
                    Integer keyi = i;
                    contains1 = subject.containsKey(keyi);
                    contains2 = reference.containsKey(keyi);

                    assertThat("Map size " + subject.size() + " ref size " + reference.size() +" key " + keyi, contains1, equalTo(contains2)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                else if (key instanceof Long)
                {
                    Long keyl = (long)i;
                    contains1 = subject.containsKey(keyl);
                    contains2 = reference.containsKey(keyl);

                    assertThat("Map size " + subject.size() + " ref size " + reference.size() + " key " + keyl, contains1, equalTo(contains2)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                ++i;
            }
        }

        private void verifyRemove(Map<K, V> subject, Map<K, V> reference)
        {
            for (Map.Entry<K, V> entry : reference.entrySet())
                assertThat(subject.remove(entry.getKey()), equalTo(entry.getValue()));
        }

        private void verifyInsert(Map<K, V> subject, Map<K, V> reference)
        {
            for (int ii = 0; ii < keys.length; ii++)
            {
                V put = subject.put(keys[ii], values[ii]);
                V put2 = reference.put(keys[ii], values[ii]);
                assertThat(put, equalTo(put2));
                assertThat(subject.size(), equalTo(reference.size()));
            }
        }

        private void verifyReplace(Map<K, V> subject, Map<K, V> reference)
        {
            int size = subject.size();
            int i = 0;
            for (Iterator<Entry<K, V>>it = subject.entrySet().iterator(); it.hasNext();)
            {
                Entry<K, V> e = it.next();
                V val = e.getValue();
                V prev = subject.put(e.getKey(), e.getValue());
                assertThat(prev, equalTo(val));
                assertThat("i = "+i, subject.size(), equalTo(size)); //$NON-NLS-1$
                i++;
                assertThat(e.getValue(), equalTo(reference.get(e.getKey())));
            }
            assertThat(i, equalTo(size));
            assertThat(subject.size(), equalTo(size));
        }

        private void verifyKeys(Map<K, V> subject, Map<K, V> reference)
        {
            List<K> subjectKeys = new ArrayList<K>(subject.keySet());
            Collections.sort(subjectKeys);
            List<K> referenceKeys = new ArrayList<K>(reference.keySet());
            Collections.sort(referenceKeys);

            assertThat(subjectKeys.size(), equalTo(referenceKeys.size()));
            assertThat(subjectKeys, equalTo(referenceKeys));
        }

        private void verifyValues(Map<K, V> subject, Map<K, V> reference)
        {
            List<V> subjectKeys = new ArrayList<V>(subject.values());
            Collections.sort(subjectKeys);
            List<V> referenceKeys = new ArrayList<V>(reference.values());
            Collections.sort(referenceKeys);

            assertThat(subjectKeys.size(), equalTo(referenceKeys.size()));
            assertThat(subjectKeys, equalTo(referenceKeys));
        }

        private void verifyEmpty(Map<K, V> subject)
        {
            assertTrue(subject.isEmpty());
            assertThat(subject.size(), equalTo(0));
            assertTrue(subject.keySet().isEmpty());
            assertTrue(subject.values().isEmpty());
            assert subject.entrySet().isEmpty();
            for (K key : keys)
                assertFalse(subject.containsKey(key));
            for (V value : values)
                assertFalse(subject.containsValue(value));
            for (K key : keys)
                assertThat(subject.remove(key), equalTo(null));
        }

        private byte[] serialize(Map<K, V> subject) throws IOException
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(subject);
            return baos.toByteArray();
        }

        @SuppressWarnings("unchecked")
        private Map<K, V> deserialize(byte b[]) throws IOException, ClassNotFoundException
        {
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object subject = ois.readObject();
            return (Map<K, V>)subject;
        }
    }
}
