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
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("hash_set_values")
public class HashSetValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IObject hashSet;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String array_attribute;

    @Argument(isMandatory = false)
    public String key_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd"); //$NON-NLS-1$

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
        else if (hashSet.getClazz().doesExtend("java.util.HashSet")) //$NON-NLS-1$
        {
            info = CollectionUtil.getInfo(hashSet);
        }
        else
        {
            throw new IllegalArgumentException(MessageUtil.format(Messages.HashSetValuesQuery_ErrorMsg_NotAHashSet,
                            hashSet.getDisplayName()));
        }

        ArrayInt hashEntries = new ArrayInt();

        // read table w/o loading the big table object!
        String arrayField = info.getBackingArrayField();
        int p = arrayField.lastIndexOf('.');
        IInstance map = p < 0 ? (IInstance) hashSet : (IInstance) hashSet.resolveValue(arrayField.substring(0, p));
        Field table = map.getField(p < 0 ? arrayField : arrayField.substring(p + 1));

        int tableObjectId = ((ObjectReference) table.getValue()).getObjectId();

        int[] outbounds = snapshot.getOutboundReferentIds(tableObjectId);
        for (int ii = 0; ii < outbounds.length; ii++)
            collectEntry(hashEntries, outbounds[ii], info, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        return new ObjectListResult.Outbound(snapshot, hashEntries.toArray());
    }

    private void collectEntry(ArrayInt hashEntries, int entryId, CollectionUtil.Info info, IProgressListener listener)
                    throws SnapshotException
    {
        // no recursion -> use entryId to collect overflow entries
        while (entryId >= 0)
        {
            // skip if it is the pseudo outgoing reference (all other elements
            // are of type Map$Entry)
            if (snapshot.isClass(entryId))
                return;

            IInstance entry = (IInstance) snapshot.getObject(entryId);

            entryId = -1;

            Field next = entry.getField("next"); //$NON-NLS-1$
            if (next.getValue() != null)
                entryId = ((ObjectReference) next.getValue()).getObjectId();

            Field key = entry.getField(info.getEntryKeyField());
            hashEntries.add(((ObjectReference) key.getValue()).getObjectId());

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }
    }

}
