/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - detect IBM 1.4/1.5/1.6 VM
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
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
         * Gets the size of the collection
         * First try using the size field
         * Then try using the filled entries in the backing array
         * and the chained entries if it is a map.
         * @param collection
         * @return size of collection or 0 if unknown
         * @throws SnapshotException
         */
        public int getSize(IObject collection) throws SnapshotException
        {
            Integer value = (Integer) collection.resolveValue(sizeField);
            if (value == null)
            {
                if (hasBackingArray())
                {
                    IObjectArray array = getBackingArray(collection);
                    if (array != null)
                    {
                        if (!isMap())
                        {
                            // E.g. ArrayList 
                            int count = getNumberOfNoNullArrayElements(array);
                            value = count;
                        }
                        else
                        {
                            int count = getMapSize(collection, array);
                            value = count;
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
                        value = count;
                    }
                }
            }
            return value == null ? 0 : value;
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
            for (int i : snapshot.getOutboundReferentIds(array.getObjectId()))
            {
                if (!snapshot.isClass(i) && !seen.get(i))
                {
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
         * Get the only object field from the object
         * Used for finding the HashMap from the HashSet
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
                        ret = (IInstance)o;
                    }
                }
            }
            return ret;
        }

        /**
         * Get the only object field from the object
         * which is of the same type as the source
         * @param sourceId
         * @param seen whether seen yet
         * @param extra extra ones to do
         * @return the next node to search, null if none found
         * @throws SnapshotException
         */
        int resolveNextSameField(ISnapshot snapshot, int sourceId, BitField seen, ArrayInt extra) throws SnapshotException
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
            // Find out how many fields to chain through to find the array
            IObject next = collection;
            // Don't do the last as that is the array field
            for (int i = arrayField.indexOf('.'); i >= 0 && next != null; i = arrayField.indexOf('.', i+1))
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
            return keyField != null;
        }

        public String getEntryKeyField()
        {
            return keyField;
        }

        public String getEntryValueField()
        {
            return valueField;
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

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    public static int getNumberOfNoNullArrayElements(IObjectArray arrayObject)
    {
        // Fast path using referentIds for arrays with same number of outbounds (+class id) as length
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
        int IBM16 = 1 << 3;
    }

    @SuppressWarnings("nls")
    private static Info[] knownCollections = new Info[] {
                    new Info("java.util.AbstractList", null, null), //

                    new Info("java.util.Collections$EmptyList", "", null), // use "" to make the size 0
                    
                    new Info("java.util.ArrayList", ~Version.IBM16, "size", "elementData"), //
                    new IBM6ArrayListInfo("java.util.ArrayList", Version.IBM16, "firstIndex", "lastIndex", "array"), //

                    new IBM6ArrayListInfo("java.util.ArrayDeque", Version.IBM16, "front", "rear", "elements"), //

                    new Info("java.util.LinkedList", ~Version.IBM16, "size", "header."), //
                    new Info("java.util.LinkedList", Version.IBM16, "size", "voidLink."), //

                    new Info("java.util.HashMap", ~Version.IBM16, "size", "table", "key", "value"), //
                    new Info("java.util.HashMap", Version.IBM16, "elementCount", "elementData", "key", "value"), //

                    // Some Java 5 PHD files don't have superclass info so add LinkedHashMap to list
                    // This is the same as HashMap
                    new Info("java.util.LinkedHashMap", Version.IBM15, "size", "table", "key", "value"), //

                    new Info("com.ibm.jvm.util.HashMapRT", Version.IBM15|Version.IBM16, "size", "table", "key", "value"), //

                    new Info("java.util.IdentityHashMap", Version.IBM14 | Version.IBM15, "size", "table"), //
                    new Info("java.util.IdentityHashMap", Version.IBM16, "size", "elementData"), // 

                    new Info("java.util.Collections$EmptySet", "", null), // use "" to make the size 0
                    new Info("java.util.Collections$EmptyMap", "", null), // use "" to make the size 0
                    
                    new Info("java.util.HashSet", ~Version.IBM16, "map.size", "map.table", "key", "value"), //
                    new Info("java.util.HashSet", Version.IBM16, //
                                    "backingMap.elementCount", "backingMap.elementData", "key", "value"), //
                                    
                    // Some Java 5 PHD files don't have superclass info so add LinkedHashSet to list
                    // This is the same as HashSet
                    new Info("java.util.LinkedHashSet", Version.IBM15, "map.size", "map.table", "key", "value"), //

                    new Info("java.util.TreeMap", "size", null), //

                    new Info("java.util.TreeSet", ~Version.IBM16, "m.size", null), // 
                    new Info("java.util.TreeSet", Version.IBM16, "backingMap.size", null),

                    new Info("java.util.Hashtable", ~(Version.IBM15 | Version.IBM16), "count", "table", "key", "value"), //
                    new Info("java.util.Hashtable", Version.IBM15 | Version.IBM16, //
                                    "elementCount", "elementData", "key", "value"), //

                    // Some Java 5 PHD files don't have superclass info so add Properties to list
                    // This is the same as Hashtable
                    new Info("java.util.Properties", Version.IBM15, //
                                    "elementCount", "elementData", "key", "value"), //

                    new Info("java.util.Vector", "elementCount", "elementData"), //

                    new Info("java.util.WeakHashMap", ~Version.IBM16, "size", "table", "referent", "value"), //
                    new Info("java.util.WeakHashMap", Version.IBM16, "elementCount", "elementData", "referent", "value"), //

                    // IBM14? or sun too?
                    new Info("java.util.PriorityQueue", Version.IBM15, "size", "queue"), //
                    new Info("java.util.PriorityQueue", Version.IBM16, "size", "elements"), //

                    new Info("java.lang.ThreadLocal$ThreadLocalMap", Version.IBM14 | Version.IBM15 | Version.IBM16, //
                                    "size", "table", "referent", "value"), //

                    new Info("java.util.concurrent.ConcurrentHashMap$Segment", "count", "table", "key", "value"), // 
                    new Info("com.sap.engine.lib.util.AbstractDataStructure", null, null), //

                    new Info("java.util.concurrent.CopyOnWriteArrayList", "", "array"), //
                    new Info("java.util.concurrent.CopyOnWriteArraySet", "", "al.array"), // 

    };

    @SuppressWarnings("nls")
    private static int resolveVersion(ISnapshot snapshot) throws SnapshotException
    {
        Collection<IClass> classes;
        if ((classes = snapshot.getClassesByName("com.ibm.misc.JavaRuntimeVersion", false)) != null && !classes.isEmpty())
            return Version.IBM15;
        else if ((classes = snapshot.getClassesByName("com.ibm.oti.vm.BootstrapClassLoader", false)) != null && !classes.isEmpty())
            return Version.IBM16;
        else if ((classes = snapshot.getClassesByName("com.ibm.jvm.Trace", false)) != null && !classes.isEmpty())
            return Version.IBM14;

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
}
