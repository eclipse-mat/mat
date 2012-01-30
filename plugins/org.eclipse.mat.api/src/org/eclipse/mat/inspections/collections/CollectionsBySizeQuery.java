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

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("collections_grouped_by_size")
public class CollectionsBySizeQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String size_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.CollectionsBySizeQuery_CollectingSizes);

        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();

        // prepare meta-data of known collections
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot))
        {
            if (!info.hasSize())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        // prepare meta-data from user provided the collection argument
        if (collection != null)
        {
            if (size_attribute == null)
            {
                String msg = Messages.CollectionsBySizeQuery_ErrorMsg_ArgumentMissing;
                throw new SnapshotException(msg);
            }

            CollectionUtil.Info info = new CollectionUtil.Info(collection, size_attribute, null);
            Collection<IClass> classes = snapshot.getClassesByName(collection, true);

            if (classes == null || classes.isEmpty())
                listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(
                                Messages.CollectionsBySizeQuery_ClassNotFound, collection), null);

            for (IClass clasz : classes)
                metadata.put(clasz.getObjectId(), info);
        }

        // group by length attribute
        Quantize.Builder builder = Quantize.valueDistribution(new Column(Messages.CollectionsBySizeQuery_Column_Length,
                        int.class));
        builder.column(Messages.CollectionsBySizeQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG, SortDirection.DESC);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        ObjectLoop: for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                CollectionUtil.Info info = metadata.get(snapshot.getClassOf(objectId).getObjectId());
                if (info != null)
                {
                    IObject obj = snapshot.getObject(objectId);
                    try
                    {
                        int size = info.getSize(obj);
                        quantize.addValue(objectId, size, null, obj.getUsedHeapSize());
                    }
                    catch (SnapshotException e)
                    {
                        listener.sendUserMessage(IProgressListener.Severity.INFO,
                                        MessageUtil.format(Messages.CollectionsBySizeQuery_IgnoringCollection, obj.getTechnicalName()), e);
                    }
                }

                if (listener.isCanceled())
                    break ObjectLoop;
            }
        }

        return quantize.getResult();
    }

}
