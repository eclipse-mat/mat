/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *    Andrew Johnson - add Strings for decoding tests
 *******************************************************************************/
package org.eclipse.mat.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;

import javax.print.attribute.standard.JobStateReason;
import javax.print.attribute.standard.PrinterStateReason;
import javax.print.attribute.standard.Severity;

/**
 * Create a dump to show different Collection classes.
 */
public class CreateCollectionDump
{
    ListCollectionTestData listCollectionTestData = new ListCollectionTestData();
    NonListCollectionTestData nonListCollectionTestData = new NonListCollectionTestData();
    MapTestData mapTestData = new MapTestData();
    EmptyListCollectionTestData emptyListCollectionTestData = new EmptyListCollectionTestData();
    EmptyNonListCollectionTestData emptyNonListCollectionTestData = new EmptyNonListCollectionTestData();
    EmptyMapTestData emptyMapTestData = new EmptyMapTestData();
    // Add Strings for testing class specific name printing
    String s1 = "My String";
    StringBuilder sl1 = new StringBuilder(s1);
    StringBuffer sf1 = new StringBuffer(s1);
    String s2 = "My String with e-acute \u00E9 and Greek Delta \u0394";
    StringBuilder sl2 = new StringBuilder(s2);
    StringBuffer sf2 = new StringBuffer(s2);

    public static void main(String[] args) throws Exception
    {
        CreateCollectionDump cd = new CreateCollectionDump();

        System.out.println("Acquire Heap Dump NOW (then press any key to terminate program)");
        int c = System.in.read();
        // Control-break causes read to return early, so try again for another
        // key to wait
        // for the dump to complete
        if (c == -1)
            c = System.in.read();

        cd.print();
    }

    /**
     * Used to ensure GC doesn't discard objects
     */
    public void print()
    {
        System.out.println(listCollectionTestData);
        System.out.println(nonListCollectionTestData);
        System.out.println(mapTestData);
        System.out.println(emptyListCollectionTestData);
        System.out.println(emptyNonListCollectionTestData);
        System.out.println(emptyMapTestData);
    }

    // //////////////////////////////////////////////////////////////
    // Collections
    // //////////////////////////////////////////////////////////////

    public static abstract class CollectionTestData
    {
        private static final int SAMEHASH = 256;
        public static final int COUNT = SAMEHASH * 4 / 3;
        String values[];
        Collection collections[];

        public boolean useEmpty()
        {
            return false;
        }

        static String samehash[] = new String[SAMEHASH];

        static
        {
            for (int i = SAMEHASH - 1; i >= 0; --i)
            {
                String s = Integer.toBinaryString(i);
                s = s.replaceAll("0", "aa").replaceAll("1", "bB");
                samehash[i] = s;
            }
        }

        public void fillValues(String prefix)
        {
            values = new String[COUNT + 1];
            for (int i = 1; i <= COUNT; ++i)
            {
                values[i] = prefix + ":" + i;
            }
            for (int i = 0; i < samehash.length; ++i)
            {
                values[COUNT - samehash.length + i] = prefix + ":" + samehash[i];
            }
            for (int i = 1; i <= COUNT; ++i)
            {
                //System.out.println(values[i] + " " + values[i].hashCode());
            }
        }

        public abstract boolean accept(Class<? extends Collection> c);

        public CollectionTestData()
        {
            List<Collection>cols = new ArrayList<Collection>();
            /*
             * List of classes under test.
             * Space separate class from static method
             */
            String ls[] = new String[] {
                            "java.util.AbstractCollection",
                            "java.util.AbstractList",
                            "java.util.AbstractQueue",
                            "java.util.AbstractSequentialList",
                            "java.util.AbstractSet",
                            "java.util.concurrent.ArrayBlockingQueue",
                            "java.util.ArrayDeque",
                            "java.util.ArrayList",
                            "java.util.Arrays asList",
                            "javax.management.AttributeList",
                            "java.beans.beancontext.BeanContextServicesSupport",
                            "java.beans.beancontext.BeanContextSupport",
                            "java.util.concurrent.ConcurrentLinkedDeque",
                            "java.util.concurrent.ConcurrentLinkedQueue",
                            "java.util.concurrent.ConcurrentSkipListSet",
                            "java.util.concurrent.CopyOnWriteArrayList",
                            "java.util.concurrent.CopyOnWriteArraySet",
                            "java.util.concurrent.DelayQueue",
                            "java.util.Collections checkedCollection",
                            "java.util.Collections checkedList",
                            "java.util.Collections checkedQueue",
                            "java.util.Collections checkedSet",
                            "java.util.Collections checkedSortedSet",
                            "java.util.Collections checkedNavigableSet",
                            "java.util.Collections emptyList",
                            "java.util.Collections emptySet",
                            "java.util.Collections emptySortedSet",
                            "java.util.Collections emptyNavigableSet",
                            "java.util.Collections singleton",
                            "java.util.Collections singletonList",
                            "java.util.Collections synchronizedCollection",
                            "java.util.Collections synchronizedList",
                            "java.util.Collections synchronizedSet",
                            "java.util.Collections synchronizedSortedSet",
                            "java.util.Collections synchronizedNavigableSet",
                            "java.util.Collections unmodifiableCollection",
                            "java.util.Collections unmodifiableList",
                            "java.util.Collections unmodifiableSet",
                            "java.util.Collections unmodifiableSortedSet",
                            "java.util.Collections unmodifiableNavigableSet",
                            "java.util.EnumSet",
                            "java.util.HashSet",
                            "javax.print.attribute.standard.JobStateReasons",
                            "java.util.concurrent.LinkedBlockingDeque",
                            "java.util.concurrent.LinkedBlockingQueue",
                            "java.util.LinkedHashSet",
                            "java.util.LinkedList",
                            "java.util.concurrent.LinkedTransferQueue",
                            "java.util.PriorityQueue",
                            "javax.management.relation.RoleList",
                            "javax.management.relation.RoleUnresolvedList",
                            "java.util.Stack",
                            "java.util.concurrent.SynchronousQueue",
                            "java.util.TreeSet",
                            "java.util.Vector",
            };

            values = new String[COUNT];
            for (String cn : ls)
            {
                Class<? extends Collection> c = null;
                // Methods?
                String cn0 = cn;
                String ss[] = cn.split(" ", 2);
                final String mn;
                Class<?>cm;
                // Ordinary class or static method to create class
                if (ss.length > 1)
                {
                    cn = ss[0];
                    mn = ss[1];
                    try
                    {
                        cm = Class.forName(cn);
                    }
                    catch (ClassNotFoundException e)
                    {
                        System.err.println("Can't find class "+cn);
                        continue;
                    }
                }
                else
                {
                    mn = null;
                    cm = null;
                    try
                    {
                        c = Class.forName(cn).asSubclass(Collection.class);
                    }
                    catch (ClassNotFoundException e)
                    {
                        System.err.println("Can't find class "+cn);
                        continue;
                    }
                    if (!accept(c))
                        continue;
                    if (Modifier.isAbstract(c.getModifiers()))
                    {
                        continue;
                    }
                }

                // To use an existing collection constructor
                fillValues(cn);
                List<String>arrayVals = new ArrayList<String>();
                for (int i = 1; i <= COUNT; ++i)
                {
                    arrayVals.add(values[i]);
                }
                // And a plain 0-based array
                String values0[] = arrayVals.toArray(new String[arrayVals.size()]);
                Set<String>setVals = new TreeSet<String>(arrayVals);
                Set<String>setVals2 = new HashSet<String>(arrayVals);
                Queue queue = new ArrayDeque(setVals);
                Object[][] args = new Object[][] { {}, { Integer.valueOf(0) }, { new Integer(COUNT) }, { new Integer(COUNT * 2) },
                    { arrayVals }, { arrayVals }, { setVals2 }, { setVals2 }, { setVals }, { setVals }, { setVals }, { setVals },
                    { values0 }, {queue}, {queue},
                    { arrayVals, String.class}, { arrayVals, String.class}, { setVals, String.class}, { queue, String.class},
                    { setVals, String.class}, { setVals, String.class},
                    { values[1] },
                    { Collections.emptySet() },
                    { Collections.emptySet() },
                };

                // Matches argument objects
                Class[][] cons = new Class[][] { {}, { Integer.TYPE }, { Integer.TYPE }, { Integer.TYPE },
                                { Collection.class }, { List.class }, { Collection.class }, { Set.class }, { Collection.class }, { Set.class }, { SortedSet.class }, { NavigableSet.class },
                                { Object[].class }, { Collection.class }, { Deque.class },
                                { Collection.class, Class.class }, { List.class, Class.class }, { Set.class, Class.class }, { Queue.class, Class.class },
                                { SortedSet.class, Class.class }, { NavigableSet.class, Class.class },
                                { Object.class },
                                { Set.class },
                                { Collections.class },
                };
                int added = 0;
                // Try the different constuctors / methods
                for (int j = 0; j < cons.length; ++j)
                {
                    Collection cl;
                    try
                    {
                        if (mn == null)
                        {
                            if (cons[j].length == 0)
                            {
                                cl = c.newInstance();
                            }
                            else
                            {
                                Constructor<? extends Collection>cns = c.getConstructor(cons[j]);
                                cl = cns.newInstance(args[j]);
                            }
                        }
                        else
                        {
                            Method method = cm.getMethod(mn, cons[j]);
                            cl = (Collection)method.invoke(null, args[j]);
                            c = cl.getClass();
                            if (!accept(c))
                            {
                                continue;
                            }
                            if (!c.getName().equals(cn))
                            {
                                // Class name changed, so rebuild initial values
                                cn = c.getName();
                                // To use an existing collection constructor
                                fillValues(cn);
                                arrayVals = new ArrayList<String>();
                                for (int i = 1; i <= COUNT; ++i)
                                {
                                    arrayVals.add(values[i]);
                                }
                                // And a plain 0-based array
                                values0 = arrayVals.toArray(new String[arrayVals.size()]);
                                setVals = new TreeSet<String>(arrayVals);
                                setVals2 = new HashSet<String>(arrayVals);
                                queue = new ArrayDeque(setVals);
                                args = new Object[][] { {}, { Integer.valueOf(0) }, { new Integer(COUNT) }, { new Integer(COUNT * 2) },
                                    { arrayVals }, { arrayVals }, { setVals2 }, { setVals2 }, { setVals }, { setVals }, { setVals }, { setVals },
                                    { values0 }, {queue}, {queue},
                                    { arrayVals, String.class}, { arrayVals, String.class}, { setVals, String.class}, { queue, String.class},
                                    { setVals, String.class}, { setVals, String.class},
                                    { values[1] },
                                    { Collections.emptySet() },
                                    { Collections.emptySet() },
                                };
                                cl = (Collection)method.invoke(null, args[j]);
                            }
                        }
                        if (!useEmpty())
                        {
                            try
                            {
                                if (cl.isEmpty())
                                {
                                    // Fill the collection
                                    for (int i = 1; i <= COUNT; ++i)
                                    {
                                        cl.add(values[i]);
                                    }
                                }
                                // Remove and refill
                                int from = 0;
                                int to = COUNT * 2 / 3;
                                for (int i = to; i <= COUNT; i += 1)
                                {
                                    cl.remove(values[i]);
                                }
                                if (cl instanceof List)
                                {
                                    List l = (List) cl;
                                    int i;
                                    for (i = to - 1; i > 0; i -= 3)
                                    {
                                        l.remove(i - 1);
                                    }
                                    for (i += 3; i < to; i += 3)
                                    {
                                        l.add(i - 1, values[i]);
                                    }
                                }
                                for (int i = to; i <= COUNT; i += 1)
                                {
                                    cl.add(values[i]);
                                }
                            }
                            catch (UnsupportedOperationException e)
                            {
                            }
                            catch (ClassCastException e)
                            {
                                // E.g. Delayed
                                //e.printStackTrace();
                            }
                            catch (IllegalStateException e)
                            {
                                // Synchronous queue
                                //e.printStackTrace();
                            }
                            if (cl.isEmpty())
                            {
                                // Special case if String not accepted
                                try
                                {
                                    cl.add(JobStateReason.JOB_COMPLETED_SUCCESSFULLY);
                                }
                                catch (UnsupportedOperationException e)
                                {
                                }
                                catch (ClassCastException e)
                                {
                                }
                                catch (IllegalStateException e)
                                {
                                }
                            }
                        }
                        if (cl.isEmpty() == useEmpty())
                        {
                            cols.add(cl);
                            ++added;
                            //System.out.println("coll "+cl.size()+" "+cl.getClass()+"  "+useEmpty());
                        }
                    }
                    catch (NoSuchMethodException e)
                    {
                        //e.printStackTrace(System.out);
                    }
                    catch (SecurityException e)
                    {
                        e.printStackTrace();
                    }
                    catch (InstantiationException e)
                    {
                        //e.printStackTrace();
                    }
                    catch (IllegalAccessException e)
                    {
                        //e.printStackTrace();
                    }
                    catch (IllegalArgumentException e)
                    {
                        //e.printStackTrace();
                    }
                    catch (InvocationTargetException e)
                    {
                        //e.printStackTrace();
                    }
                }
                if (added == 0 && (mn == null || c == null))
                {
                    System.out.println("Missing collection "+cn0+" "+useEmpty());
                }
            }
            collections = cols.toArray(new Collection[cols.size()]);
        }

        public String toString()
        {
            return Arrays.toString(collections);
        }

        public Collection[] getCollections()
        {
            return collections.clone();
        }
    }

    /**
     * Collections which are not lists but do have COUNT entries
     */
    public static class NonListCollectionTestData extends CollectionTestData
    {
        public boolean accept(Class<? extends Collection> c)
        {
            return !List.class.isAssignableFrom(c);
        }
    }

    /**
     * Collections which are not lists which are empty
     */
    public static class EmptyNonListCollectionTestData extends NonListCollectionTestData
    {
        public boolean useEmpty()
        {
            return true;
        }
    }

    /**
     * Lists which do have COUNT entries
     */
    public static class ListCollectionTestData extends CollectionTestData
    {

        public boolean accept(Class<? extends Collection> c)
        {
            return List.class.isAssignableFrom(c);
        }
    }

    /**
     * Lists which are empty
     */
    public static class EmptyListCollectionTestData extends ListCollectionTestData
    {
        public boolean useEmpty()
        {
            return true;
        }
    }

    /**
     * Maps which do have COUNT entries
     */
    public static class MapTestData
    {
        private static final int SAMEHASH = 256;
        public static final int COUNT = SAMEHASH * 4 / 3;
        Map maps[];
        String keys[];
        String values[];

        public boolean useEmpty()
        {
            return false;
        }

        static String samehash[] = new String[SAMEHASH];

        static
        {
            for (int i = SAMEHASH - 1; i >= 0; --i)
            {
                String s = Integer.toBinaryString(i);
                s = s.replaceAll("0", "aa").replaceAll("1", "bB");
                samehash[i] = s;
            }
        }

        public void fillValues(String prefix)
        {
            values = new String[COUNT + 1];
            keys = new String[COUNT + 1];
            for (int i = 1; i <= COUNT; ++i)
            {
                values[i] = prefix + ":" + i;
                keys[i] = String.valueOf(i);
            }
            for (int i = 0; i < samehash.length; ++i)
            {
                values[COUNT - samehash.length + i] = prefix + ":" + samehash[i];
                // Make a fresh String so key is just used here
                keys[COUNT - samehash.length + i] = "" + samehash[i];
            }
            for (int i = 1; i <= COUNT; ++i)
            {
                //System.out.println(keys[i] + " " + keys[i].hashCode());
                //System.out.println(values[i] + " " + values[i].hashCode());
            }
        }

        public MapTestData()
        {
            List<Map>ms = new ArrayList<Map>();
            // List of maps under test
            String ls[] = new String[] {
                            "java.util.AbstractMap",
                            "java.util.jar.Attributes",
                            "java.security.AuthProvider",
                            "java.util.Collections checkedMap",
                            "java.util.Collections checkedSortedMap",
                            "java.util.Collections checkedNavigableMap",
                            "java.util.Collections emptyMap",
                            "java.util.Collections emptySortedMap",
                            "java.util.Collections emptyNavigableMap",
                            "java.util.Collections singletonMap",
                            "java.util.Collections synchronizedMap",
                            "java.util.Collections synchronizedSortedMap",
                            "java.util.Collections synchronizedNavigableMap",
                            "java.util.Collections unmodifiableMap",
                            "java.util.Collections unmodifiableSortedMap",
                            "java.util.Collections unmodifiableNavigableMap",
                            "java.util.concurrent.ConcurrentHashMap",
                            "java.util.concurrent.ConcurrentSkipListMap",
                            "java.util.EnumMap",
                            "java.util.HashMap",
                            "java.util.Hashtable",
                            "java.util.IdentityHashMap",
                            "java.util.LinkedHashMap",
                            "javax.print.attribute.standard.PrinterStateReasons",
                            "java.util.Properties",
                            "java.security.Provider",
                            "java.awt.RenderingHints",
                            "javax.script.SimpleBindings",
                            "javax.management.openmbean.TabularDataSupport",
                            "java.util.TreeMap",
                            "javax.swing.UIDefaults",
                            "java.util.WeakHashMap" // Make WeakHashMap last so that keys are retained
            };

            keys = new String[COUNT];
            for (String cn : ls)
            {
                Class<? extends Map> c = null;
                String cn0 = cn;
                // Methods?
                String ss[] = cn.split(" ", 2);
                final String mn;
                Class<?>cm;
                if (ss.length > 1)
                {
                    cn = ss[0];
                    mn = ss[1];
                    try
                    {
                        cm = Class.forName(cn);
                    }
                    catch (ClassNotFoundException e)
                    {
                        System.err.println("Can't find class "+cn);
                        continue;
                    }
                }
                else
                {
                    mn = null;
                    cm = null;
                    try
                    {
                        c = Class.forName(cn).asSubclass(Map.class);
                    }
                    catch (ClassNotFoundException e)
                    {
                        System.err.println("Can't find class "+cn);
                        continue;
                    }
                    if (Modifier.isAbstract(c.getModifiers()))
                    {
                        continue;
                    }
                }
                // To use an existing map constructor
                fillValues(cn);
                Map<String,String>mapVals = new TreeMap<String,String>();
                for (int i = 1; i <= COUNT; ++i)
                {
                    mapVals.put(keys[i], values[i]);
                }
                Map<String,String>mapVals2 = new HashMap<String,String>(mapVals);
                Object[][] args = new Object[][] { {}, { Integer.valueOf(0) }, { new Integer(COUNT) }, { new Integer(COUNT * 2) },
                    { mapVals2 } , { mapVals } , { mapVals } , { mapVals },
                    { mapVals, String.class, String.class }, { mapVals, String.class, String.class }, { mapVals, String.class, String.class },
                    { keys[1], values[1] },
                    { Collections.emptyMap() },
                };

                Class[][] cons = new Class[][] { {}, { Integer.TYPE }, { Integer.TYPE }, { Integer.TYPE },
                    { Map.class }, { Map.class }, { SortedMap.class }, { NavigableMap.class },
                    { Map.class, Class.class, Class.class }, { SortedMap.class, Class.class, Class.class }, { NavigableMap.class, Class.class, Class.class },
                    { Object.class, Object.class },
                    { Map.class },
                };

                int added = 0;
                for (int j = 0; j < cons.length; ++j)
                {
                    Map cl;
                    try
                    {
                        if (mn == null)
                        {
                            if (cons[j].length == 0)
                            {
                                cl = c.newInstance();
                            }
                            else
                            {
                                Constructor<? extends Map>cns = c.getConstructor(cons[j]);
                                cl = cns.newInstance(args[j]);
                            }
                        }
                        else
                        {
                            cl = (Map)cm.getMethod(mn, cons[j]).invoke(null, args[j]);
                            c = cl.getClass();
                            if (!c.getName().equals(cn))
                            {
                                cn = c.getName();
                                fillValues(cn);
                                mapVals = new TreeMap<String,String>();
                                for (int i = 1; i <= COUNT; ++i)
                                {
                                    mapVals.put(keys[i], values[i]);
                                }
                                mapVals2 = new HashMap<String,String>(mapVals);
                                args = new Object[][] { {}, { Integer.valueOf(0) }, { new Integer(COUNT) }, { new Integer(COUNT * 2) },
                                    { mapVals2 } , { mapVals } , { mapVals } , { mapVals },
                                    { mapVals, String.class, String.class }, { mapVals, String.class, String.class }, { mapVals, String.class, String.class },
                                    { keys[1], values[1] },
                                    { Collections.emptyMap() },
                                };
                                cl = (Map)cm.getMethod(mn, cons[j]).invoke(null, args[j]);
                                c = cl.getClass();
                            }
                        }

                        if (!useEmpty())
                        {
                            try
                            {
                                if (cl.isEmpty())
                                {
                                    for (int i = 1; i <= COUNT; ++i)
                                    {
                                        cl.put(keys[i], values[i]);
                                    }
                                }
                                // Try shuffling around a little
                                for (int i = 1; i <= COUNT; i += 3)
                                {
                                    cl.remove(keys[i]);
                                }
                                for (int i = 1; i <= COUNT; i += 3)
                                {
                                    cl.put(keys[i], values[i]);
                                }
                            }
                            catch (UnsupportedOperationException e)
                            {
                            }
                            catch (ClassCastException e)
                            {
                                // E.g. PrinterStateReasons
                                //e.printStackTrace();
                            }
                            if (cl.isEmpty())
                            {
                                // Special case if String not accepted
                                try
                                {
                                    // javax.print.attribute.standard.PrinterStateReasons
                                    cl.put(PrinterStateReason.CONNECTING_TO_DEVICE, Severity.REPORT);
                                }
                                catch (UnsupportedOperationException e)
                                {
                                }
                                catch (ClassCastException e)
                                {
                                }
                            }
                            if (cl.isEmpty())
                            {
                                // Special case if String not accepted
                                try
                                {
                                    // java.util.jar.Attributes
                                    cl.put(Attributes.Name.CLASS_PATH, values[1]);
                                }
                                catch (UnsupportedOperationException e)
                                {
                                }
                                catch (ClassCastException e)
                                {
                                }
                            }
                        }
                        if (cl.isEmpty() == useEmpty())
                        {
                            ms.add(cl);
                            ++added;
                            //System.out.println("map "+cl.size()+" "+cl.getClass()+"  "+useEmpty()+" "+mn+" "+j);
                        }
                    }
                    catch (InstantiationException e)
                    {
                    }
                    catch (IllegalAccessException e)
                    {
                    }
                    catch (NoSuchMethodException e)
                    {
                    }
                    catch (SecurityException e)
                    {
                    }
                    catch (IllegalArgumentException e)
                    {
                    }
                    catch (InvocationTargetException e)
                    {
                    }
                }
                if (added == 0 && (mn == null || c == null))
                {
                    System.out.println("Missing map "+cn0+" "+useEmpty());
                }
            }
            maps = ms.toArray(new Map[ms.size()]);
        }

        public String toString()
        {
            return Arrays.toString(maps);
        }

        public Map[] getMaps()
        {
            return maps.clone();
        }
    }

    /**
     * Maps which are empty
     */
    public static class EmptyMapTestData extends MapTestData
    {
        public boolean useEmpty()
        {
            return true;
        }
    }

    public Collection[] getListCollectionTestData()
    {
        return listCollectionTestData.getCollections();
    }

    public Collection[] getNonListCollectionTestData()
    {
        return nonListCollectionTestData.getCollections();
    }

    public Map[] getMapTestData()
    {
        return mapTestData.getMaps();
    }

    public Collection[] getEmptyListCollectionTestData()
    {
        return emptyListCollectionTestData.getCollections();
    }

    public Collection[] getEmptyNonListCollectionTestData()
    {
        return emptyNonListCollectionTestData.getCollections();
    }

    public Map[] getEmptyMapTestData()
    {
        return emptyMapTestData.getMaps();
    }
}
