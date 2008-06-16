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

import java.text.MessageFormat;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.query.ObjectListQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;


@Name("Extract Hash Set Values")
@CommandName("hash_set_values")
@Category("Java Collections")
@Help("List elements of a HashSet.")
public class HashSetValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IObject hashSet;

    public IResult execute(IProgressListener listener) throws Exception
    {
        if (!hashSet.getClazz().doesExtend("java.util.HashSet"))
            throw new IllegalArgumentException(MessageFormat.format("Not a hash set: {0}", hashSet.getDisplayName()));

        IObject map = (IObject) hashSet.resolveValue("map");

        ArrayInt hashEntries = new ArrayInt();

        // read table w/o loading the big table object!
        Field table = ((IInstance) map).getField("table");
        int tableObjectId = ((ObjectReference) table.getValue()).getObjectId();

        int[] outbounds = snapshot.getOutboundReferentIds(tableObjectId);
        for (int ii = 0; ii < outbounds.length; ii++)
            collectEntry(hashEntries, outbounds[ii], listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        return new ObjectListQuery.OutboundObjects(snapshot, hashEntries.toArray());
    }

    private void collectEntry(ArrayInt hashEntries, int entryId, IProgressListener listener) throws SnapshotException
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

            Field next = entry.getField("next");
            if (next.getValue() != null)
                entryId = ((ObjectReference) next.getValue()).getObjectId();

            Field key = entry.getField("key");
            hashEntries.add(((ObjectReference) key.getValue()).getObjectId());

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }
    }

}
