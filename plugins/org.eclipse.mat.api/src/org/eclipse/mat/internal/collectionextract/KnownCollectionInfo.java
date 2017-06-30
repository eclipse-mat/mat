/*******************************************************************************
 * Copyright (c) 2008, 2016 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *                       introduce CollectionExtractor extension
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM14;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM15;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM16;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM18;
import static org.eclipse.mat.snapshot.extension.JdkVersion.JAVA18;

import java.util.Arrays;
import java.util.List;

import org.eclipse.mat.snapshot.extension.CollectionExtractionInfo;
import org.eclipse.mat.snapshot.extension.ICollectionExtractorProvider;
import org.eclipse.mat.snapshot.extension.JdkVersion;
import org.eclipse.mat.snapshot.registry.CollectionExtractorProviderRegistry;

public class KnownCollectionInfo implements ICollectionExtractorProvider
{
    public List<CollectionExtractionInfo> getExtractorInfo() {
        return Arrays.asList(knownCollections);
    }

    private static CollectionExtractionInfo[] knownCollections = new CollectionExtractionInfo[] {
                    // these are always empty
                    new CollectionExtractionInfo("java.util.Collections$EmptyList", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new CollectionExtractionInfo("java.util.Collections$EmptySet", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new CollectionExtractionInfo("java.util.Collections$EmptyMap", new EmptyMapExtractor()), //$NON-NLS-1$
                    new CollectionExtractionInfo("com.sap.engine.lib.util.AbstractDataStructure", new EmptyCollectionExtractor()), //$NON-NLS-1$
                    new CollectionExtractionInfo("java.util.concurrent.SynchronousQueue", new EmptyCollectionExtractor()), //$NON-NLS-1$

                    // these have a field indicating the size
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentLinkedBlockingDeque", new LinkedListCollectionExtractor("count", "first.next")), //$NON-NLS-1$  //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentLinkedBlockingQueue", new LinkedListCollectionExtractor("count.value", "head.next")), //$NON-NLS-1$  //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.LinkedBlockingDeque", new LinkedListCollectionExtractor("count", "first.next")), //$NON-NLS-1$  //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.LinkedBlockingQueue", new LinkedListCollectionExtractor("count.value", "head.next")), //$NON-NLS-1$  //$NON-NLS-2$

                    // these store the data in an array
                    new CollectionExtractionInfo("java.util.concurrent.CopyOnWriteArrayList", new FieldArrayCollectionExtractor("array")), // //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.CopyOnWriteArraySet", new FieldArrayCollectionExtractor("al.array")), // //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentLinkedDeque", new LinkedListCollectionExtractor(null, "head.next")), // //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentLinkedQueue", new LinkedListCollectionExtractor(null, "head.next")), // //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.concurrent.LinkedTransferQueue", new LinkedListCollectionExtractor(null, "head.next")), // //$NON-NLS-1$ //$NON-NLS-2$

                    // both size and array field
                    new CollectionExtractionInfo("java.util.ArrayList", JdkVersion.except(IBM16), new FieldSizeArrayCollectionExtractor("size", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.LinkedList", JdkVersion.except(IBM16), new LinkedListCollectionExtractor("size", "header")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.LinkedList", IBM16, new LinkedListCollectionExtractor("size", "voidLink")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.Vector", new FieldSizeArrayCollectionExtractor("elementCount", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.PriorityQueue", JdkVersion.except(IBM15, IBM16), new FieldSizeArrayCollectionExtractor("size", "queue")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.PriorityQueue", JdkVersion.of(IBM15, IBM16), new FieldSizeArrayCollectionExtractor("size", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.concurrent.DelayQueue", JdkVersion.except(IBM15, IBM16), new FieldSizeArrayCollectionExtractor("q.size", "q.queue")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.concurrent.DelayQueue", JdkVersion.of(IBM15, IBM16), new FieldSizeArrayCollectionExtractor("q.size", "q.elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // IBM 6 specific collections
                    new CollectionExtractionInfo("java.util.ArrayList", IBM16, new IBM6ArrayListCollectionExtractor("firstIndex", "lastIndex", "array")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new CollectionExtractionInfo("java.util.ArrayDeque", JdkVersion.except(IBM16), new IBM6ArrayListCollectionExtractor("head", "tail", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new CollectionExtractionInfo("java.util.ArrayDeque", IBM16, new IBM6ArrayListCollectionExtractor("front", "rear", "elements")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // TODO Find how to find collisions Identity map
                    new CollectionExtractionInfo("java.util.IdentityHashMap", JdkVersion.except(IBM14, IBM15, IBM16), new IdentityHashMapCollectionExtractor("size", "table")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.IdentityHashMap", JdkVersion.of(IBM14, IBM15), new IdentityHashMapCollectionExtractor("size", "table")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.IdentityHashMap", IBM16, new IdentityHashMapCollectionExtractor("size", "elementData")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // hash maps
                    new CollectionExtractionInfo("java.util.HashMap", JdkVersion.except(IBM16), new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.HashMap", IBM16, new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashMap to list
                    // This is the same as HashMap
                    new CollectionExtractionInfo("java.util.LinkedHashMap", IBM15, new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new CollectionExtractionInfo("java.beans.beancontext.BeanContextSupport", JdkVersion.except(IBM16), new HashSetCollectionExtractor("children.size", "children.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.beans.beancontext.BeanContextSupport", IBM16, new HashSetCollectionExtractor("children.elementCount", "children.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new CollectionExtractionInfo("com.ibm.jvm.util.HashMapRT", JdkVersion.of(IBM15, IBM16), new HashMapCollectionExtractor("size", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new CollectionExtractionInfo("java.util.HashSet", JdkVersion.except(IBM16), new HashSetCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.HashSet", IBM16, // //$NON-NLS-1$
                             new HashSetCollectionExtractor(
                                                    "backingMap.elementCount", "backingMap.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new CollectionExtractionInfo("javax.script.SimpleBindings", JdkVersion.except(IBM16), new HashMapCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("javax.script.SimpleBindings", IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor(
                                                    "map.elementCount", "map.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new CollectionExtractionInfo("java.util.jar.Attributes", JdkVersion.except(IBM16), new HashMapCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.jar.Attributes", IBM16, // //$NON-NLS-1$
                                    new HashMapCollectionExtractor(
                                                    "map.elementCount", "map.elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashSet to list
                    // This is the same as HashSet
                    new CollectionExtractionInfo("java.util.LinkedHashSet", IBM15, new HashSetCollectionExtractor("map.size", "map.table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.Hashtable", JdkVersion.except(IBM15, IBM16), new HashMapCollectionExtractor("count", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.Hashtable", JdkVersion.of(IBM15, IBM16), // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // Some Java 5 PHD files don't have superclass info so add
                    // Properties to list
                    // This is the same as Hashtable
                    new CollectionExtractionInfo("java.util.Properties", JdkVersion.of(IBM15, IBM16), // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("elementCount", "elementData", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new CollectionExtractionInfo("java.util.WeakHashMap", JdkVersion.except(IBM16), new HashMapCollectionExtractor("size", "table", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.WeakHashMap", IBM16, new HashMapCollectionExtractor("elementCount", "elementData", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new CollectionExtractionInfo("java.lang.ThreadLocal$ThreadLocalMap", // //$NON-NLS-1$
                                    new HashMapCollectionExtractor("size", "table", "referent", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentHashMap$Segment", new HashMapCollectionExtractor("count", "table", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // FIXME This is only approximate and just works for some
                    // small maps.
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentHashMap", JdkVersion.of(JAVA18, IBM18), new HashMapCollectionExtractor("baseCount", "table", "key", "val")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentHashMap", JdkVersion.except(JAVA18, IBM18), new ConcurrentHashMapCollectionExtractor("segments", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentSkipListSet",
                                    new ConcurrentSkipListSetCollectionExtractor("m.head.node", "key", "value")),
                    new CollectionExtractionInfo("java.util.concurrent.ConcurrentSkipListMap",
                                    new ConcurrentSkipListCollectionExtractor("head.node", "key", "value")),

                    // tree maps
                    new CollectionExtractionInfo("java.util.TreeMap", JdkVersion.except(IBM16), new TreeMapCollectionExtractor("size", "key", "value")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new CollectionExtractionInfo("java.util.TreeMap", IBM16, new TreeMapCollectionExtractor("size", "keys[]", "values[]")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new CollectionExtractionInfo("java.util.TreeSet", JdkVersion.except(IBM16), new TreeSetCollectionExtractor("m.size", "key", "value")), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new CollectionExtractionInfo("java.util.TreeSet", IBM16, new TreeSetCollectionExtractor("backingMap.size", "keys[]", "values[]")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // wrappers
                    // also works for SynchronizedSet, SynchronizedSortedSet,
                    // SynchronizedList, SynchronizedRandomAccessList
                    new CollectionExtractionInfo("java.util.Collections$SynchronizedCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for: UnmodifiableSet, UnmodifiableSortedSet,
                    // UnmodifiableList, UnmodifiableRandomAccessList
                    // UnmodifiableEntrySet
                    new CollectionExtractionInfo("java.util.Collections$UnmodifiableCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for UnmodifiableSortedMap
                    new CollectionExtractionInfo("java.util.Collections$UnmodifiableMap", new WrapperMapExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for SynchronizedSortedMap
                    new CollectionExtractionInfo("java.util.Collections$SynchronizedMap", new WrapperMapExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for CheckedSet, CheckedSortedSet, CheckedList,
                    // CheckedRandomAccessList
                    new CollectionExtractionInfo("java.util.Collections$CheckedCollection", new WrapperCollectionExtractor("c")), //$NON-NLS-1$ //$NON-NLS-2$
                    // also works for CheckedSortedMap
                    new CollectionExtractionInfo("java.util.Collections$CheckedMap", new WrapperCollectionExtractor("m")), //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.Collections$CheckedMap$CheckedEntrySet", new WrapperCollectionExtractor("s")), //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("org.eclipse.osgi.framework.util.CaseInsensitiveDictionaryMap", new WrapperMapExtractor("map")), //$NON-NLS-1$ //$NON-NLS-2$


                    // singletons
                    new CollectionExtractionInfo("java.util.Collections$SingletonSet", new SingletonCollectionExtractor("element")), //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.Collections$SingletonList", new SingletonCollectionExtractor("element")), //$NON-NLS-1$ //$NON-NLS-2$
                    new CollectionExtractionInfo("java.util.Collections$SingletonMap", new SingletonMapExtractor("k", "v")),//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new CollectionExtractionInfo("java.util.Collections$CopiesList", new ReplicatedValueCollectionExtractor("n", "element")),//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // These are known un-implemented ones
                    new CollectionExtractionInfo("sun.util.PreHashedMap", new NoContentCollectionExtractor()), //$NON-NLS-1$
                    new CollectionExtractionInfo("sun.misc.SoftCache", new NoContentCollectionExtractor()), //$NON-NLS-1$

                    // usually shouldn't match
                    new CollectionExtractionInfo("java.util.AbstractList", new NoContentCollectionExtractor()), //$NON-NLS-1$
    };
}
