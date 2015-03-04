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
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.HashMapCollectionExtractor;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("map_collision_ratio")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
public class MapCollisionRatioQuery implements IQuery
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
        listener.subTask(Messages.MapCollisionRatioQuery_CalculatingCollisionRatios);

        // create frequency distribution
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(
                        Messages.MapCollisionRatioQuery_Column_CollisionRatio, 0, 1, (double) 1 / (double) segments);
        builder.column(Messages.MapCollisionRatioQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        IMapExtractor specificExtractor = new HashMapCollectionExtractor(size_attribute, array_attribute, null, null);
        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    break;

                IObject obj = snapshot.getObject(objectId);
                try
                {
                    ExtractedMap coll = CollectionExtractionUtils.extractMap(obj, collection, specificExtractor);

                    if (coll != null)
                    {
                        Double collisionRatio = coll.getCollisionRatio();
                        if (collisionRatio == null)
                            collisionRatio = 0.0;
                        quantize.addValue(obj.getObjectId(), collisionRatio, null, obj.getUsedHeapSize());
                    }
                }
                catch (RuntimeException e)
                {
                    listener.sendUserMessage(
                                    IProgressListener.Severity.INFO,
                                    MessageUtil.format(Messages.MapCollisionRatioQuery_IgnoringCollection,
                                                    obj.getTechnicalName()), e);
                }
            }
        }

        return quantize.getResult();
    }
}
