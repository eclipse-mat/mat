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
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.MessageUtil;

public class HashMapCollectionExtractor extends HashedMapCollectionExtractorBase
{
    public HashMapCollectionExtractor(String sizeField, String arrayField, String keyField, String valueField)
    {
        super(sizeField, arrayField, keyField, valueField);
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public boolean hasExtractableArray()
    {
        // true for CHM segments?
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        return null;
    }

    public int[] extractEntryIds(IObject coll) throws SnapshotException
    {
        ISnapshot snapshot = coll.getSnapshot();
        String collectionName = coll.getDisplayName();
        ArrayInt entries = new ArrayInt();

        final IObject table = getTable(coll);
        if (table != null)
        {
            int[] outbounds = snapshot.getOutboundReferentIds(table.getObjectId());
            for (int ii = 0; ii < outbounds.length; ii++)
                collectEntry(entries, coll.getObjectId(), collectionName, outbounds[ii], snapshot);
        }

        return entries.toArray();
    }

    @Override
    public Integer getCapacity(IObject coll) throws SnapshotException
    {
        IObject table = getTable(coll);
        if (table != null && table instanceof IObjectArray)
        {
            return ((IObjectArray) table).getLength();
        }
        else
        {
            return null;
        }
    }

    private IObject getTable(IObject coll) throws SnapshotException
    {
        IObjectArray ba = getBackingArray(coll);
        if (ba != null)
            return ba;

        // this is used for classes like ConcurrentSkipListSet
        String arrayField = this.arrayField;
        // read table w/o loading the big table object!
        int p = arrayField.lastIndexOf('.');
        if (p == arrayField.length() - 1 && p > 0)
        {
            // trailing dot indicates field is not actually an array
            arrayField = arrayField.substring(0, p);
            p = arrayField.lastIndexOf('.');
        }
        else
        {
            p = arrayField.length();
        }

        IObject arr = (IObject) coll.resolveValue(arrayField.substring(0, p));
        if (arr instanceof IObjectArray) { return arr; }

        IInstance map = p < 0 ? (IInstance) coll : (IInstance) arr;
        if (map != null)
        {
            Field tableField = map.getField(p < 0 ? arrayField : arrayField.substring(p + 1));
            if (tableField != null)
            {
                final ObjectReference tableFieldValue = (ObjectReference) tableField.getValue();
                return (tableFieldValue != null) ? tableFieldValue.getObject() : null;
            }
        }
        return null;
    }

    private void collectEntry(ArrayInt entries, int collectionId, String collectionName, int entryId, ISnapshot snapshot)
                    throws SnapshotException
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
        }
    }

    public Integer getNumberOfNotNullElements(IObject collection) throws SnapshotException
    {
        IObjectArray arrayObject = extractBackingArray(collection);
        if (arrayObject == null)
            return 0;
        return ExtractionUtils.getNumberOfNotNullArrayElements(arrayObject);
    }

    public IObjectArray getBackingArray(IObject coll) throws SnapshotException
    {
        if (arrayField == null)
            return null;
        final Object obj = coll.resolveValue(arrayField);
        IObjectArray ret = null;
        if (obj instanceof IObjectArray)
        {
            ret = (IObjectArray) obj;
            return ret;
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
}
