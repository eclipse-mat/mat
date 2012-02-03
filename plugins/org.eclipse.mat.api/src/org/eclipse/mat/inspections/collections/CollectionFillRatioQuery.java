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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("collection_fill_ratio")
public class CollectionFillRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public int segments = 5;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String size_attribute;

    @Argument(isMandatory = false)
    public String array_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.CollectionFillRatioQuery_ExtractingFillRatios);

        Map<Integer, CollectionUtil.Info> metadata = new HashMap<Integer, CollectionUtil.Info>();

        // prepare meta-data of known collections
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot))
        {
            if (!info.hasSize() || !info.hasBackingArray())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        // prepare meta-data from user provided the collection argument
        if (collection != null)
        {
            if (size_attribute == null || array_attribute == null)
            {
                String msg = Messages.CollectionFillRatioQuery_ErrorMsg_AllArgumentsMustBeSet;
                throw new SnapshotException(msg);
            }

            CollectionUtil.Info info = new CollectionUtil.Info(collection, size_attribute, array_attribute);
            Collection<IClass> classes = snapshot.getClassesByName(collection, true);
            if (classes == null)
                classes = Collections.emptySet();

            if (classes.isEmpty())
                listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(
                                Messages.CollectionFillRatioQuery_ClassNotFound, collection), null);

            for (IClass clasz : classes)
                metadata.put(clasz.getObjectId(), info);
        }

        // create frequency distribution
        // The load factor should be <= 1, but for old PHD files with inaccurate array sizes can appear > 1.
        // Therefore we have a larger upper bound just in case
        // Use slightly > 5.0 so final 1.000 division is >= 1
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(
                        Messages.CollectionFillRatioQuery_Column_FillRatio, 0, 5.0000000001, (double) 1 / (double) segments);
        builder.column(Messages.CollectionFillRatioQuery_ColumnNumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        ObjectLoop: for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    break ObjectLoop;

                CollectionUtil.Info info = metadata.get(snapshot.getClassOf(objectId).getObjectId());
                if (info != null)
                {
                    IObject obj = snapshot.getObject(objectId);
                    try
                    {
                        double fillRatio = getFillRatio(info, obj);
                        quantize.addValue(obj.getObjectId(), fillRatio, 1, obj.getUsedHeapSize());
                    }
                    catch (SnapshotException e)
                    {
                        listener.sendUserMessage(IProgressListener.Severity.INFO,
                                        MessageUtil.format(Messages.CollectionFillRatioQuery_IgnoringCollection, obj.getTechnicalName()), e);
                    }
                }
            }
        }

        return quantize.getResult();
    }

    private static double getFillRatio(CollectionUtil.Info info, IObject hashtableObject) throws SnapshotException
    {
        int size = info.getSize(hashtableObject);
        int capacity = info.getCapacity(hashtableObject);

        if (capacity == 0)
            return 1; // 100% if the array has length 0 --> the good ones

        return (double) size / (double) capacity;
    }

}
