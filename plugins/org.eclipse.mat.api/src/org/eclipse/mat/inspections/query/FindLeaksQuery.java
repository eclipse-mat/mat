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
package org.eclipse.mat.inspections.query;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsRecord;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

@Category(Category.HIDDEN)
public class FindLeaksQuery implements IQuery
{

    // ///////////////////////////////////////////
    //
    // static fields
    //
    // ///////////////////////////////////////////

    private final static Set<String> REFERENCE_FIELD_SET = new HashSet<String>(Arrays
                    .asList(new String[] { "referent" }));
    private final static int MAX_DEPTH = 1000;

    // ////////////////////////////////////////////
    //
    // Command parameters
    //
    // ////////////////////////////////////////////

    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    @Help("Objects/classes retaining more than this threshold are considered suspicious")
    public int threshold_percent = 20;

    @Argument(isMandatory = false)
    public int max_paths = 10000;

    // @Argument(isMandatory = false, flag = "big_drop_ratio")
    public double big_drop_ratio = 0.7;

    public IResult execute(IProgressListener listener) throws Exception
    {
        long totalHeap;
        int[] topDominators;

        totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();
        topDominators = snapshot.getImmediateDominatedIds(-1);
        long threshold = threshold_percent * totalHeap / 100;

        /*
         * find suspect single objects
         */
        listener.subTask("Searching suspicious single objects ...");

        ArrayInt suspiciousObjects = new ArrayInt();
        Set<String> suspectNames = new HashSet<String>();
        int i = 0;
        while (i < topDominators.length && snapshot.getRetainedHeapSize(topDominators[i]) > threshold)
        {
            suspiciousObjects.add(topDominators[i]);
            suspectNames.add(snapshot.getClassOf(topDominators[i]).getName());
            i++;
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        /*
         * Find suspect classes
         */
        listener.subTask("Searching suspicious groups of objects ...");

        Histogram histogram = groupByClasses(topDominators, listener);
        ArrayList<ClassHistogramRecord> suspiciousClasses = new ArrayList<ClassHistogramRecord>();

        ClassHistogramRecord[] classRecords = histogram.getClassHistogramRecords().toArray(new ClassHistogramRecord[0]);
        Arrays.sort(classRecords, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        int k = 0;
        while (k < classRecords.length && classRecords[k].getRetainedHeapSize() > threshold)
        {
            // avoid showing class-suspect for s.th. which was found on object
            // level
            if (!suspectNames.contains(classRecords[k].getLabel()))
            {
                suspiciousClasses.add(classRecords[k]);
            }
            k++;
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        /*
         * build the results
         */
        return buildResult(suspiciousObjects, suspiciousClasses, totalHeap, threshold, listener);

    }

    private Histogram groupByClasses(int[] dominated, IProgressListener listener) throws SnapshotException
    {
        Histogram histogram = snapshot.getHistogram(dominated, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Collection<ClassHistogramRecord> records = histogram.getClassHistogramRecords();
        ClassHistogramRecord[] arr = new ClassHistogramRecord[records.size()];
        int i = 0;

        for (ClassHistogramRecord record : records)
        {
            record.setRetainedHeapSize(sumRetainedSize(record.getObjectIds(), snapshot));
            arr[i++] = record;
        }

        Collection<ClassLoaderHistogramRecord> loaderRecords = histogram.getClassLoaderHistogramRecords();
        ClassLoaderHistogramRecord[] loaderArr = new ClassLoaderHistogramRecord[loaderRecords.size()];
        i = 0;

        for (ClassLoaderHistogramRecord record : loaderRecords)
        {
            long retainedSize = 0;
            for (ClassHistogramRecord classRecord : record.getClassHistogramRecords())
            {
                retainedSize += classRecord.getRetainedHeapSize();
            }

            record.setRetainedHeapSize(retainedSize);
            loaderArr[i++] = record;
        }

        return histogram;

    }

    private long sumRetainedSize(int[] objectIds, ISnapshot snapshot) throws SnapshotException
    {
        long sum = 0;
        for (int id : objectIds)
        {
            sum += snapshot.getRetainedHeapSize(id);
        }
        return sum;
    }

    private AccumulationPoint findAccumulationPoint(int bigObjectId) throws SnapshotException
    {
        int dominator = bigObjectId;
        double dominatorRetainedSize = snapshot.getRetainedHeapSize(dominator);
        int dominated[] = snapshot.getImmediateDominatedIds(dominator);

        int depth = 0;
        while (dominated != null && dominated.length != 0 && depth < MAX_DEPTH)
        {
            double dominatedRetainedSize = snapshot.getRetainedHeapSize(dominated[0]);
            if (dominatedRetainedSize / dominatorRetainedSize < big_drop_ratio) { return new AccumulationPoint(snapshot
                            .getObject(dominator)); }

            dominatorRetainedSize = dominatedRetainedSize;
            dominator = dominated[0];
            dominated = snapshot.getImmediateDominatedIds(dominator);
            depth++;
        }

        if (dominated == null || dominated.length == 0)
            return new AccumulationPoint(snapshot.getObject(dominator));

        return null;
    }

    private SuspectRecord buildSuspectRecordGroupOfObjects(ClassHistogramRecord record,
                    IProgressListener listener) throws SnapshotException
    {
        int[] objectIds = getRandomIds(record.getObjectIds());
        IObject suspectClass = snapshot.getObject(record.getClassId());

        // calculate the shortest paths to all
        // avoid weak paths
        Map<IClass, Set<String>> excludeMap = new HashMap<IClass, Set<String>>();
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.WeakReference", true);
        for (IClass clazz : classes)
        {
            excludeMap.put(clazz, REFERENCE_FIELD_SET);
        }

        IMultiplePathsFromGCRootsComputer comp = snapshot.getMultiplePathsFromGCRoots(objectIds, excludeMap);

        MultiplePathsFromGCRootsRecord[] records = comp.getPathsByGCRoot(listener);
        ArrayIntBig commonPath = new ArrayIntBig();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        int numPaths = comp.getAllPaths(new VoidProgressListener()).length;
        int diff = objectIds.length - numPaths;
        if (diff > 0)
        {
            listener.sendUserMessage(IProgressListener.Severity.INFO, MessageFormat.format(
                            "Couldn't find paths for {0} of the {1} objects", new Object[] { diff, objectIds.length }),
                            null);
        }
        setRetainedSizesForMPaths(records, snapshot);
        Arrays.sort(records, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());

        MultiplePathsFromGCRootsRecord parentRecord = records[0];
        
//        parentRecord.getReferencedRetainedSize()
        int threshold = (int)(0.8 * objectIds.length);

        while (parentRecord.getCount() > threshold)
        {
//            System.out.println("count: " + parentRecord.getCount());
            commonPath.add(parentRecord.getObjectId());

            MultiplePathsFromGCRootsRecord[] children = parentRecord.nextLevel();
            if (children == null || children.length == 0)
            {
                // reached the end ?! report the parent as it is big enough
                AccumulationPoint accPoint = new AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()));
                SuspectRecordGroupOfObjects result = new SuspectRecordGroupOfObjects(suspectClass, record
                                .getObjectIds(), record.getRetainedHeapSize(), accPoint, commonPath.toArray(), comp);
                return result;
            }
            setRetainedSizesForMPaths(children, snapshot);
            Arrays.sort(children, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());

            if ((double) children[0].getReferencedRetainedSize() / (double) parentRecord.getReferencedRetainedSize() < big_drop_ratio)
            {
                // there is a big drop here - return the parent
                AccumulationPoint accPoint = new AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()));
                SuspectRecordGroupOfObjects result = new SuspectRecordGroupOfObjects(suspectClass, record
                                .getObjectIds(), record.getRetainedHeapSize(), accPoint, commonPath.toArray(), comp);
                return result;
            }

            // no big drop - take the biggest child and try again
            parentRecord = children[0];
        }

        // return a SuspectRecord without an accumulation point
        return new SuspectRecordGroupOfObjects(suspectClass, record.getObjectIds(), record.getRetainedHeapSize(), null,
                        commonPath.toArray(), comp);
    }

    private void setRetainedSizesForMPaths(MultiplePathsFromGCRootsRecord[] records, ISnapshot snapshot)
                    throws SnapshotException
    {
        for (MultiplePathsFromGCRootsRecord rec : records)
        {
            int[] referencedObjects = rec.getReferencedObjects();
            long retained = 0;
            for (int objectId : referencedObjects)
            {
                retained += snapshot.getRetainedHeapSize(objectId);
            }
            rec.setReferencedRetainedSize(retained);
        }
    }

    private IResult buildResult(ArrayInt suspiciousObjects, ArrayList<ClassHistogramRecord> suspiciousClasses,
                    long totalHeap, long threshold, IProgressListener listener) throws SnapshotException
    {
        SuspectRecord[] allSuspects = new SuspectRecord[suspiciousObjects.size() + suspiciousClasses.size()];
        int j = 0;
        int[] suspectObjIds = suspiciousObjects.toArray();
        for (int objectId : suspectObjIds)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            IObject suspectObject = snapshot.getObject(objectId);
            AccumulationPoint accPoint = findAccumulationPoint(objectId);
            SuspectRecord r = new SuspectRecord(suspectObject, suspectObject.getRetainedHeapSize(), accPoint);

            allSuspects[j++] = r;
        }

        for (ClassHistogramRecord record : suspiciousClasses)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            SuspectRecord r = buildSuspectRecordGroupOfObjects(record, /*(long) (threshold * 0.7),*/ listener);
            allSuspects[j++] = r;
        }

        return new SuspectsResultTable(allSuspects, totalHeap);
    }

    private int[] getRandomIds(int[] objectIds)
    {
        if (objectIds.length < max_paths)
            return objectIds;

        Logger.getLogger(getClass().getName()).log(Level.INFO,
                        "Too many suspect instances ("+ objectIds.length +"). Will use " + max_paths + " random from them to search for common path.");
        
        Random random = new Random();
        int length = objectIds.length - 1;
        BitField visited = new BitField(length);

        int[] result = new int[max_paths];
        for (int i = 0; i < max_paths; i++)
        {
            int index = random.nextInt(length);
            while (visited.get(index))
                index = random.nextInt(length);

            visited.set(index);
            result[i] = objectIds[index];
        }
        return result;
    }

    public static class AccumulationPoint
    {
        IObject object;

        public AccumulationPoint(IObject object)
        {
            this.object = object;
        }

        public IObject getObject()
        {
            return object;
        }
    }

    public static class AccumulationPointOfGroupOfObject extends AccumulationPoint
    {
        int[] commonPath;
        IMultiplePathsFromGCRootsComputer pathsComputer;

        public AccumulationPointOfGroupOfObject(IObject object, int[] commonPath,
                        IMultiplePathsFromGCRootsComputer pathsComputer)
        {
            super(object);
            this.commonPath = commonPath;
            this.pathsComputer = pathsComputer;
        }

        public int[] getCommonPath()
        {
            return commonPath;
        }

        public IMultiplePathsFromGCRootsComputer getPathsComputer()
        {
            return pathsComputer;
        }
    }

    public static class SuspectRecordGroupOfObjects extends SuspectRecord
    {
        int[] commonPath;
        IMultiplePathsFromGCRootsComputer pathsComputer;
        int[] suspectInstances;

        SuspectRecordGroupOfObjects(IObject suspect, int[] suspectInstances, long suspectRetained,
                        AccumulationPoint accumulationPoint, int[] commonPath,
                        IMultiplePathsFromGCRootsComputer pathsComputer)
        {
            super(suspect, suspectRetained, accumulationPoint);
            this.suspectInstances = suspectInstances;
            this.commonPath = commonPath;
            this.pathsComputer = pathsComputer;
        }

        public int[] getCommonPath()
        {
            return commonPath;
        }

        public IMultiplePathsFromGCRootsComputer getPathsComputer()
        {
            return pathsComputer;
        }

        public int[] getSuspectInstances()
        {
            return suspectInstances;
        }

    }

    public static class SuspectRecord
    {
        IObject suspect;

        long suspectRetained;
        AccumulationPoint accumulationPoint;

        SuspectRecord(IObject suspect, long suspectRetained, AccumulationPoint accumulationPoint)
        {
            this.suspect = suspect;
            this.suspectRetained = suspectRetained;
            this.accumulationPoint = accumulationPoint;
        }

        public IObject getSuspect()
        {
            return suspect;
        }

        public long getSuspectRetained()
        {
            return suspectRetained;
        }

        public AccumulationPoint getAccumulationPoint()
        {
            return accumulationPoint;
        }
    }

    public static class SuspectsResultTable implements IResultTable
    {
        SuspectRecord[] data;
        long totalHeap;

        public SuspectsResultTable(SuspectRecord[] data, long totalHeap)
        {
            this.data = data;
            this.totalHeap = totalHeap;
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //

                            .addContext(new ContextProvider("Leak Suspect")
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return getLeakSuspect(row);
                                }
                            }) //

                            .addContext(new ContextProvider("Accumulation Point")
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return getAccumulationPoint(row);
                                }
                            }) //

                            .build();
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Leak Suspect"), //
                            new Column("# Objects", Long.class), //
                            new Column("Suspect's Retained Heap", Long.class), //
                            new Column("Suspect's %", Double.class).formatting(NumberFormat.getPercentInstance()), //
                            new Column("Accumulation Point"), //
                            new Column("Acc. Point Retained Heap", Long.class), //
                            new Column("Acc. Point %", Double.class).formatting(NumberFormat.getPercentInstance()) };
        }

        public int getRowCount()
        {
            return data.length;
        }

        public SuspectRecord getRow(int rowId)
        {
            return data[rowId];
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            SuspectRecord suspect = (SuspectRecord) row;
            switch (columnIndex)
            {
                case 0:
                    return suspect.suspect.getTechnicalName();
                case 1:
                    return suspect instanceof SuspectRecordGroupOfObjects ? ((SuspectRecordGroupOfObjects) suspect).suspectInstances.length
                                    : 1;
                case 2:
                    return suspect.suspectRetained;
                case 3:
                    return Double.valueOf((double) suspect.suspectRetained / (double) totalHeap);
                case 4:
                    return suspect.accumulationPoint == null ? "<not found>" : suspect.accumulationPoint.getObject()
                                    .getTechnicalName();
                case 5:
                    return suspect.accumulationPoint == null ? 0 : suspect.accumulationPoint.getObject()
                                    .getRetainedHeapSize();
                case 6:
                    return suspect.accumulationPoint == null ? 0 : Double.valueOf((double) suspect.accumulationPoint
                                    .getObject().getRetainedHeapSize()
                                    / (double) totalHeap);
                default:
                    throw new IllegalArgumentException("invalid column index");
            }
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((SuspectRecord) row).suspect.getObjectId();
                }
            };
        }

        IContextObject getLeakSuspect(Object row)
        {
            final SuspectRecord suspect = (SuspectRecord) row;

            if (suspect instanceof SuspectRecordGroupOfObjects)
            {
                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return suspect.suspect.getObjectId();
                    }

                    public int[] getObjectIds()
                    {
                        return ((SuspectRecordGroupOfObjects) suspect).suspectInstances;
                    }

                    public String getOQL()
                    {
                        return null;
                    }
                };
            }
            else
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return suspect.suspect.getObjectId();
                    }
                };
            }
        }

        IContextObject getAccumulationPoint(final Object row)
        {
            final SuspectRecord suspect = (SuspectRecord) row;

            if (suspect.accumulationPoint == null)
                return null;

            return new IContextObject()
            {
                public int getObjectId()
                {
                    return suspect.accumulationPoint.getObject().getObjectId();
                }
            };
        }

        public SuspectRecord[] getData()
        {
            return data;
        }

        public long getTotalHeap()
        {
            return totalHeap;
        }
    }
}
