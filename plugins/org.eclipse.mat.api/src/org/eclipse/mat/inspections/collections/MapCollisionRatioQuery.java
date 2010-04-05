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

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.InspectionAssert;
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

@CommandName("map_collision_ratio")
public class MapCollisionRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
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
        InspectionAssert.heapFormatIsNot(snapshot, "phd"); //$NON-NLS-1$

        listener.subTask(Messages.MapCollisionRatioQuery_CalculatingCollisionRatios);

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = CollectionUtil.getKnownMaps(snapshot);

        // prepare meta-data from user provided the collection argument
        if (collection != null)
        {
            if (size_attribute == null || array_attribute == null)
            {
                String msg = Messages.MapCollisionRatioQuery_ErrorMsg_MissingArgument;
                throw new SnapshotException(msg);
            }

            CollectionUtil.Info info = new CollectionUtil.Info(collection, size_attribute, array_attribute);
            Collection<IClass> classes = snapshot.getClassesByName(collection, true);

            if (classes == null || classes.isEmpty())
            {
                listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(
                                Messages.MapCollisionRatioQuery_ErrorMsg_ClassNotFound, collection), null);
            }
            else
            {
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
            }
        }

        // create frequency distribution
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(
                        Messages.MapCollisionRatioQuery_Column_CollisionRatio, 0, 1, (double) 1 / (double) segments);
        builder.column(Messages.MapCollisionRatioQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    break;

                CollectionUtil.Info info = metadata.get(snapshot.getClassOf(objectId).getObjectId());
                if (info != null)
                {
                    IObject obj = snapshot.getObject(objectId);
                    double collisionRatio = getCollisionRatio(info, obj);
                    quantize.addValue(obj.getObjectId(), collisionRatio, null, obj.getUsedHeapSize());
                }
            }
        }

        return quantize.getResult();
    }

    private static double getCollisionRatio(CollectionUtil.Info info, IObject hashtableObject) throws SnapshotException
    {
        int size = info.getSize(hashtableObject);
        if (size <= 0)
            return size;

        IObjectArray table = info.getBackingArray(hashtableObject);
        if (table == null)
            return 0;

        return (double) (size - CollectionUtil.getNumberOfNoNullArrayElements(table)) / (double) size;
    }
}
