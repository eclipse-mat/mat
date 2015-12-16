/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class KnownCollectionInfo
{
    @SuppressWarnings("nls")
    public static int resolveVersion(ISnapshot snapshot) throws SnapshotException
    {
        Collection<IClass> classes;

        // Previously this code only checked the existence of certain IBM
        // classes for the IBM JVM version, but this is dangerous because we
        // will often backport stuff into older versions. Instead we will check
        // the JVM info, but I'm not sure if we can really depend on that being
        // there, so we'll leave the old checks as fallback.

        String jvmInfo = snapshot.getSnapshotInfo().getJvmInfo();
        if (jvmInfo != null)
        {
            // Example from IBM Java 6:
            // Java(TM) SE Runtime Environment(build pxi3260sr9ifx-20110208_02
            // (SR9))
            // IBM J9 VM(JRE 1.6.0 IBM J9 2.4 Linux x86-32
            // jvmxi3260sr9-20101209_70480 (JIT enabled, AOT enabled)
            // J9VM - 20101209_070480
            // JIT - r9_20101028_17488ifx3
            // GC - 20101027_AA)

            // Example from IBM Java 7 (for some reason doesn't include "IBM"
            // anymore):
            // JRE 1.7.0 Linux amd64-64 build 20130205_137358
            // (pxa6470sr4ifix-20130305_01(SR4+IV37419) )

            if (jvmInfo.contains("IBM") || jvmInfo.contains(" build "))
            {
                int jreIndex = jvmInfo.indexOf("JRE ");
                if (jreIndex != -1)
                {
                    String jreVersion = jvmInfo.substring(jreIndex + 4);
                    if (jreVersion.length() >= 3)
                    {
                        jreVersion = jreVersion.substring(0, 3);
                        if (jreVersion.equals("1.8"))
                            return Version.IBM18;
                        else if (jreVersion.equals("1.7"))
                        {
                            if (jvmInfo.matches(".*\\(SR[1-3][^0-9].*") || jvmInfo.matches(".*\\(GA"))
                            {
                                // Harmony based collections
                                return Version.IBM16;
                            }
                            // SR4 and later switches to Oracle
                            return Version.IBM17;
                        }
                        else if (jreVersion.equals("1.6"))
                            return Version.IBM16;
                        else if (jreVersion.equals("1.5"))
                            return Version.IBM15;
                        else if (jreVersion.equals("1.4"))
                            return Version.IBM14;
                    }
                }
            }
        }

        if ((classes = snapshot.getClassesByName("com.ibm.misc.JavaRuntimeVersion", false)) != null && !classes.isEmpty())return Version.IBM15; //$NON-NLS-1$
        else if ((classes = snapshot.getClassesByName("com.ibm.oti.vm.BootstrapClassLoader", false)) != null && !classes.isEmpty())return Version.IBM16; //$NON-NLS-1$
        else if ((classes = snapshot.getClassesByName("com.ibm.jvm.Trace", false)) != null && !classes.isEmpty())return Version.IBM14; //$NON-NLS-1$

        classes = snapshot.getClassesByName("sun.misc.Version", false);
        if (classes != null && classes.size() > 0)
        {
            Object ver = classes.iterator().next().resolveValue("java_version");
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("1.8.")) { return Version.JAVA18; }
        }
        return Version.SUN;
    }

    public interface Version
    {
        int SUN = 1 << 0;
        int IBM14 = 1 << 1;
        int IBM15 = 1 << 2;
        int IBM16 = 1 << 3; // Harmony based collections
        int IBM17 = 1 << 4; // Oracle based collections
        int IBM18 = 1 << 5;
        int JAVA18 = 1 << 6;

        int ALL = ~0;
    }

    public static class Info
    {
        final public String className;
        final public int version;
        final public ICollectionExtractor extractor;

        public Info(String className, ICollectionExtractor extractor)
        {
            this(className, Version.ALL, extractor);
        }

        public Info(String className, int version, ICollectionExtractor extractor)
        {
            if (className == null)
                throw new IllegalArgumentException();
            if (extractor == null)
                throw new IllegalArgumentException();
            this.className = className;
            this.version = version;
            this.extractor = extractor;
        }
    }

    public static Info[] knownCollections = new Info[] {
                    // these are always empty
                    new Info("java.util.Collections$EmptyList", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new Info("java.util.Collections$EmptySet", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new Info("java.util.Collections$EmptyMap", new EmptyMapExtractor()), //$NON-NLS-1$
                    new Info("com.sap.engine.lib.util.AbstractDataStructure", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new Info("java.util.concurrent.SynchronousQueue", new EmptyCollectionExtractor()), //$NON-NLS-1$

                    // these have a field indicating the size
                    new Info(
                                    "java.util.concurrent.ConcurrentLinkedBlockingDeque", new FieldSizedCollectionExtractor("count")), //$NON-NLS-1$  //$NON-NLS-2$
                    new Info(
                                    "java.util.concurrent.ConcurrentLinkedBlockingQueue", new FieldSizedCollectionExtractor("count.value")), //$NON-NLS-1$  //$NON-NLS-2$
                    new Info("java.util.concurrent.LinkedBlockingDeque", new FieldSizedCollectionExtractor("count")), //$NON-NLS-1$  //$NON-NLS-2$
                    new Info(
                                    "java.util.concurrent.LinkedBlockingQueue", new FieldSizedCollectionExtractor("count.value")), //$NON-NLS-1$  //$NON-NLS-2$

                    // these store the data in an array
                    new Info("java.util.concurrent.CopyOnWriteArrayList", new FieldArrayCollectionExtractor("array")), // //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.concurrent.CopyOnWriteArraySet", new FieldArrayCollectionExtractor("al.array")), // //$NON-NLS-1$ //$NON-NLS-2$
                    // Calculations not yet quite correct for these
                    //new Info("java.util.concurrent.ConcurrentLinkedDeque", new LinkedListCollectionExtractor("head")), // //$NON-NLS-1$ //$NON-NLS-2$
                    //new Info("java.util.concurrent.ConcurrentLinkedQueue", new LinkedListCollectionExtractor("head")), // //$NON-NLS-1$ //$NON-NLS-2$
                    //new Info("java.util.concurrent.LinkedTransferQueue", new LinkedListCollectionExtractor("head")), // //$NON-NLS-1$ //$NON-NLS-2$

                    // both size and array field
                    new Info(
                                    "java.util.ArrayList", ~Version.IBM16, new FieldSizeArrayCollectionExtractor("size", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.LinkedList", ~Version.IBM16, new LinkedListCollectionExtractor("size", "header")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.LinkedList", Version.IBM16, new LinkedListCollectionExtractor("size", "voidLink")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info("java.util.Vector", new FieldSizeArrayCollectionExtractor("elementCount", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.PriorityQueue", ~(Version.IBM15 | Version.IBM16), new FieldSizeArrayCollectionExtractor("size", "queue")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.PriorityQueue", Version.IBM15 | Version.IBM16, new FieldSizeArrayCollectionExtractor("size", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.concurrent.DelayQueue", ~(Version.IBM15 | Version.IBM16), new FieldSizeArrayCollectionExtractor("q.size", "q.queue")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.concurrent.DelayQueue", Version.IBM15 | Version.IBM16, new FieldSizeArrayCollectionExtractor("q.size", "q.elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // IBM 6 specific collections
                    new Info(
                                    "java.util.ArrayList", Version.IBM16, new IBM6ArrayListCollectionExtractor("firstIndex", "lastIndex", "array")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info(
                                    "java.util.ArrayDeque", ~Version.IBM16, new IBM6ArrayListCollectionExtractor("head", "tail", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info(
                                    "java.util.ArrayDeque", Version.IBM16, new IBM6ArrayListCollectionExtractor("front", "rear", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // TODO Find how to find collisions Identity map
                    new Info(
                                    "java.util.IdentityHashMap", ~(Version.IBM14 | Version.IBM15 | Version.IBM16), new IdentityHashMapCollectionExtractor("size", "table")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.IdentityHashMap", Version.IBM14 | Version.IBM15, new IdentityHashMapCollectionExtractor("size", "table")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info(
                                    "java.util.IdentityHashMap", Version.IBM16, new IdentityHashMapCollectionExtractor("size", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // hash maps
                    new Info(
                                    "java.util.HashMap", ~Version.IBM16, new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.HashMap", Version.IBM16, new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashMap to list
                    // This is the same as HashMap
                    new Info(
                                    "java.util.LinkedHashMap", Version.IBM15, new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "java.beans.beancontext.BeanContextSupport", ~Version.IBM16, new HashMapCollectionExtractor("children.size", "children.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.beans.beancontext.BeanContextSupport", Version.IBM16, new HashMapCollectionExtractor("children.elementCount", "children.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "com.ibm.jvm.util.HashMapRT", Version.IBM15 | Version.IBM16, new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "java.util.HashSet", ~Version.IBM16, new HashSetCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.HashSet", Version.IBM16, // //$NON-NLS-1$
                                    new HashSetCollectionExtractor(
                                                    "backingMap.elementCount", "backingMap.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "javax.script.SimpleBindings", ~Version.IBM16, new HashMapCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info("javax.script.SimpleBindings", Version.IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor(
                                                    "map.elementCount", "map.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "java.util.jar.Attributes", ~Version.IBM16, new HashMapCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info("java.util.jar.Attributes", Version.IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor(
                                                    "map.elementCount", "map.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashSet to list
                    // This is the same as HashSet
                    new Info(
                                    "java.util.LinkedHashSet", Version.IBM15, new HashSetCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.Hashtable", ~(Version.IBM15 | Version.IBM16), new HashMapCollectionExtractor("count", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info("java.util.Hashtable", Version.IBM15 | Version.IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // Some Java 5 PHD files don't have superclass info so add
                    // Properties to list
                    // This is the same as Hashtable
                    new Info("java.util.Properties", Version.IBM15 | Version.IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "java.util.WeakHashMap", ~Version.IBM16, new HashMapCollectionExtractor("size", "table", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.WeakHashMap", Version.IBM16, new HashMapCollectionExtractor("elementCount", "elementData", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "java.lang.ThreadLocal$ThreadLocalMap", Version.IBM14 | Version.IBM15 | Version.IBM16 | Version.IBM17 | Version.IBM18 | Version.SUN | Version.JAVA18, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("size", "table", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "java.util.concurrent.ConcurrentHashMap$Segment", new HashMapCollectionExtractor("count", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // FIXME This is only approximate and just works for some
                    // small maps.
                    new Info(
                                    "java.util.concurrent.ConcurrentHashMap", Version.JAVA18 | Version.IBM18, new HashMapCollectionExtractor("baseCount", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info("java.util.concurrent.ConcurrentSkipListSet", Version.ALL,
                                    new ConcurrentSkipListCollectionExtractor("m.head.node", "key", "value")),
                    new Info("java.util.concurrent.ConcurrentSkipListMap", Version.ALL,
                                    new ConcurrentSkipListCollectionExtractor("head.node", "key", "value")),

                    // tree maps
                    new Info(
                                    "java.util.TreeMap", ~Version.IBM16, new TreeMapCollectionExtractor("size", "key", "value")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info(
                                    "java.util.TreeMap", Version.IBM16, new TreeMapCollectionExtractor("size", "keys[]", "values[]")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info(
                                    "java.util.TreeSet", ~Version.IBM16, new TreeMapCollectionExtractor("m.size", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info(
                                    "java.util.TreeSet", Version.IBM16, new TreeMapCollectionExtractor("backingMap.size", "keys[]", "values[]")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // concurrent hash map
                    new Info(
                                    "java.util.concurrent.ConcurrentHashMap", ~(Version.JAVA18 | Version.IBM18), new ConcurrentHashMapCollectionExtractor("segments", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // wrappers
                    // also works for SynchronizedSet, SynchronizedSortedSet,
                    // SynchronizedList, SynchronizedRandomAccessList
                    new Info("java.util.Collections$SynchronizedCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for: UnmodifiableSet, UnmodifiableSortedSet,
                    // UnmodifiableList, UnmodifiableRandomAccessList
                    // UnmodifiableEntrySet
                    new Info("java.util.Collections$UnmodifiableCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for UnmodifiableSortedMap
                    new Info("java.util.Collections$UnmodifiableMap", new WrapperMapExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for SynchronizedSortedMap
                    new Info("java.util.Collections$SynchronizedMap", new WrapperMapExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for CheckedSet, CheckedSortedSet, CheckedList,
                    // CheckedRandomAccessList
                    new Info("java.util.Collections$CheckedCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for CheckedSortedMap
                    new Info("java.util.Collections$CheckedMap", new WrapperCollectionExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.Collections$CheckedMap$CheckedEntrySet", new WrapperCollectionExtractor("s")), //$NON-NLS-1$ //$NON-NLS-2$

                    // singletons
                    new Info("java.util.Collections$SingletonSet", new SingletonCollectionExtractor("element")), //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.Collections$SingletonList", new SingletonCollectionExtractor("element")), //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.Collections$SingletonMap", new SingletonMapExtractor("k", "v")),//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info("java.util.Collections$CopiesList", new ReplicatedValueCollectionExtractor("n", "element")),//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // These are known un-implemented ones
                    new Info("sun.util.PreHashedMap", new NoContentCollectionExtractor()), //$NON-NLS-1$
                    new Info("sun.misc.SoftCache", new NoContentCollectionExtractor()), //$NON-NLS-1$

                    // usually shouldn't match
                    new Info("java.util.AbstractList", new NoContentCollectionExtractor()), //$NON-NLS-1$
    };
}
