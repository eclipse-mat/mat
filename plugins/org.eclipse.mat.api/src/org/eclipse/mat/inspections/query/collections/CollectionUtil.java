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
package org.eclipse.mat.inspections.query.collections;

import org.eclipse.mat.snapshot.model.IObjectArray;

public class CollectionUtil
{
    // //////////////////////////////////////////////////////////////
    // meta information about known collections
    // //////////////////////////////////////////////////////////////

    public static class Info
    {
        private String className;
        private String sizeField;
        private String arrayField;
        private String keyField;

        public Info(String className, String sizeField, String arrayField)
        {
            this(className, sizeField, arrayField, null);
        }

        public Info(String className, String sizeField, String arrayField, String keyField)
        {
            this.className = className;
            this.sizeField = sizeField;
            this.arrayField = arrayField;
            this.keyField = keyField;
        }

        public String getClassName()
        {
            return className;
        }

        public String getSizeField()
        {
            return sizeField;
        }

        public String getArrayField()
        {
            return arrayField;
        }

        public boolean isHashed()
        {
            return keyField != null;
        }

        public String getKeyField()
        {
            return keyField;
        }
    }

    public static Info[] knownCollections = new Info[] {
                    new Info("java.util.ArrayList", "size", "elementData"), //
                    new Info("java.util.HashMap", "size", "table", "key"), //
                    new Info("java.util.TreeMap", "size", null), // 
                    new Info("java.util.Hashtable", "count", "table", "key"),
                    new Info("java.util.Properties", "count", "table"),
                    new Info("java.util.Vector", "elementCount", "elementData"),
                    new Info("java.util.WeakHashMap", "size", "table", "referent"),
                    new Info("java.util.concurrent.ConcurrentHashMap$Segment", "count", "table", "key"),
                    new Info("java.util.AbstractList", null, null),
                    new Info("com.sap.engine.lib.util.AbstractDataStructure", null, null) };

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    public static int getNumberOfNoNullArrayElements(IObjectArray arrayObject)
    {
        long[] elements = (long[]) arrayObject.getContent();
        int result = 0;
        for (int i = 0; i < elements.length; i++)
        {
            if (elements[i] != 0)
                result++;
        }
        return result;
    }

}
