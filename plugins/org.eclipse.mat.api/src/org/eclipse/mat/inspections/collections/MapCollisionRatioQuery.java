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

import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

@Name("Map Collision Ratio")
@Category("Java Collections")
@Help("Prints a frequency distribution of the collistion ratios of map-like collections.\n\n"
                + "The below listed map-like collections are known to the query. "
                + "One additional custom map-like (e.g. non-JDK) collection "
                + "can be specified by the 'collection', 'size_attribute' and " // 
                + "'array_attribute' argument.\n" //
                + "Known collections:\n" //
                + "java.util.HashMap\n" // 
                + "java.util.Properties\n" //
                + "java.util.Hashtable\n" //
                + "java.util.WeakHashMap\n" //
                + "java.util.concurrent.ConcurrentHashMap$Segment")
public class MapCollisionRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    @Help("The hashtable objects. Non-hashtable objects will be ignored.")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    @Help("Number of segments used for the frequency distribution.")
    public int segments = 5;

    @Argument(isMandatory = false)
    @Help("Optional: fully qualified class name of a custom (e.g. non-JDK) map-like collection class.")
    public String collection;

    @Argument(isMandatory = false)
    @Help("The size attribute of the (optionally) specified map-like collection. Must be of type int or Integer.")
    public String size_attribute;

    @Argument(isMandatory = false)
    @Help("The array attribute of the (optionally) specified map-like collection. Must be a Java array.")
    public String array_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        listener.subTask("Calculating Map Collision Ratios...");

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = CollectionUtil.getKnownMaps(snapshot);

        // prepare meta-data from user provided the collection argument
        if (collection != null)
        {
            if (size_attribute == null || array_attribute == null)
            {
                String msg = "If the collection argument is set to a custom (e.g. non-JDK) collection class, "
                                + "the size_attribute and array_attribute argument must be set. Otherwise, the query "
                                + "cannot calculate the collision ratio.";
                throw new SnapshotException(msg);
            }

            CollectionUtil.Info info = new CollectionUtil.Info(collection, size_attribute, array_attribute);
            Collection<IClass> classes = snapshot.getClassesByName(collection, true);

            if (classes.isEmpty())
                listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageFormat.format(
                                "Class ''{0}'' not found in heap dump.", collection), null);

            for (IClass clasz : classes)
                metadata.put(clasz.getObjectId(), info);
        }

        // create frequency distribution
        Quantize.Builder builder = Quantize.linearFrequencyDistribution("Collision Ratio", 0, 1, (double) 1
                        / (double) segments);
        builder.column("# Objects", Quantize.COUNT);
        builder.column("Shallow Heap", Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();

        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

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

        if (size == 0)
            return 0;

        IObjectArray table = info.getBackingArray(hashtableObject);

        return (double) (size - CollectionUtil.getNumberOfNoNullArrayElements(table)) / (double) size;
    }
}
