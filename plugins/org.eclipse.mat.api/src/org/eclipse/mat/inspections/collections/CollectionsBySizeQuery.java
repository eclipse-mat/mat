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
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@Name("Collections Grouped By Size")
@Category("Java Collections")
@Help("Distribution histogram of given collections by their size.\n\n"
                + "The below mentioned collections are known to the query. "
                + "One additional custom collection (e.g. non-JDK) collection "
                + "can be specified by the 'collection' and 'size_attribute' argument.\n" //
                + "Known collections:\n" //
                + "java.util.ArrayList\n" //
                + "java.util.TreeMap\n" // 
                + "java.util.HashMap\n" // 
                + "java.util.Hashtable\n" //
                + "java.util.Properties\n" //
                + "java.util.Vector\n" //
                + "java.util.WeakHashMap\n" //
                + "java.util.concurrent.ConcurrentHashMap$Segment")
public class CollectionsBySizeQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    @Help("The collection objects. Non-collection objects will be ignored.")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    @Help("Optional: fully qualified class name of a custom (e.g. non-JDK) collection class.")
    public String collection;

    @Argument(isMandatory = false)
    @Help("The size attribute of the (optionally) specified collection class. Must be of type int or Integer.")
    public String size_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        listener.subTask("Collecting collection sizes...");

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
                String msg = "If the collection argument is set to a custom (e.g. non-JDK) collection class, "
                                + "the size_attribute must be set. Otherwise, the query cannot determine the "
                                + "size of the collection.";
                throw new SnapshotException(msg);
            }

            CollectionUtil.Info info = new CollectionUtil.Info(collection, size_attribute, null);
            Collection<IClass> classes = snapshot.getClassesByName(collection, true);

            if (classes.isEmpty())
                listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(
                                "Class ''{0}'' not found in heap dump.", collection), null);

            for (IClass clasz : classes)
                metadata.put(clasz.getObjectId(), info);
        }

        // group by length attribute
        Quantize.Builder builder = Quantize.valueDistribution(new Column("Length", int.class));
        builder.column("# Objects", Quantize.COUNT);
        builder.column("Shallow Heap", Quantize.SUM_LONG, SortDirection.DESC);
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
                    int size = info.getSize(obj);
                    quantize.addValue(objectId, size, null, obj.getUsedHeapSize());
                }

                if (listener.isCanceled())
                    break ObjectLoop;
            }
        }

        return quantize.getResult();
    }

}
