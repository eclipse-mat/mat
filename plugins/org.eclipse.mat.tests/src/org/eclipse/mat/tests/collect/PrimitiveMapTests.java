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
import org.junit.Test;

public class PrimitiveMapTests
{
    private static final int NUM_VALUES = 10000;

    // //////////////////////////////////////////////////////////////
    // HashMapIntLong
    // //////////////////////////////////////////////////////////////

    @Test
    public void testIntLongMap()
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

    class MapIntLongBridge implements Map<Integer, Long>
    {
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

    class MapIntLongBridge2 extends MapIntLongBridge
    {
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
    public void testIntObjectMap()
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

    // //////////////////////////////////////////////////////////////
    // bridges
    // //////////////////////////////////////////////////////////////

    class MapIntObjectBridge<V> implements Map<Integer, V>
    {
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

    class MapIntObjectBridge2<V> extends MapIntObjectBridge<V>
    {

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

    class MapIntObjectBridge3<V> extends MapIntObjectBridge<V>
    {

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
    public void testLongObjectMap()
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

    class MapLongObjectBridge<V> implements Map<Long, V>
    {
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

    class MapLongObjectBridge2<V> extends MapLongObjectBridge<V>
    {

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

    class MapLongObjectBridge3<V> extends MapLongObjectBridge<V>
    {

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
    public void testObjectLongMap()
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
                keys[ii] = new Integer(keys[ii - 1]);
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

    class MapObjectLongBridge<K> implements Map<K, Long>
    {
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

    class MapObjectLongBridge2<K> extends MapObjectLongBridge<K>
    {

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

    class MapObjectLongBridge3<K> extends MapObjectLongBridge<K>
    {

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

    class MapObjectLongBridge4<K> extends MapObjectLongBridge<K>
    {
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

        public void run()
        {
            TestSnapshots.testAssertionsEnabled();
            assert keys.length == values.length : "Keys and values must have the same length";

            Map<K, V> subject = createEmpty();
            Map<K, V> reference = new HashMap<K, V>();

            verifyEmpty(subject);
            verifyInsert(subject, reference);
            verifyKeys(subject, reference);
            verifyValues(subject, reference);
            verifyGets(subject, reference);
            verifyRemove(subject, reference);
            verifyEmpty(subject);
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
    }
}
