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
package org.eclipse.mat.inspections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.query.GroupByValueQuery;
import org.eclipse.mat.inspections.query.TopConsumers2Query;
import org.eclipse.mat.inspections.query.collections.ArraysBySizeQuery;
import org.eclipse.mat.inspections.query.collections.CollectionUtil;
import org.eclipse.mat.inspections.query.collections.CollectionsBySizeQuery;
import org.eclipse.mat.inspections.query.collections.MapCollisionRatioQuery;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.QueryUtil;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.test.Params;
import org.eclipse.mat.test.QuerySpec;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.util.IProgressListener;


@Name("Component Report")
@Category("Leak Identification")
public class ComponentReportQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SectionSpec componentReport = new SectionSpec("Component Report");
        componentReport.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        int retained[] = snapshot.getRetainedSet(objects.getIds(listener), listener);
        Histogram histogram = snapshot.getHistogram(retained, listener);

        addRetainedSet(componentReport, histogram);

        addTopConsumer(componentReport, retained, listener);

        addCollections(componentReport, histogram, listener);

        addArraysBySize(componentReport, histogram, listener);

        addDuplicateStrings(componentReport, histogram, listener);

        addDegeneratedHashMaps(componentReport, histogram, listener);

        return componentReport;
    }

    private void addDegeneratedHashMaps(SectionSpec componentReport, Histogram histogram, IProgressListener listener)
                    throws Exception
    {
        SectionSpec mapCollisionRatiosSpec = new SectionSpec("Hash Map Collision Ratios");

        Map<Integer, CollectionUtil.Info> metadata = new HashMap<Integer, CollectionUtil.Info>();

        // prepare meta-data of known collections
        for (CollectionUtil.Info info : CollectionUtil.knownCollections)
        {
            if (!info.isHashed())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            int classId = record.getClassId();
            if (metadata.containsKey(classId))
            {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                MapCollisionRatioQuery collisions = new MapCollisionRatioQuery();
                collisions.snapshot = snapshot;
                
                int[] objectIds = record.getObjectIds();
                if (objectIds.length > 20000)
                {
                    int[] copy = new int[20000];
                    System.arraycopy(objectIds, 0, copy, 0, copy.length);
                    objectIds = copy;
                }
                collisions.objects = QueryUtil.asArgument(objectIds);

                QuerySpec collisionsSpec = new QuerySpec(clazz.getName());
                collisionsSpec.setResult(QueryUtil.execute(collisions, listener));
                mapCollisionRatiosSpec.add(collisionsSpec);
            }
        }

        if (!mapCollisionRatiosSpec.getChildren().isEmpty())
            componentReport.add(mapCollisionRatiosSpec);
    }

    private void addDuplicateStrings(SectionSpec componentReport, Histogram histogram, IProgressListener listener)
                    throws Exception
    {
        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (!"char[]".equals(record.getLabel()))
                continue;
            
            GroupByValueQuery groupBy = new GroupByValueQuery();
            groupBy.snapshot = snapshot;
            
            int[] objectIds = record.getObjectIds();
            if (objectIds.length > 100000)
            {
                int[] copy = new int[100000];
                System.arraycopy(objectIds, 0, copy, 0, copy.length);
                objectIds = copy;
            }
            groupBy.objects = QueryUtil.asArgument(objectIds);

            QuerySpec groupBySpec = new QuerySpec("Duplicate Strings");
            groupBySpec.set(Params.Rendering.FILTER, "Objects=>10");
            groupBySpec.setResult(QueryUtil.execute(groupBy, listener));
            componentReport.add(groupBySpec);
        }
    }

    private void addCollections(SectionSpec componentReport, Histogram histogram, IProgressListener listener)
                    throws Exception
    {
        long threshold = histogram.getNumberOfObjects() / 10;

        SectionSpec collectionbySizeSpec = new SectionSpec("Collections By Size");

        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();

        // prepare meta-data of known collections
        for (CollectionUtil.Info info : CollectionUtil.knownCollections)
        {
            if (info.getSizeField() == null)
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (record.getNumberOfObjects() < threshold)
                continue;

            if (metadata.containsKey(record.getClassId()))
            {
                CollectionsBySizeQuery bySize = new CollectionsBySizeQuery();
                bySize.snapshot = snapshot;
                bySize.objects = QueryUtil.asArgument(record.getObjectIds());

                IClass clazz = (IClass) snapshot.getObject(record.getClassId());
                QuerySpec bySizeSpec = new QuerySpec(clazz.getName());
                bySizeSpec.setResult(QueryUtil.execute(bySize, listener));
                collectionbySizeSpec.add(bySizeSpec);
            }
        }

        if (!collectionbySizeSpec.getChildren().isEmpty())
            componentReport.add(collectionbySizeSpec);
    }

    private void addArraysBySize(SectionSpec componentReport, Histogram retained, IProgressListener listener)
                    throws Exception
    {
        long threshold = retained.getNumberOfObjects() / 10;

        SectionSpec arrayFillRatioSpec = new SectionSpec("Arrays By Size");

        for (ClassHistogramRecord record : retained.getClassHistogramRecords())
        {
            if (record.getNumberOfObjects() < threshold)
                continue;

            IClass clazz = (IClass) snapshot.getObject(record.getClassId());
            if (clazz.isArrayType())
            {
                ArraysBySizeQuery fillRatio = new ArraysBySizeQuery();
                fillRatio.snapshot = snapshot;
                fillRatio.objects = QueryUtil.asArgument(record.getObjectIds());

                QuerySpec fillRatioSpec = new QuerySpec(clazz.getName());
                fillRatioSpec.setResult(QueryUtil.execute(fillRatio, listener));
                arrayFillRatioSpec.add(fillRatioSpec);
            }
        }

        if (!arrayFillRatioSpec.getChildren().isEmpty())
            componentReport.add(arrayFillRatioSpec);
    }

    private void addTopConsumer(SectionSpec componentReport, int[] retained, IProgressListener listener)
                    throws Exception
    {
        TopConsumers2Query consumers = new TopConsumers2Query();
        consumers.snapshot = snapshot;
        consumers.objects = retained;

        QuerySpec topConsumers = new QuerySpec("Top Consumers");
        topConsumers.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        topConsumers.set(Params.Html.COLLAPSED, Boolean.FALSE.toString());
        topConsumers.setResult(QueryUtil.execute(consumers, listener));
        componentReport.add(topConsumers);
    }

    private void addRetainedSet(SectionSpec componentReport, Histogram histogram)
    {
        QuerySpec retainedSet = new QuerySpec("Retained Set");
        retainedSet.setResult(histogram);
        componentReport.add(retainedSet);
    }

}
