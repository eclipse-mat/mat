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
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.MessageUtil;

public class FieldArrayCollectionExtractor implements ICollectionExtractor
{
    protected final String arrayField;

    public FieldArrayCollectionExtractor(String arrayField)
    {
        if (arrayField == null)
            throw new IllegalArgumentException();
        this.arrayField = arrayField;
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        // what if they can hold null?
        return getNumberOfNotNullElements(coll);
    }

    public boolean hasCapacity()
    {
        return true;
    }

    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IObjectArray arrayObject = extractEntries(coll);
        if (arrayObject == null)
            return null;
        else
            return arrayObject.getLength();
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        Integer size = getNumberOfNotNullElements(coll);
        Integer cap = getCapacity(coll);
        if (size != null && cap != null)
        {
            double sz = size.doubleValue();
            double cp = cap.doubleValue();
            if (sz == 0.0 && cp == 0.0)
            {
                return 1.0;
            }
            else
            {
                return sz / cp;
            }
        }
        else
            return null;
    }

    public boolean hasExtractableContents()
    {
        return hasExtractableArray();
    }

    public boolean hasExtractableArray()
    {
        return !arrayField.endsWith("."); //$NON-NLS-1$
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.referenceArrayToIds(coll.getSnapshot(), extractEntries(coll).getReferenceArray());
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        final Object obj = coll.resolveValue(arrayField);
        if (obj instanceof IObjectArray)
        {
            return (IObjectArray) obj;
        }
        else if (obj instanceof IObject)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), ((IObject) obj).getTechnicalName());
            throw new SnapshotException(msg);
        }
        else if (obj != null)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), obj.toString());
            throw new SnapshotException(msg);
        }

        IObject next = resolveNextFields(coll);
        if (next == null)
            return null;
        IObjectArray ret = null;
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

    protected IObject resolveNextFields(IObject collection) throws SnapshotException
    {
        int j = arrayField.lastIndexOf('.');
        if (j >= 0)
        {
            Object ret = collection.resolveValue(arrayField.substring(0, j));
            if (ret instanceof IObject) { return (IObject) ret; }
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

    /**
     * Get the only object field from the object Used for finding the HashMap
     * from the HashSet
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

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        IObjectArray arrayObject = extractEntries(coll);
        if (arrayObject == null)
            return null;
        return ExtractionUtils.getNumberOfNotNullArrayElements(arrayObject);
    }
}
