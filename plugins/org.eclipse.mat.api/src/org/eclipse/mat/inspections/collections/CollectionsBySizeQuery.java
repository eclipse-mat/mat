/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others
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
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.FieldSizeArrayCollectionExtractor;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("collections_grouped_by_size")
@Icon("/META-INF/icons/collection_size.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"java.util.AbstractCollection",
    "java.util.jar.Attributes",
    "java.util.Dictionary",
    "java.lang.ThreadLocal$ThreadLocalMap",
    "java.util.concurrent.ConcurrentHashMap$Segment",
    "java.util.concurrent.ConcurrentHashMap$CollectionView",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.Collections$SynchronizedCollection",
    "java.util.Collections$SynchronizedMap",
    "java.util.Collections$UnmodifiableCollection",
    "java.util.Collections$UnmodifiableMap",
    "java.util.Collections$CheckedCollection",
    "java.util.Collections$CheckedMap$CheckedEntrySet",
    "java.util.Collections$SetFromMap",
    "java.util.Collections$EmptyList",
    "java.util.Collections$EmptySet",
    "java.util.Properties$EntrySet",
    "java.util.ResourceBundle",
    "java.awt.RenderingHints",
    "java.beans.beancontext.BeanContextSupport",
    "sun.awt.WeakIdentityHashMap",
    "javax.script.SimpleBindings",
    "javax.management.openmbean.TabularDataSupport",
    "com.ibm.jvm.util.HashMapRT",
    "com.sap.engine.lib.util.AbstractDataStructure",
    "org.eclipse.mat.collect.SetInt",
    "org.eclipse.mat.collect.SetLong",
    "org.eclipse.mat.collect.QueueInt",
    "org.eclipse.mat.collect.ArrayInt",
    "org.eclipse.mat.collect.ArrayLong",
    "org.eclipse.mat.collect.ArrayIntBig",
    "org.eclipse.mat.collect.ArrayLongBig",

    "java.util.AbstractMap",
    "java.util.jar.Attributes",
    "java.util.Dictionary",
    "java.lang.ThreadLocal$ThreadLocalMap",
    "java.util.concurrent.ConcurrentHashMap$Segment",
    "java.util.concurrent.ConcurrentHashMap$CollectionView",
    "java.util.Collections$SynchronizedMap",
    "java.util.Collections$UnmodifiableMap",
    "java.util.Collections$CheckedMap",
    "java.util.Collections$SetFromMap",
    "java.util.Collections$EmptyMap",
    "java.util.ResourceBundle",
    "java.beans.beancontext.BeanContextSupport",
    "sun.awt.WeakIdentityHashMap",
    "javax.script.SimpleBindings",
    "javax.management.openmbean.TabularDataSupport",
    "com.ibm.jvm.util.HashMapRT",
    "com.sap.engine.lib.util.AbstractDataStructure",
    "org.eclipse.mat.collect.HashMapIntLong",
    "org.eclipse.mat.collect.HashMapIntObject",
    "org.eclipse.mat.collect.HashMapLongObject",
    "org.eclipse.mat.collect.HashMapObjectLong",
})
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
            throw new IllegalArgumentException(Messages.CollectionsBySizeQuery_NeedSizeAndArrayOrNone);
        }

        // group by length attribute
        Quantize.Builder builder = Quantize.valueDistribution(new Column(Messages.CollectionsBySizeQuery_Column_Length,
                        int.class).noTotals());
        builder.column(Messages.CollectionsBySizeQuery_Column_NumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_BYTES, SortDirection.DESC);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        runQuantizer(listener, quantize, specificExtractor, collection);
        return quantize.getResult();
    }

    private void runQuantizer(IProgressListener listener, Quantize quantize, ICollectionExtractor specificExtractor,
                    String specificClass) throws SnapshotException
    {

        final long LIMIT = 20;
        HashMapIntLong exceptions = new HashMapIntLong();
        int counter = 0;
        IClass type = null;
        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                IObject obj = snapshot.getObject(objectId);
                if (counter++ % 1000 == 0 && !obj.getClazz().equals(type))
                {
                    type = obj.getClazz();
                    listener.subTask(Messages.CollectionsBySizeQuery_CollectingSizes + "\n" + type.getName()); //$NON-NLS-1$
                }
                try
                {
                    AbstractExtractedCollection<?, ?> coll = CollectionExtractionUtils.extractCollection(obj, specificClass,
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
                    int classId = obj.getClazz().getObjectId();
                    if (!exceptions.containsKey(classId))
                    {
                        exceptions.put(classId, 0);
                    }
                    long c =  exceptions.get(classId);
                    exceptions.put(classId, c + 1);
                    if (c < LIMIT)
                    {
                        listener.sendUserMessage(
                                        IProgressListener.Severity.INFO,
                                        MessageUtil.format(Messages.CollectionsBySizeQuery_IgnoringCollection,
                                                        obj.getTechnicalName()), e);
                    }
                }

                if (listener.isCanceled())
                    return;
            }
        }
    }
}
