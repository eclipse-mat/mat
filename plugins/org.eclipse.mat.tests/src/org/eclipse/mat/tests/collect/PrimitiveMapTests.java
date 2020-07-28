/*******************************************************************************
 * Copyright (c) 2008,2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - fix deprecated method
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
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
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapObjectLong;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.tests.TestSnapshots;
import org.junit.Ignore;
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

    @Test
    public void testIntLongMap() throws ClassNotFoundException, IOException
    {
        Random r = new Random();

        Integer[] keys = new Integer[NUM_VALUES];
        Long[] values = new Long[NUM_VALUES];

        for (int ii = 0; ii < NUM_VALUES; ii++)
        {
            keys[ii] = r.nextInt();
            values[ii] = r.nextLong();

            // make sure we have at least one duplicate
            if (ii == 10)
            {
                ii++;
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
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

    @Test
    @Ignore
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
        Random r = new Random();

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

    @Test
    public void testIntObjectMap() throws ClassNotFoundException, IOException
    {
        Random r = new Random();

        Integer[] keys = new Integer[NUM_VALUES];
        Long[] values = new Long[NUM_VALUES];

        for (int ii = 0; ii < NUM_VALUES; ii++)
        {
            keys[ii] = r.nextInt();
            values[ii] = r.nextLong();

            // make sure we have at least one duplicate
            if (ii == 10)
            {
                ii++;
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
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
    @Ignore
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
        Random r = new Random();

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

    @Test
    public void testLongObjectMap() throws ClassNotFoundException, IOException
    {
        Random r = new Random();

        Long[] keys = new Long[NUM_VALUES];
        Integer[] values = new Integer[NUM_VALUES];

        for (int ii = 0; ii < NUM_VALUES; ii++)
        {
            keys[ii] = r.nextLong();
            values[ii] = r.nextInt();

            // make sure we have at least one duplicate
            if (ii == 10)
            {
                ii++;
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
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
    @Ignore
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
        Random r = new Random();

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

    @Test
    public void testObjectLongMap() throws ClassNotFoundException, IOException
    {
        Random r = new Random();

        Integer[] keys = new Integer[NUM_VALUES];
        Long[] values = new Long[NUM_VALUES];

        for (int ii = 0; ii < NUM_VALUES; ii++)
        {
            keys[ii] = r.nextInt();
            values[ii] = r.nextLong();

            // make sure we have at least one duplicate
            if (ii == 10)
            {
                ii++;
                keys[ii] = keys[ii - 1];
                values[ii] = values[ii - 1];
            }

            // make sure we have at least one via equals
            if (ii == 20)
            {
                ii++;
                // Deliberately construct new object
                keys[ii] = new Integer(keys[ii - 1]);
                // Deliberately construct new object
                values[ii] = new Long(values[ii - 1]);
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
    @Ignore
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
        Random r = new Random();

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
                key = new Integer((Integer)key);
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
            TestSnapshots.testAssertionsEnabled();
            assert keys.length == values.length : "Keys and values must have the same length"; //$NON-NLS-1$

            Map<K, V> subject = createEmpty();
            Map<K, V> reference = new HashMap<K, V>();

            verifyEmpty(subject);
            verifyInsert(subject, reference);
            verifyKeys(subject, reference);
            verifyValues(subject, reference);
            verifyGets(subject, reference);
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
            TestSnapshots.testAssertionsEnabled();
            assert keys.length == values.length : "Keys and values must have the same length"; //$NON-NLS-1$

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

                assert get == null ? get2 == null : get.equals(get2);
            }
        }

        private void verifyContains(Map<K, V> subject, Map<K, V> reference)
        {
            int i = 0;
            for (K key : keys)
            {
                boolean contains1 = subject.containsKey(key);
                boolean contains2 = reference.containsKey(key);

                assert contains1 == contains2;
                
                if (key instanceof Integer)
                {
                    Integer keyi = i;
                    contains1 = subject.containsKey(keyi);
                    contains2 = reference.containsKey(keyi);

                    assert contains1 == contains2;
                }
                else if (key instanceof Long)
                {
                    Long keyl = (long)i;
                    contains1 = subject.containsKey(keyl);
                    contains2 = reference.containsKey(keyl);

                    assert contains1 == contains2;
                }
                ++i;
            }
        }

        private void verifyRemove(Map<K, V> subject, Map<K, V> reference)
        {
            for (Map.Entry<K, V> entry : reference.entrySet())
                assert entry.getValue().equals(subject.remove(entry.getKey()));
        }

        private void verifyInsert(Map<K, V> subject, Map<K, V> reference)
        {
            for (int ii = 0; ii < keys.length; ii++)
            {
                V put = subject.put(keys[ii], values[ii]);
                V put2 = reference.put(keys[ii], values[ii]);
                assert put == null ? put2 == null : put.equals(put2);
                assert subject.size() == reference.size();
            }
        }

        private void verifyKeys(Map<K, V> subject, Map<K, V> reference)
        {
            List<K> subjectKeys = new ArrayList<K>(subject.keySet());
            Collections.sort(subjectKeys);
            List<K> referenceKeys = new ArrayList<K>(reference.keySet());
            Collections.sort(referenceKeys);

            assert subjectKeys.size() == referenceKeys.size();
            assert subjectKeys.equals(referenceKeys);
        }

        private void verifyValues(Map<K, V> subject, Map<K, V> reference)
        {
            List<V> subjectKeys = new ArrayList<V>(subject.values());
            Collections.sort(subjectKeys);
            List<V> referenceKeys = new ArrayList<V>(reference.values());
            Collections.sort(referenceKeys);

            assert subjectKeys.size() == referenceKeys.size();
            assert subjectKeys.equals(referenceKeys);
        }

        private void verifyEmpty(Map<K, V> subject)
        {
            assert subject.isEmpty();
            assert subject.size() == 0;
            assert subject.keySet().isEmpty();
            assert subject.values().isEmpty();
            assert subject.entrySet().isEmpty();
            for (K key : keys)
                assert !subject.containsKey(key);
            for (V value : values)
                assert !subject.containsValue(value);
            for (K key : keys)
                assert subject.remove(key) == null;
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
