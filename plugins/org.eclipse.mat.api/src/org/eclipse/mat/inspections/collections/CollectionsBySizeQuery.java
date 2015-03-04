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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.FieldSizeArrayCollectionExtractor;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
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

@CommandName("collections_grouped_by_size")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
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

        ICollectionExtractor specificExtractor;
        if (size_attribute != null && collection != null)
        {
            specificExtractor = new FieldSizeArrayCollectionExtractor(size_attribute, collection);
        }
        else if (size_attribute == null && collection == null)
        {
            specificExtractor = null;
        }
        else
        {
            throw new IllegalArgumentException("need both or none of size and array attributes");
        }

        // group by length attribute
        Quantize.Builder builder = Quantize.valueDistribution(new Column(Messages.CollectionsBySizeQuery_Column_Length,
                        int.class));
        builder.column(Messages.CollectionsBySizeQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG, SortDirection.DESC);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        runQuantizer(listener, quantize, specificExtractor, collection);
        return quantize.getResult();
    }

    private void runQuantizer(IProgressListener listener, Quantize quantize, ICollectionExtractor specificExtractor,
                    String specificClass) throws SnapshotException
    {
        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                IObject obj = snapshot.getObject(objectId);
                try
                {
                    AbstractExtractedCollection coll = CollectionExtractionUtils.extractCollection(obj, specificClass,
                                    specificExtractor);
                    if (coll != null && coll.hasSize())
                    {
                        Integer size = coll.size();
                        if (size != null)
                            quantize.addValue(objectId, size, null, coll.getUsedHeapSize());
                    }
                }
                catch (RuntimeException e)
                {
                    listener.sendUserMessage(
                                    IProgressListener.Severity.INFO,
                                    MessageUtil.format(Messages.CollectionsBySizeQuery_IgnoringCollection,
                                                    obj.getTechnicalName()), e);
                }

                if (listener.isCanceled())
                    return;
            }
        }
    }
}
