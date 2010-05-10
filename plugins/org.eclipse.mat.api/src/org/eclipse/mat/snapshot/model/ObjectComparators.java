/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.util.Comparator;

/**
 * Factory of heap object comparators.
 */
public class ObjectComparators
{
    public static Comparator<IObject> getComparatorForTechnicalNameAscending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                return o1.getTechnicalName().compareTo(o2.getTechnicalName());
            }
        };
    }

    public static Comparator<IObject> getComparatorForTechnicalNameDescending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                return o2.getTechnicalName().compareTo(o1.getTechnicalName());
            }
        };
    }

    public static Comparator<IObject> getComparatorForClassSpecificNameAscending()
    {
        return new Comparator<IObject>()
        {

            public int compare(IObject o1, IObject o2)
            {
                String name1 = o1.getClassSpecificName();
                if (name1 == null)
                    return -1;

                String name2 = o2.getClassSpecificName();
                if (name2 == null)
                    return 1;
                return name1.compareTo(name2);
            }
        };
    }

    public static Comparator<IObject> getComparatorForClassSpecificNameDescending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                String name1 = o1.getClassSpecificName();
                if (name1 == null)
                    return 1;

                String name2 = o2.getClassSpecificName();
                if (name2 == null)
                    return -1;
                return name2.compareTo(name1);
            }
        };
    }

    public static Comparator<IObject> getComparatorForUsedHeapSizeAscending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                if (o1.getUsedHeapSize() < o2.getUsedHeapSize())
                    return -1;
                if (o1.getUsedHeapSize() > o2.getUsedHeapSize())
                    return 1;
                return 0;
            }
        };
    }

    public static Comparator<IObject> getComparatorForUsedHeapSizeDescending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                if (o1.getUsedHeapSize() < o2.getUsedHeapSize())
                    return 1;
                if (o1.getUsedHeapSize() > o2.getUsedHeapSize())
                    return -1;
                return 0;
            }
        };
    }

    public static Comparator<IObject> getComparatorForRetainedHeapSizeAscending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                if (o1.getRetainedHeapSize() < o2.getRetainedHeapSize())
                    return -1;
                if (o1.getRetainedHeapSize() > o2.getRetainedHeapSize())
                    return 1;
                return 0;
            }
        };
    }

    public static Comparator<IObject> getComparatorForRetainedHeapSizeDescending()
    {
        return new Comparator<IObject>()
        {
            public int compare(IObject o1, IObject o2)
            {
                if (o1.getRetainedHeapSize() < o2.getRetainedHeapSize())
                    return 1;
                if (o1.getRetainedHeapSize() > o2.getRetainedHeapSize())
                    return -1;
                return 0;
            }
        };
    }
}
