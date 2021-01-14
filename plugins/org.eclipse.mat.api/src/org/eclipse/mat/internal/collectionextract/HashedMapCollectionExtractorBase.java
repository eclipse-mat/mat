/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.MessageUtil;

public abstract class HashedMapCollectionExtractorBase extends MapCollectionExtractorBase
{
    protected final String arrayField;

    public HashedMapCollectionExtractorBase(String arrayField, String keyField, String valueField)
    {
        super(keyField, valueField);
        this.arrayField = arrayField;
    }

    public boolean hasFillRatio()
    {
        return true;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        Integer size = getSize(coll);
        Integer cap = getCapacity(coll);
        if (size != null && cap != null)
        {
            double sz = size.doubleValue();
            double cp = cap.doubleValue();
            // If the size and capacity are zero mark as full
            // to avoid generating wasted space reports
            if (sz == 0.0 && cp == 0.0)
                return 1.0;
            else
                return sz / cp;
        }
        else
        {
            // Sometimes an empty map doesn't have a capacity yet
            return 1.0;
        }
    }

    public boolean hasCollisionRatio()
    {
        return hasSize();
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException
    {
        Integer size = getSize(coll);
        if (size == null || size <= 0)
        {
            return 0d;
        }
        else
        {
            return (double) (size - getNumberOfNotNullElements(coll)) / (double) size;
        }
    }

    protected IObjectArray extractBackingArray(IObject coll) throws SnapshotException
    {
        final Object obj = coll.resolveValue(arrayField);
        if (obj instanceof IObjectArray)
        {
            return (IObjectArray) obj;
        }
        else if (obj != null)
        {
            String desc = (obj instanceof IObject) ? ((IObject) obj).getTechnicalName() : obj.toString();
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                            coll.getTechnicalName(), desc);
            throw new SnapshotException(msg);
        }

        // the array wasn't found, because the field information is missing
        IObject next = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(arrayField, coll);
        if (next == null)
            return null;
        return ExtractionUtils.getOnlyArrayField(next);
    }

    protected int getMapSize(IObject collection, int[] objects) throws SnapshotException
    {
        // Maps have chained buckets in case of clashes
        // LinkedMaps have additional chains to maintain ordering
        int count = 0;
        ISnapshot snapshot = collection.getSnapshot();
        // Avoid visiting nodes twice
        BitField seen = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        // Used for alternative nodes if there is a choice
        ArrayInt extra = new ArrayInt();
        // Eliminate the LinkedHashMap header node
        // seen.set(array.getObjectId());
        // Walk over whole array, or all outbounds of header
        for (int i : objects)
        {
            // Ignore classes, outbounds we have seen, and plain Objects (which
            // can't be buckets e.g. ConcurrentSkipListMap)
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
     * Get the only object field from the object which is of the same type as
     * the source
     *
     * @param sourceId
     * @param seen
     *            whether seen yet
     * @param extra
     *            extra ones to do
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


    protected void collectEntriesFromTable(ArrayInt entries, int collectionId, int entryId, ISnapshot snapshot)
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
                    if (nextEntryId == entryId)
                        nextEntryId = -1;
                }
            }

            if (nextEntryId == -1)
            {
                if (snapshot.getClassOf(entryId).getName().equals("java.util.concurrent.ConcurrentHashMap$TreeBin")) //$NON-NLS-1$
                {
                    Object o = entry.resolveValue("first"); //$NON-NLS-1$
                    if (o instanceof IInstance)
                    {
                        entry = (IInstance) o;
                        entryId = entry.getObjectId();
                        continue;
                    }
                }
            }

            entries.add(entryId);
            entryId = nextEntryId;
        }
    }
}
