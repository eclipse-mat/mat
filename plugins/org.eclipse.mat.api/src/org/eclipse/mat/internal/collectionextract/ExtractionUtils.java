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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ExtractionUtils
{
    public static Integer toInteger(Object i)
    {
        if (i != null && i instanceof Number)
            return ((Number) i).intValue();
        else
            return null;
    }

    public static int getNumberOfNotNullArrayElements(IObjectArray arrayObject)
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

        return getNumberOfNotNullArrayElements(arrayObject.getReferenceArray());
    }

    public static int getNumberOfNotNullArrayElements(long[] addresses)
    {
        int result = 0;
        for (int i = 0; i < addresses.length; i++)
        {
            if (addresses[i] != 0)
                result++;
        }
        return result;
    }

    public static int getNumberOfNotNullArrayElements(int[] ids)
    {
        int result = 0;
        for (int i = 0; i < ids.length; i++)
        {
            if (ids[i] != 0)
                result++;
        }
        return result;
    }

    public static int[] referenceArrayToIds(ISnapshot snapshot, long[] referenceArray) throws SnapshotException
    {
        ArrayInt arr = new ArrayInt();
        for (int i = 0; i < referenceArray.length; i++)
        {
            if (referenceArray[i] != 0)
                arr.add(snapshot.mapAddressToId(referenceArray[i]));
        }
        return arr.toArray();

    }


    /**
     * Get the only non-array object field from the object.
     * For example used for finding the HashMap from the HashSet
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IInstance followOnlyNonArrayOutgoingReference(IObject obj) throws SnapshotException
    {
        final ISnapshot snapshot = obj.getSnapshot();
        IInstance ret = null;
        for (int i : snapshot.getOutboundReferentIds(obj.getObjectId()))
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
     * Walks the only non-array object field from the object,
     *  stopping at the second-last.
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IObject followOnlyOutgoingReferencesExceptLast(String field, IObject obj) throws SnapshotException
    {
        int j = field.lastIndexOf('.');
        if (j >= 0)
        {
            Object ret = obj.resolveValue(field.substring(0, j));
            if (ret instanceof IObject) { return (IObject) ret; }
        }
        // Find out how many fields to chain through to find the array
        IObject next = obj;
        // Don't do the last as that is the array field
        for (int i = field.indexOf('.'); i >= 0 && next != null; i = field.indexOf('.', i + 1))
        {
            next = followOnlyNonArrayOutgoingReference(next);
        }
        return next;
    }

    /**
     * Get the only array field from the object.
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IObjectArray getOnlyArrayField(IObject obj) throws SnapshotException
    {
        IObjectArray ret = null;
        // Look for the only object array field
        final ISnapshot snapshot = obj.getSnapshot();
        for (int i : snapshot.getOutboundReferentIds(obj.getObjectId()))
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
}
