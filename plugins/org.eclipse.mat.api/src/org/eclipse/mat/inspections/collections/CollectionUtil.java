/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - detect IBM 1.4/1.5/1.6 VM, more collections
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public final class CollectionUtil
{
    // //////////////////////////////////////////////////////////////
    // meta information about known collections
    // //////////////////////////////////////////////////////////////

    public static class Info
    {
        private String className;
        private int version;

        private String sizeField;
        private String arrayField;

        private String keyField;
        private String valueField;

        private ICollectionExtractor collectionExtractor;

        /* package */Info(String className, int version, String sizeField, String arrayField)
        {
            this(className, version, sizeField, arrayField, null, null);
        }

        public Info(String className, String sizeField, String arrayField)
        {
            this(className, sizeField, arrayField, null, null);
        }

        /* package */Info(String className, int version, String sizeField, String arrayField, String keyField,
                        String valueField)
        {
            this.className = className;
            this.version = version;
            this.sizeField = sizeField;
            this.arrayField = arrayField;
            this.keyField = keyField;
            this.valueField = valueField;
        }

        /* package */Info(String className, int version, String sizeField, String arrayField, String keyField,
                        String valueField, ICollectionExtractor collectionExtractor)
        {
            this.className = className;
            this.version = version;
            this.sizeField = sizeField;
            this.arrayField = arrayField;
            this.keyField = keyField;
            this.valueField = valueField;
            this.collectionExtractor = collectionExtractor;
        }

        public Info(String className, String sizeField, String arrayField, String keyField, String valueField)
        {
            this(className, ~0, sizeField, arrayField, keyField, valueField);
        }

        public String getClassName()
        {
            return className;
        }

        public boolean hasSize()
        {
            return sizeField != null;
        }

        /**
         * Gets the size of the collection First try using the size field Then
         * try using the filled entries in the backing array and the chained
         * entries if it is a map.
         * 
         * @param collection
         * @return size of collection or 0 if unknown
         * @throws SnapshotException
         */
        public int getSize(IObject collection) throws SnapshotException
        {
            Object value = collection.resolveValue(sizeField);
            int ret = 0;
            // Allow for int or long
            if (value instanceof Integer)
            {
                ret = (Integer)value;
            }
            else if (value instanceof Long)
            {
                ret = ((Long)value).intValue();
            }
            else
            {
                if (hasBackingArray())
                {
                    IObjectArray array = getBackingArray(collection);
                    if (array != null)
                    {
                        if (!isMap())
                        {
                            // E.g. ArrayList
                            int count = CollectionUtil.getNumberOfNoNullArrayElements(array);
                            ret = count;
                        }
                        else
                        {
                            int count = getMapSize(collection, array);
                            ret = count;
                        }
                    }
                }
                else if (arrayField != null)
                {
                    // LinkedList
                    IObject header = resolveNextFields(collection);
                    if (header != null)
                    {
                        int count = getMapSize(collection, header);
                        ret = count;
                    }
                }
            }
            return ret;
        }

        private int getMapSize(IObject collection, IObject array) throws SnapshotException
        {
            // Maps have chained buckets in case of clashes
            // LinkedMaps have additional chains to maintain ordering
            int count = 0;
            ISnapshot snapshot = array.getSnapshot();
            // Avoid visiting nodes twice
            BitField seen = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
            // Used for alternative nodes if there is a choice
            ArrayInt extra = new ArrayInt();
            // Eliminate the LinkedHashMap header node
            seen.set(array.getObjectId());
            // Walk over whole array, or all outbounds of header
            for (int i : snapshot.getOutboundReferentIds(array.getObjectId()))
            {
                // Ignore classes, outbounds we have seen, and plain Objects (which can't be buckets e.g. ConcurrentSkipListMap) 
                if (!snapshot.isClass(i) && !seen.get(i) && !snapshot.getClassOf(i).getName().equals("java.lang.Object")) //$NON-NLS-1$
                {
                    // Found a new outbound
                    // Look at the reachable nodes from this one, remember this
                    extra.clear();
                    extra.add(i);
                    seen.set(i);
                    for (int k = 0; k < extra.size(); ++k)
                    {
                        for (int j = extra.get(k); j >= 0;)
                        {
                            ++count;
                            j = resolveNextSameField(snapshot, j, seen, extra);
                        }
                    }
                }
            }
            return count;
        }

        /**
         * Get the only object field from the object Used for finding the
         * HashMap from the HashSet
         * 
         * @param source
         * @return null if non or duplicates found
         * @throws SnapshotException
         */
        private IInstance resolveNextField(IObject source) throws SnapshotException
        {
            final ISnapshot snapshot = source.getSnapshot();
            IInstance ret = null;
            for (int i : snapshot.getOutboundReferentIds(source.getObjectId()))
            {
                if (!snapshot.isArray(i) && !snapshot.isClass(i))
                {
                    IObject o = snapshot.getObject(i);
                    if (o instanceof IInstance)
                    {
                        if (ret != null)
                        {
                            ret = null;
                            break;
                        }
                        ret = (IInstance) o;
                    }
                }
            }
            return ret;
        }

        /**
         * Get the only object field from the object which is of the same type
         * as the source
         * 
         * @param sourceId
         * @param seen
         *            whether seen yet
         * @param extra
         *            extra ones to do
         * @return the next node to search, null if none found
         * @throws SnapshotException
         */
        int resolveNextSameField(ISnapshot snapshot, int sourceId, BitField seen, ArrayInt extra)
                        throws SnapshotException
        {
            int ret = -1;
            IClass c1 = snapshot.getClassOf(sourceId);
            for (int i : snapshot.getOutboundReferentIds(sourceId))
            {
                if (!snapshot.isArray(i) && !snapshot.isClass(i))
                {
                    IClass c2 = snapshot.getClassOf(i);
                    if (c1.equals(c2) && !seen.get(i))
                    {
                        seen.set(i);
                        if (ret == -1)
                        {
                            ret = i;
                        }
                        else
                        {
                            extra.add(i);
                        }
                    }
                }
            }
            return ret;
        }

        public boolean hasBackingArray()
        {
            return arrayField != null && !arrayField.endsWith("."); //$NON-NLS-1$
        }

        public IObjectArray getBackingArray(IObject collection) throws SnapshotException
        {
            if (arrayField == null)
                return null;
            final Object obj = collection.resolveValue(arrayField);
            IObjectArray ret = null;
            if (obj instanceof IObjectArray)
            {
                ret = (IObjectArray) obj;
                return ret;
            }
            else if (obj instanceof IObject)
            {
                String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                                collection.getTechnicalName(), ((IObject) obj).getTechnicalName());
                throw new SnapshotException(msg);
            }
            else if (obj != null)
            {
                String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                                collection.getTechnicalName(), obj.toString());
                throw new SnapshotException(msg);
            }
            IObject next = resolveNextFields(collection);
            if (next == null)
                return null;
            // Look for the only object array field
            final ISnapshot snapshot = next.getSnapshot();
            for (int i : snapshot.getOutboundReferentIds(next.getObjectId()))
            {
                if (snapshot.isArray(i))
                {
                    IObject o = snapshot.getObject(i);
                    if (o instanceof IObjectArray)
                    {
                        // Have we already found a possible return type?
                        // If so, things are uncertain and so give up.
                        if (ret != null)
                            return null;
                        ret = (IObjectArray) o;
                    }
                }
            }
            return ret;
        }

        IObject resolveNextFields(IObject collection) throws SnapshotException
        {
            int j = arrayField.lastIndexOf('.');
            if (j >= 0)
            {
                Object ret = collection.resolveValue(arrayField.substring(0, j));
                if (ret instanceof IObject)
                {
                    return (IObject) ret;
                }
            }
            // Find out how many fields to chain through to find the array
            IObject next = collection;
            // Don't do the last as that is the array field
            for (int i = arrayField.indexOf('.'); i >= 0 && next != null; i = arrayField.indexOf('.', i + 1))
            {
                next = resolveNextField(next);
            }
            return next;
        }

        public String getBackingArrayField()
        {
            return arrayField;
        }

        public boolean isMap()
        {
            return keyField != null || collectionExtractor != null;
        }

        public String getEntryKeyField()
        {
            return keyField;
        }

        public String getEntryValueField()
        {
            return valueField;
        }

        public ICollectionExtractor getCollectionExtractor()
        {
            return collectionExtractor;
        }

        /* package */Info setCollectionExtractor(ICollectionExtractor collectionExtractor)
        {
            this.collectionExtractor = collectionExtractor;
            return this;
        }

        /**
         */
        public int getNumberOfNoNullArrayElements(IObject collection) throws SnapshotException
        {
            IObjectArray arrayObject = getBackingArray(collection);
            if (arrayObject == null)
                return 0;
            return CollectionUtil.getNumberOfNoNullArrayElements(arrayObject);
        }

        public int getCapacity(IObject collection) throws SnapshotException
        {
            IObjectArray arrayObject = getBackingArray(collection);
            if (arrayObject == null)
                return 0;
            IObjectArray table = getBackingArray(collection);

            if (table == null) { return 0; }
            return table.getLength();
        }
        
        public String toString() {
            return this.className+" "+this.sizeField+" "+this.arrayField;  //$NON-NLS-1$//$NON-NLS-2$
        }
    }

    /**
     * Info for IdentityHashMaps
     * These are stored as key/value pairs in an array
     */
    public static class IdentityInfo extends Info
    {

        IdentityInfo(String className, int version, String sizeField, String arrayField)
        {
            super(className, version, sizeField, arrayField, null, null, IDENTITY_HASH_MAP_EXTRACTOR);
        }
        
        /**
         * Gets the capacity of the map.
         * Needs two array elements for each entry.
         */
        public int getCapacity(IObject collection) throws SnapshotException {
            return super.getCapacity(collection) / 2;
        }
    }

    public static List<Info> getKnownCollections(ISnapshot snapshot) throws SnapshotException
    {
        int version = resolveVersion(snapshot);

        List<Info> answer = new ArrayList<Info>(knownCollections.length);

        for (Info info : knownCollections)
        {
            if ((info.version & version) == version)
                answer.add(info);
        }

        return answer;
    }

    public static HashMapIntObject<CollectionUtil.Info> getKnownMaps(ISnapshot snapshot) throws SnapshotException
    {
        HashMapIntObject<CollectionUtil.Info> answer = new HashMapIntObject<Info>();

        for (Info info : getKnownCollections(snapshot))
        {
            if (!info.isMap())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    answer.put(clasz.getObjectId(), info);
        }

        return answer;
    }

    public static Info getInfo(IObject object) throws SnapshotException
    {
        List<Info> known = getKnownCollections(object.getSnapshot());

        int len = known.size();
        for (int ii = len - 1; ii > 0; ii--)
        {
            Info info = known.get(ii);
            if (object.getClazz().doesExtend(info.getClassName()))
                return info;
        }
        return null;
    }

    public static Info getInfo(String className)
    {
        for (Info info : knownCollections)
        {
            if (info.getClassName().equals(className))
                return info;
        }
        return null;
    }

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    public static int getNumberOfNoNullArrayElements(IObjectArray arrayObject)
    {
        // Fast path using referentIds for arrays with same number of outbounds
        // (+class id) as length
        // or no outbounds other than the class
        ISnapshot snapshot = arrayObject.getSnapshot();
        try
        {
            final int[] outs = snapshot.getOutboundReferentIds(arrayObject.getObjectId());
            if (outs.length == 1 || outs.length == arrayObject.getLength() + 1) { return outs.length - 1; }
        }
        catch (SnapshotException e)
        {}
        long[] elements = arrayObject.getReferenceArray();
        int result = 0;
        for (int i = 0; i < elements.length; i++)
        {
            if (elements[i] != 0)
                result++;
        }
        return result;
    }

    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////

    private CollectionUtil()
    {}

    private interface Version
    {
        int SUN = 1 << 0;
        int IBM14 = 1 << 1;
        int IBM15 = 1 << 2;
        int IBM16 = 1 << 3; // Harmony based collections
        int IBM17 = 1 << 4; // Oracle based collections
        int IBM18 = 1 << 5;
        int JAVA18 = 1 << 6;
    }

    /* package */static ICollectionExtractor HASH_MAP_EXTRACTOR = new HashMapEntryExtractor();
    /* package */static ICollectionExtractor CONCURRENT_HASH_MAP_EXTRACTOR = new ConcurrentHashMapEntryExtractor();
    /* package */static ICollectionExtractor TREE_MAP_EXTRACTOR = new TreeMapEntryExtractor();
    /* package */static ICollectionExtractor IDENTITY_HASH_MAP_EXTRACTOR = new IdentityHashMapEntryExtractor();

    @SuppressWarnings("nls")
    private static Info[] knownCollections = new Info[] {
                    new Info("java.util.AbstractList", null, null), // //$NON-NLS-1$

                    // use "" to make the size 0
                    new Info("java.util.Collections$EmptyList", "", null), //$NON-NLS-1$ //$NON-NLS-2$

                    new Info("java.util.ArrayList", ~Version.IBM16, "size", "elementData"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new IBM6ArrayListInfo("java.util.ArrayList", Version.IBM16, "firstIndex", "lastIndex", "array"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new IBM6ArrayListInfo("java.util.ArrayDeque", ~Version.IBM16, "head", "tail", "elements"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new IBM6ArrayListInfo("java.util.ArrayDeque", Version.IBM16, "front", "rear", "elements"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info("java.util.LinkedList", ~Version.IBM16, "size", "header."), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info("java.util.LinkedList", Version.IBM16, "size", "voidLink."), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    new Info("java.util.HashMap", ~Version.IBM16, "size", "table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.HashMap", Version.IBM16, "elementCount", "elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashMap to list
                    // This is the same as HashMap
                    new Info(
                                    "java.util.LinkedHashMap", Version.IBM15, "size", "table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                                    
                    new Info("java.beans.beancontext.BeanContextSupport", ~Version.IBM16, "children.size", "children.table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info("java.beans.beancontext.BeanContextSupport", Version.IBM16, "children.elementCount", "children.elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "com.ibm.jvm.util.HashMapRT", Version.IBM15 | Version.IBM16, "size", "table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    // TODO Find how to find collisions Identity map
                    new IdentityInfo("java.util.IdentityHashMap", ~(Version.IBM14 | Version.IBM15 | Version.IBM16), "size", "table"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new IdentityInfo("java.util.IdentityHashMap", Version.IBM14 | Version.IBM15, "size", "table"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new IdentityInfo("java.util.IdentityHashMap", Version.IBM16, "size", "elementData"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    // use "" to make the size 0
                    new Info("java.util.Collections$EmptySet", "", null), //$NON-NLS-1$ //$NON-NLS-2$
                    // use "" to make the size 0
                    new Info("java.util.Collections$EmptyMap", "", null), //$NON-NLS-1$ //$NON-NLS-2$

                    new Info(
                                    "java.util.HashSet", ~Version.IBM16, "map.size", "map.table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.HashSet", Version.IBM16, // //$NON-NLS-1$
                                    "backingMap.elementCount", "backingMap.elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "javax.script.SimpleBindings", ~Version.IBM16, "map.size", "map.table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "javax.script.SimpleBindings", Version.IBM16, // //$NON-NLS-1$
                                    "map.elementCount", "map.elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                   "java.util.jar.Attributes", ~Version.IBM16, "map.size", "map.table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                   "java.util.jar.Attributes", Version.IBM16, // //$NON-NLS-1$
                                   "map.elementCount", "map.elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$


                    // Some Java 5 PHD files don't have superclass info so add
                    // LinkedHashSet to list
                    // This is the same as HashSet
                    new Info(
                                    "java.util.LinkedHashSet", Version.IBM15, "map.size", "map.table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info("java.util.TreeMap", ~Version.IBM16, "size", null, "key", "value").setCollectionExtractor(TREE_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info("java.util.TreeMap", Version.IBM16, "size", null, "keys[]", "values[]").setCollectionExtractor(TREE_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info("java.util.TreeSet", ~Version.IBM16, "m.size", null, "key", "value").setCollectionExtractor(TREE_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    new Info("java.util.TreeSet", Version.IBM16, "backingMap.size", null, "keys[]", "values[]").setCollectionExtractor(TREE_MAP_EXTRACTOR), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info(
                                    "java.util.Hashtable", ~(Version.IBM15 | Version.IBM16), "count", "table", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info("java.util.Hashtable", Version.IBM15 | Version.IBM16, // //$NON-NLS-1$
                                    "elementCount", "elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    // Some Java 5 PHD files don't have superclass info so add
                    // Properties to list
                    // This is the same as Hashtable
                    new Info("java.util.Properties", Version.IBM15|Version.IBM16, // //$NON-NLS-1$
                                    "elementCount", "elementData", "key", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info("java.util.Vector", "elementCount", "elementData"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                    new Info(
                                    "java.util.WeakHashMap", ~Version.IBM16, "size", "table", "referent", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    new Info(
                                    "java.util.WeakHashMap", Version.IBM16, "elementCount", "elementData", "referent", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info(
                                    "java.lang.ThreadLocal$ThreadLocalMap", Version.IBM14 | Version.IBM15 | Version.IBM16 | Version.IBM17 | Version.IBM18  | Version.SUN | Version.JAVA18, // //$NON-NLS-1$
                                    "size", "table", "referent", "value", HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

                    new Info("java.util.concurrent.ConcurrentHashMap$Segment", "count", "table", "key", "value").setCollectionExtractor(HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new ConcurrentHashMapInfo(), // special sub-class of Info
                                                 // for ConcurrentHashMap
                    
                    // FIXME This is only approximate and just works for some small maps.
                    new Info("java.util.concurrent.ConcurrentHashMap", Version.JAVA18 | Version.IBM18, "baseCount", "table", "key", "value").setCollectionExtractor(HASH_MAP_EXTRACTOR), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

                    new Info("com.sap.engine.lib.util.AbstractDataStructure", null, null), // //$NON-NLS-1$

                    new Info("java.util.concurrent.CopyOnWriteArrayList", "", "array"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info("java.util.concurrent.CopyOnWriteArraySet", "", "al.array"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new Info("java.util.PriorityQueue", ~(Version.IBM15|Version.IBM16), "size", "queue"), // //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.PriorityQueue", Version.IBM15 | Version.IBM16, "size", "elements"), // //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.concurrent.DelayQueue", ~(Version.IBM15|Version.IBM16), "q.size", "q.queue"), // //$NON-NLS-1$ //$NON-NLS-2$
                    new Info("java.util.concurrent.DelayQueue", Version.IBM15 | Version.IBM16, "q.size", "q.elements"), // //$NON-NLS-1$ //$NON-NLS-2$
                    // use "" to make the size 0
                    new Info("java.util.concurrent.SynchronousQueue", "", null),
                    new Info("java.util.concurrent.ConcurrentLinkedBlockingDeque", "count", null),
                    new Info("java.util.concurrent.ConcurrentLinkedBlockingQueue", "count.value", null),
                    new Info("java.util.concurrent.LinkedBlockingDeque", "count", null),
                    new Info("java.util.concurrent.LinkedBlockingQueue", "count.value", null),
                    
                    // Calculations not yet quite correct for these
                    //new Info("java.util.concurrent.ConcurrentLinkedDeque", "", "head."),
                    //new Info("java.util.concurrent.ConcurrentLinkedQueue", "", "head."),
                    //new Info("java.util.concurrent.LinkedTransferQueue", "", "head."),
                    
                    new Info("java.util.concurrent.ConcurrentSkipListSet", ~0, "", "m.head.node.", "key", "value", HASH_MAP_EXTRACTOR),
                    new Info("java.util.concurrent.ConcurrentSkipListMap", ~0, "", "head.node.", "key", "value", HASH_MAP_EXTRACTOR),
    };

    @SuppressWarnings("nls")
    private static int resolveVersion(ISnapshot snapshot) throws SnapshotException
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
                            if (jvmInfo.matches(".*\\(SR[1-3][^0-9].*") ||
                                jvmInfo.matches(".*\\(GA"))
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
        if (classes.size() > 0)
        {
            Object ver = classes.iterator().next().resolveValue("java_version");
            if (ver instanceof IObject && ((IObject)ver).getClassSpecificName().startsWith("1.8.")) {
                return Version.JAVA18;
            }
        }
        return Version.SUN;
    }

    private static class IBM6ArrayListInfo extends Info
    {
        private String firstIndex;

        public IBM6ArrayListInfo(String className, int version, String firstIndex, String lastIndex, String arrayField)
        {
            super(className, version, lastIndex, arrayField);
            this.firstIndex = firstIndex;
        }

        @Override
        public int getSize(IObject collection) throws SnapshotException
        {
            int lastIndex = super.getSize(collection);
            if (lastIndex <= 0)
                return lastIndex;

            Integer firstIndex = (Integer) collection.resolveValue(this.firstIndex);

            return lastIndex - (firstIndex == null ? 0 : firstIndex);
        }
    }

    private static class HashMapEntryExtractor implements ICollectionExtractor
    {

        public int[] extractEntries(int objectId, Info info, ISnapshot snapshot, IProgressListener listener)
                        throws SnapshotException
        {
            IInstance collection = (IInstance) snapshot.getObject(objectId);
            String collectionName = collection.getDisplayName();
            ArrayInt entries = new ArrayInt();

            // read table w/o loading the big table object!
            String arrayField = info.getBackingArrayField();
            int p = arrayField.lastIndexOf('.');
            if (p == arrayField.length() - 1 && p > 0) {
                // trailing dot indicates field is not actually an array
                arrayField = arrayField.substring(0, p);
                p = arrayField.lastIndexOf('.');
            }
            IInstance map = p < 0 ? (IInstance) collection : (IInstance) collection.resolveValue(arrayField.substring(
                            0, p));
            if (map != null)
            {
                Field tableField = map.getField(p < 0 ? arrayField : arrayField.substring(p + 1));
                if (tableField != null)
                {
                    final ObjectReference tableFieldValue = (ObjectReference) tableField.getValue();
                    if (tableFieldValue != null)
                    {
                        int tableObjectId = tableFieldValue.getObjectId();

                        int[] outbounds = snapshot.getOutboundReferentIds(tableObjectId);
                        for (int ii = 0; ii < outbounds.length; ii++)
                            collectEntry(entries, info, collection.getObjectId(), collectionName, outbounds[ii], snapshot,
                                            listener);
                    }
                }
            }

            return entries.toArray();
        }

        private void collectEntry(ArrayInt entries, CollectionUtil.Info info, int collectionId, String collectionName,
                        int entryId, ISnapshot snapshot, IProgressListener listener) throws SnapshotException
        {
            // no recursion -> use entryId to collect overflow entries
            while (entryId >= 0)
            {
                // skip if it is the pseudo outgoing reference (all other
                // elements are of type Map$Entry)
                if (snapshot.isClass(entryId))
                    return;
                // For ConcurrentSkipListMap skip Object refs
                if (snapshot.getClassOf(entryId).getName().equals("java.lang.Object")) //$NON-NLS-1$
                    return;

                IInstance entry = (IInstance) snapshot.getObject(entryId);

                // The java.util.WeakHashMap$Entry class extends WeakReference
                // which in turns extends ObjectReference. Both, the Entry as
                // well as the ObjectReference class, define a member variable
                // "next". Only the first next must be processed (fields are
                // ordered ascending the inheritance chain, i.e. from class to
                // super class)
                boolean nextFieldProcessed = false;
                int nextEntryId = -1;

                for (Field field : entry.getFields())
                {
                    if (!nextFieldProcessed && "next".equals(field.getName())) //$NON-NLS-1$
                    {
                        nextFieldProcessed = true;

                        if (field.getValue() != null)
                            nextEntryId = ((ObjectReference) field.getValue()).getObjectId();
                    }
                }

                entries.add(entryId);
                entryId = nextEntryId;

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

    }

    private static class ConcurrentHashMapInfo extends Info
    {
        /* package */ConcurrentHashMapInfo()
        {
            super("java.util.concurrent.ConcurrentHashMap", ~(Version.JAVA18 | Version.IBM18), null, "segments", "key", "value"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            setCollectionExtractor(CONCURRENT_HASH_MAP_EXTRACTOR);
        }

        /*
         * overwrite the getSize method to return correct result for a
         * concurrent map
         */
        @Override
        public int getSize(IObject collection) throws SnapshotException
        {
            IObjectArray segmentsArray = getBackingArray(collection);
            if (segmentsArray == null)
                return 0;
            ISnapshot snapshot = collection.getSnapshot();
            int size = 0;
            Info segmentInfo = getInfo("java.util.concurrent.ConcurrentHashMap$Segment"); //$NON-NLS-1$

            long[] refs = segmentsArray.getReferenceArray();
            for (long addr : refs)
            {
                if (addr != 0)
                {
                    int segmentId = snapshot.mapAddressToId(addr);
                    size += segmentInfo.getSize(snapshot.getObject(segmentId));
                }
            }

            return size;
        }

        @Override
        public boolean hasSize()
        {
            return true;
        }

        @Override
        public int getNumberOfNoNullArrayElements(IObject collection) throws SnapshotException
        {
            IObjectArray segmentsArray = getBackingArray(collection);
            if (segmentsArray == null)
                return 0;
            ISnapshot snapshot = collection.getSnapshot();
            int result = 0;
            Info segmentInfo = getInfo("java.util.concurrent.ConcurrentHashMap$Segment"); //$NON-NLS-1$

            long[] refs = segmentsArray.getReferenceArray();
            for (long addr : refs)
            {
                if (addr != 0)
                {
                    int segmentId = snapshot.mapAddressToId(addr);
                    result += segmentInfo.getNumberOfNoNullArrayElements(snapshot.getObject(segmentId));
                }
            }
            return result;
        }

        @Override
        public int getCapacity(IObject collection) throws SnapshotException
        {
            IObjectArray segmentsArray = getBackingArray(collection);
            if (segmentsArray == null)
                return 0;
            ISnapshot snapshot = collection.getSnapshot();
            int result = 0;
            Info segmentInfo = getInfo("java.util.concurrent.ConcurrentHashMap$Segment"); //$NON-NLS-1$

            long[] refs = segmentsArray.getReferenceArray();
            for (long addr : refs)
            {
                if (addr != 0)
                {
                    int segmentId = snapshot.mapAddressToId(addr);
                    result += segmentInfo.getCapacity(snapshot.getObject(segmentId));
                }
            }
            return result;
        }
    }

    private static class ConcurrentHashMapEntryExtractor implements ICollectionExtractor
    {
        public int[] extractEntries(int objectId, Info info, ISnapshot snapshot, IProgressListener listener)
                        throws SnapshotException
        {
            IObject concurrentMap = snapshot.getObject(objectId);
            IObjectArray segmentsArray = info.getBackingArray(concurrentMap);
            if (segmentsArray == null)
                return new int[0];
            ArrayInt result = new ArrayInt();
            Info segmentInfo = getInfo("java.util.concurrent.ConcurrentHashMap$Segment"); //$NON-NLS-1$

            long[] refs = segmentsArray.getReferenceArray();
            for (long addr : refs)
            {
                if (addr != 0)
                {
                    int segmentId = snapshot.mapAddressToId(addr);
                    int[] segmentEntries = segmentInfo.getCollectionExtractor().extractEntries(segmentId, segmentInfo,
                                    snapshot, listener);
                    result.addAll(segmentEntries);
                }
            }

            return result.toArray();
        }
    }

    /**
     * Extract the entries from a TreeMap
     */
    private static class TreeMapEntryExtractor implements ICollectionExtractor
    {
        public int[] extractEntries(int objectId, Info info, ISnapshot snapshot, IProgressListener listener)
                        throws SnapshotException
        {
            ArrayInt result = new ArrayInt();
            IObject treeMap = snapshot.getObject(objectId);
            String rootf = "root"; //$NON-NLS-1$
            // For TreeSet
            int dot = info.sizeField.lastIndexOf("."); //$NON-NLS-1$
            if (dot > 0) 
            {
                rootf = info.sizeField.substring(0, dot + 1) + rootf;
            }
            IObject root = (IObject) treeMap.resolveValue(rootf);
            if (root == null)
                return new int[0];

            Stack<IObject> stack = new Stack<IObject>();
            stack.push(root);

            IObject current = root;
            SetInt visited = new SetInt();

            /* traverse the TreeMap entries in-order */
            while (stack.size() > 0)
            {
                current = stack.peek();

                /* go left */
                IObject left = (IObject) current.resolveValue("left"); //$NON-NLS-1$
                if (left != null && !visited.contains(left.getObjectId()))
                {
                    stack.push(left);
                }
                else
                {
                    /* process node */
                    result.add(current.getObjectId());
                    visited.add(current.getObjectId());
                    stack.pop();

                    /* go right */
                    IObject right = (IObject) current.resolveValue("right"); //$NON-NLS-1$
                    if (right != null && !visited.contains(right.getObjectId()))
                    {
                        stack.push(right);
                    }
                }
            }

            return result.toArray();
        }
    }
    
    /**
     * Extract the entries from an IdentityHashMap.
     * Entries stored as key,value.
     */
    private static class IdentityHashMapEntryExtractor implements ICollectionExtractor
    {
        public int[] extractEntries(int objectId, Info info, ISnapshot snapshot, IProgressListener listener)
                        throws SnapshotException
        {
            ArrayInt result = new ArrayInt();
            IObject idMap = snapshot.getObject(objectId);
            IObjectArray array = (IObjectArray)info.getBackingArray(idMap);
            if (array == null)
                return new int[0];
            for (int i = 0; i < array.getLength(); i += 2)
            {
                
                long l[] = array.getReferenceArray(i, 2);
                // Skip over empty entries
                boolean empty = true;
                for (int j = 0; j < l.length; ++j)
                {
                    if (l[j] != 0)
                    {
                        empty = false;
                        break;
                    }
                }
                if (empty) continue;
                for (int j = 0; j < l.length; ++j)
                {
                    int objId;
                    if (l[j] != 0)
                    {
                        objId = snapshot.mapAddressToId(l[j]);
                    }
                    else
                    {
                        objId = -1;
                    }
                    result.add(objId);
                }
            }
            return result.toArray();
        }
    }
}
