/*******************************************************************************
 * Copyright (c) 2008 SAP AG and others.
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
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

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

        public int getSize(IObject collection) throws SnapshotException
        {
            Integer value = (Integer) collection.resolveValue(sizeField);
            return value == null ? 0 : value;
        }

        public boolean hasBackingArray()
        {
            return arrayField != null;
        }

        public IObjectArray getBackingArray(IObject collection) throws SnapshotException
        {
            return (IObjectArray) collection.resolveValue(arrayField);
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

    private static Info[] knownCollections = new Info[] {
                    new Info("java.util.AbstractList", null, null), //

                    new Info("java.util.ArrayList", ~Version.IBM16, "size", "elementData"), //
                    new IBM6ArrayListInfo("java.util.ArrayList", Version.IBM16, "firstIndex", "lastIndex", "array"), //

                    new IBM6ArrayListInfo("java.util.ArrayDeque", Version.IBM16, "front", "rear", "elements"), //

                    new Info("java.util.LinkedList", "size", null), //

                    new Info("java.util.HashMap", ~Version.IBM16, "size", "table", "key", "value"), //
                    new Info("java.util.HashMap", Version.IBM16, "elementCount", "elementData", "key", "value"), // 

                    new Info("java.util.IdentityHashMap", Version.IBM14 | Version.IBM15, "size", "table"), //
                    new Info("java.util.IdentityHashMap", Version.IBM16, "size", "elementData"), // 

                    new Info("java.util.HashSet", ~Version.IBM16, "map.size", "map.table", "key", "value"), //
                    new Info("java.util.HashSet", Version.IBM16, //
                                    "backingMap.elementCount", "backingMap.elementData", "key", "value"), // 

                    new Info("java.util.TreeMap", "size", null), //

                    new Info("java.util.TreeSet", ~Version.IBM16, "m.size", null), // 
                    new Info("java.util.TreeSet", Version.IBM16, "backingMap.size", null), 

                    new Info("java.util.Hashtable", ~(Version.IBM15 | Version.IBM16), "count", "table", "key", "value"), //
                    new Info("java.util.Hashtable", Version.IBM15 | Version.IBM16, //
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
                    new Info("com.sap.engine.lib.util.AbstractDataStructure", null, null) //
    };

    private static int resolveVersion(ISnapshot snapshot) throws SnapshotException
    {
        if (snapshot.getClassesByName("com.ibm.misc.JavaRuntimeVersion", false) != null)
            return Version.IBM15;
        else if (snapshot.getClassesByName("com.ibm.oti.vm.BootstrapClassLoader", false) != null)
            return Version.IBM16;
        else if (snapshot.getClassesByName("com.ibm.jvm.Trace", false) != null)
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
            if (lastIndex == 0)
                return 0;

            Integer firstIndex = (Integer) collection.resolveValue(this.firstIndex);

            return lastIndex - (firstIndex == null ? 0 : firstIndex);
        }
    }
}
