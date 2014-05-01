/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation
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

/**
 * Create a dump to show different Collection classes.
 */
public class CreateCollectionDump
{

    public static void main(String[] args) throws Exception
    {
        ListCollectionTestData listCollectionTestData = new ListCollectionTestData();
        NonListCollectionTestData nonListCollectionTestData = new NonListCollectionTestData();
        MapTestData mapTestData = new MapTestData();
        EmptyListCollectionTestData emptyListCollectionTestData = new EmptyListCollectionTestData();
        EmptyNonListCollectionTestData emptyNonListCollectionTestData = new EmptyNonListCollectionTestData();
        EmptyMapTestData emptyMapTestData = new EmptyMapTestData();

        System.out.println("Acquire Heap Dump NOW (then press any key to terminate program)");
        int c = System.in.read();
        // Control-break causes read to return early, so try again for another
        // key to wait
        // for the dump to complete
        if (c == -1)
            c = System.in.read();

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
        public static final int COUNT = 17;
        String values[];
        Collection collections[];
        public boolean useEmpty() {
            return false;
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
                            // Don't currently quite support these
                            //"java.util.concurrent.ConcurrentLinkedDeque",
                            //"java.util.concurrent.ConcurrentLinkedQueue",
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
                            // Don't currently quite support this
                            //"java.util.concurrent.LinkedTransferQueue",
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
                    try
                    {
                        for (int i = 1; i <= COUNT; ++i)
                        {
                            cl.add(cn + ":" + i);
                        }
                        int from = 0;
                        int to = COUNT * 2 / 3;
                        for (int i = to; i <= COUNT; i += 1)
                        {
                            cl.remove(cn + ":" + i);
                        }
                        if (cl instanceof List)
                        {
                            List l = (List)cl;
                            int i;
                            for (i = to - 1; i > 0; i -= 3)
                            {
                                l.remove(i - 1);
                            }
                            for (i += 3; i < to; i += 3)
                            {
                                l.add(i - 1, cn + ":" + i);
                            }
                        }
                        for (int i = to; i <= COUNT; i += 1)
                        {
                            cl.add(cn + ":" + i);
                        }
                    }
                    catch (ClassCastException e)
                    {
                        // E.g. Delayed
                    }
                    catch (IllegalStateException e)
                    {
                        // Synchronous queue
                    }
                    if (cl.isEmpty() == useEmpty())
                        cols.add(cl);
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
    public static class EmptyNonListCollectionTestData extends NonListCollectionTestData {
        public boolean useEmpty() {
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
    public static class EmptyListCollectionTestData extends ListCollectionTestData {
        public boolean useEmpty() {
            return true;
        }
    }

    /**
     * Maps which do have COUNT entries 
     */
    public static class MapTestData
    {
        public static final int COUNT = 17;
        Map maps[];
        String keys[];
        public boolean useEmpty() {
            return false;
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
                    try
                    {
                        for (int i = 1; i <= COUNT; ++i)
                        {
                            if (keys[i - 1] == null)
                                keys[i - 1] = String.valueOf(i);
                            cl.put(keys[i - 1], cn + ":" + i);
                        }
                        // Try shuffling around a little
                        for (int i = 1; i <= COUNT; i += 3)
                        {
                            cl.remove(keys[i - 1]);
                        }
                        for (int i = 1; i <= COUNT; i += 3)
                        {
                            cl.put(keys[i - 1], cn + ":" + i);
                        }
                    }
                    catch (ClassCastException e)
                    {
                        // E.g. PrinterStateReasons
                        e.printStackTrace();
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
    public static class EmptyMapTestData extends MapTestData {
        public boolean useEmpty() {
            return true;
        }
    }

}
