/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("hash_set_values")
public class HashSetValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IObject hashSet;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String array_attribute;

    @Argument(isMandatory = false)
    public String key_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        CollectionUtil.Info info = null;

        if (collection != null && hashSet.getClazz().doesExtend(collection))
        {
            if (array_attribute == null || key_attribute == null)
            {
                String msg = Messages.HashSetValuesQuery_ErrorMsg_MissingArgument;
                throw new SnapshotException(msg);
            }
            info = new CollectionUtil.Info(collection, null, array_attribute, key_attribute, null);
        }
        else if ((info = CollectionUtil.getInfo(hashSet)) != null && info.isMap() && info.getClassName().contains("Set")) //$NON-NLS-1$
        {
            // Got a HashSet
        }
        else
        {
            throw new IllegalArgumentException(MessageUtil.format(Messages.HashSetValuesQuery_ErrorMsg_NotAHashSet,
                            hashSet.getDisplayName()));
        }

        ArrayInt hashEntries = new ArrayInt();

        // read table w/o loading the big table object!
        String arrayField = info.getBackingArrayField();
        if (arrayField == null)
        {
            // No array, but perhaps an extractor
            ICollectionExtractor ex = info.getCollectionExtractor();
            if (ex != null)
            {
                int entries[] = ex.extractEntries(hashSet.getObjectId(), info, snapshot, listener);
                String valueField = info.getEntryKeyField();
                if (valueField != null) {
                    ArrayInt ret = new ArrayInt();
                    for (int entryId : entries)
                    {
                        IInstance entry = (IInstance) snapshot.getObject(entryId);
                        Object f = entry.resolveValue(valueField);
                        if (f instanceof IInstance)
                        {
                            ret.add(((IInstance)f).getObjectId());
                        }
                    }
                    entries = ret.toArray();
                }
                return new ObjectListResult.Outbound(snapshot, entries);
            }
            return null;
        }
        int p = arrayField.lastIndexOf('.');
        IInstance map = p < 0 ? (IInstance) hashSet : (IInstance) hashSet.resolveValue(arrayField.substring(0, p));
        int tableObjectId;
        if (map != null)
        {
            Field table = map.getField(p < 0 ? arrayField : arrayField.substring(p + 1));
            if (table == null)
                return null;
            Object tableValue = table.getValue();
            if (tableValue == null)
                return null;
            tableObjectId = ((ObjectReference) tableValue).getObjectId();
        }
        else
        {
            IObjectArray back = info.getBackingArray(hashSet);
            if (back == null)
                return null;
            tableObjectId = back.getObjectId();
        }

        // Avoid visiting nodes twice
        BitField seen = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        ArrayInt extra = new ArrayInt();
        int[] outbounds = snapshot.getOutboundReferentIds(tableObjectId);
        for (int ii = 0; ii < outbounds.length && !listener.isCanceled(); ii++)
            collectEntry(hashEntries, outbounds[ii], seen, extra, info, listener);

        return new ObjectListResult.Outbound(snapshot, hashEntries.toArray());
    }

    /**
     * Find the hash entries
     * @param hashEntries
     * @param entryId
     * @param seen - whether the node has been visited or value has been seen
     * @param extra - holds extra nodes to visit
     * @param info
     * @param listener
     * @throws SnapshotException
     */
    private void collectEntry(ArrayInt hashEntries, int entryId, BitField seen, ArrayInt extra, CollectionUtil.Info info, IProgressListener listener)
                    throws SnapshotException
    {
        if (seen.get(entryId))
            return;
        extra.clear();
        extra.add(entryId);
        seen.set(entryId);
        ObjectLoop: for (int k = 0; k < extra.size(); ++k)
        {
            entryId = extra.get(k);
            while (entryId >= 0)
            {
                // skip if it is the pseudo outgoing reference (all other elements
                // are of type Map$Entry)
                if (snapshot.isClass(entryId))
                    break;

                IInstance entry = (IInstance) snapshot.getObject(entryId);

                entryId = -1;

                Field next = entry.getField("next"); //$NON-NLS-1$
                if (next != null)
                {
                    if (next.getValue() != null)
                    {
                        entryId = ((ObjectReference) next.getValue()).getObjectId();
                        seen.set(entryId);
                    }
                }
                else
                {
                    // Try to find without using fields
                    entryId = info.resolveNextSameField(snapshot, entry.getObjectId(), seen, extra);
                }

                Field key = entry.getField(info.getEntryKeyField());
                if (key != null)
                {
                    hashEntries.add(((ObjectReference) key.getValue()).getObjectId());
                }
                else
                {
                    // Find an object which is not the type of the entry, nor the HashSet, not next
                    for (int i : snapshot.getOutboundReferentIds(entry.getObjectId()))
                    {
                        if (i != entryId
                            && i != entry.getClazz().getObjectId()
                            && i != hashSet.getObjectId()
                            && !seen.get(i))
                        {
                            hashEntries.add(i);
                            seen.set(i);
                        }
                    }
                }

                if (listener.isCanceled())
                    break ObjectLoop;
            }
        }
    }

}
