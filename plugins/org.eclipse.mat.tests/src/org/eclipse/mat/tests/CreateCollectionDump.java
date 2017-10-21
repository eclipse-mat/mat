/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;

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
            String ls[] = new String[] {
                            "java.util.AbstractCollection",
                            "java.util.AbstractList",
                            "java.util.AbstractQueue",
                            "java.util.AbstractSequentialList",
                            "java.util.AbstractSet",
                            "java.util.concurrent.ArrayBlockingQueue",
                            "java.util.ArrayDeque",
                            "java.util.ArrayList",
                            "javax.management.AttributeList",
                            "java.beans.beancontext.BeanContextServicesSupport",
                            "java.beans.beancontext.BeanContextSupport",
                            "java.util.concurrent.ConcurrentLinkedDeque",
                            "java.util.concurrent.ConcurrentLinkedQueue",
                            "java.util.concurrent.ConcurrentSkipListSet",
                            "java.util.concurrent.CopyOnWriteArrayList",
                            "java.util.concurrent.CopyOnWriteArraySet",
                            "java.util.concurrent.DelayQueue",
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
                            "java.util.Vector"
            };
            
            values = new String[COUNT];
            for (String cn : ls)
            {
                Class<? extends Collection> c = null;
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
                try
                {
                    Collection cl = c.newInstance();
                    if (!useEmpty())
                    {
                        try
                        {
                            fillValues(cn);
                            for (int i = 1; i <= COUNT; ++i)
                            {
                                cl.add(values[i]);
                            }
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
                        catch (ClassCastException e)
                        {
                            // E.g. Delayed
                            e.printStackTrace();
                        }
                        catch (IllegalStateException e)
                        {
                            // Synchronous queue
                            e.printStackTrace();
                        }
                        if (cl.isEmpty() == useEmpty())
                            cols.add(cl);
                    }
                }
                catch (InstantiationException e)
                {}
                catch (IllegalAccessException e)
                {}
            }
            collections = cols.toArray(new Collection[cols.size()]);
        }

        public String toString()
        {
            return Arrays.toString(collections);
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
            String ls[] = new String[] {
                            "java.util.AbstractMap",
                            "java.util.jar.Attributes",
                            "java.security.AuthProvider",
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
            "java.util.WeakHashMap"};

            keys = new String[COUNT];
            for (String cn : ls)
            {
                Class<? extends Map> c = null;

                try
                {
                    c = Class.forName(cn).asSubclass(Map.class);
                }
                catch (ClassNotFoundException e)
                {
                    System.err.println("Can't find class "+cn);
                    continue;
                }
                try
                {
                    Map cl = c.newInstance();
                    if (!useEmpty())
                    {
                        try
                        {
                            fillValues(cn);
                            for (int i = 1; i <= COUNT; ++i)
                            {
                                cl.put(keys[i], values[i]);
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
                        catch (ClassCastException e)
                        {
                            // E.g. PrinterStateReasons
                            e.printStackTrace();
                        }
                        if (cl.isEmpty())
                        {
                            // Special case if String not accepted
                            try
                            {
                                // javax.print.attribute.standard.PrinterStateReasons
                                cl.put(PrinterStateReason.CONNECTING_TO_DEVICE, Severity.REPORT);
                            }
                            catch (ClassCastException e)
                            {}
                        }
                        if (cl.isEmpty())
                        {
                            // Special case if String not accepted
                            try
                            {
                                // java.util.jar.Attributes
                                cl.put(Attributes.Name.CLASS_PATH, values[1]);
                            }
                            catch (ClassCastException e)
                            {}
                        }
                    }
                    if (cl.isEmpty() == useEmpty())
                        ms.add(cl);
                }
                catch (InstantiationException e)
                {}
                catch (IllegalAccessException e)
                {}
            }
            maps = ms.toArray(new Map[ms.size()]);
        }

        public String toString()
        {
            return Arrays.toString(maps);
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

}
