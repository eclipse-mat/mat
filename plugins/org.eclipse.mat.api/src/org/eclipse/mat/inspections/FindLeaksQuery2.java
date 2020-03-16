/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - version for comparing two snapshots
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.FindLeaksQuery.SuspectRecord;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsRecord;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.NumberFormat;

@CommandName("find_leaks2")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/leak.gif")
public class FindLeaksQuery2 implements IQuery
{

    // ///////////////////////////////////////////
    //
    // static fields
    //
    // ///////////////////////////////////////////

    private final static Set<String> REFERENCE_FIELD_SET = new HashSet<String>(Arrays
                    .asList(new String[] { "referent" })); //$NON-NLS-1$
    private final static int MAX_DEPTH = 1000;

    // ////////////////////////////////////////////
    //
    // Command parameters
    //
    // ////////////////////////////////////////////

    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot baseline;

    @Argument(isMandatory = false)
    public int threshold_percent = 2;

    @Argument(isMandatory = false)
    public int max_paths = 10000;

    // @Argument(isMandatory = false, flag = "big_drop_ratio")
    public double big_drop_ratio = 0.7;

    @Argument(isMandatory = false)
    public String options = "-prefix"; //$NON-NLS-1$

    @Argument(isMandatory = false)
    public Pattern mask = Pattern.compile("\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$|(?<=\\p{javaJavaIdentifierPart}\\[)\\d+(?=\\])"); //$NON-NLS-1$

    @Argument(isMandatory = false, flag = "x")
    public String[] extraReferences = new String[] {
                    "java.util.HashMap$Node:key", //$NON-NLS-1$
                    "java.util.Hashtable$Entry:key", //$NON-NLS-1$
                    "java.util.WeakHashMap$Entry:referent", //$NON-NLS-1$
                    "java.util.concurrent.ConcurrentHashMap$Node:key" //$NON-NLS-1$
    };

    @Argument(isMandatory = false, flag = "xfile")
    public File extraReferencesListFile;

    static final int retainedDiffCol = 5;
    static final int simpleDiffCol = 2;

    public IResult execute(IProgressListener listener) throws Exception
    {
        long totalHeap;

        totalHeap = baseline.getSnapshotInfo().getUsedHeapSize();
        long threshold = threshold_percent * totalHeap / 100;

        IResultTree baseTree = callDominatorTree(listener, baseline);
        IResultTree currTree = callDominatorTree(listener, snapshot);

        String queryId = "comparetablesquery -mode DIFF_RATIO_TO_FIRST"; //$NON-NLS-1$
        if (options != null && options.length() > 0)
            queryId += " " + options; //$NON-NLS-1$

        SnapshotQuery queryc = SnapshotQuery.parse(queryId, snapshot);

        List<IResultTree> r = new ArrayList<IResultTree>();
        r.add(baseTree);
        r.add(currTree);
        queryc.setArgument("tables", r); //$NON-NLS-1$
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(baseline);
        snapshots.add(snapshot);
        queryc.setArgument("snapshots", snapshots); //$NON-NLS-1$

        if (mask != null && mask.pattern().length() > 0)
            queryc.setArgument("mask", mask); //$NON-NLS-1$
        if (extraReferences != null && extraReferences.length > 0)
            queryc.setArgument("extraReferences", Arrays.asList(extraReferences)); //$NON-NLS-1$
        if (extraReferencesListFile != null)
            queryc.setArgument("extraReferencesListFile", extraReferencesListFile); //$NON-NLS-1$

        RefinedResultBuilder rbc = queryc.refine(listener);
        rbc.setSortOrder(retainedDiffCol, SortDirection.DESC);
        final RefinedTree compTree = (RefinedTree)rbc.build();
        List<?>topDominators = compTree.getElements();

        /*
         * find suspect single objects
         */
        listener.subTask(Messages.FindLeaksQuery_SearchingSingleObjects);

        List<ContextProvider> provs = compTree.getResultMetaData().getContextProviders();
        // Get the last - in case the first is also this snapshot, so has a context provider
        ContextProvider cp = provs.get(provs.size() - 1);
        ArrayInt suspiciousObjects = new ArrayInt();
        Set<String> suspectNames = new HashSet<String>();
        int i = 0;
        HashMapIntObject<ClassRecord>map = new HashMapIntObject<ClassRecord>();
        final HashMapIntObject<Object>rowmap = new HashMapIntObject<Object>(); 
        while (i < topDominators.size())
        {
            Object row = topDominators.get(i);
            IContextObject ctx = cp.getContext(row);
            if (ctx != null)
            {
                int objId = ctx.getObjectId();
                if (objId >= 0)
                {
                    long deltaRetained = readCol(compTree, row, retainedDiffCol);
                    IClass cls = snapshot.getClassOf(objId);
                    if (deltaRetained > threshold)
                    {
                        suspiciousObjects.add(objId);
                        suspectNames.add(cls.getName());
                        rowmap.put(objId, row);
                    }
                    else
                    {
                        // Add to types
                        ClassRecord cr = map.get(cls.getObjectId());
                        if (cr == null)
                        {
                            cr = new ClassRecord(cls.getName(), cls.getObjectId());
                            map.put(cls.getObjectId(), cr);
                        }
                        long deltaSimple = readCol(compTree, row, simpleDiffCol);
                        cr.addObj(objId, deltaSimple, deltaRetained);
                        rowmap.put(objId, row);
                    }
                }
            }
            i++;
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        /*
         * Find suspect classes
         */
        listener.subTask(Messages.FindLeaksQuery_SearchingGroupsOfObjects);

        ArrayList<ClassHistogramRecord> suspiciousClasses = new ArrayList<ClassHistogramRecord>();

        for (Iterator<ClassRecord> it = map.values(); it.hasNext(); )
        {
            ClassRecord cr = it.next();
            if (cr.retained > threshold)
            {
                ClassHistogramRecord chr = new ClassHistogramRecord(cr.name, cr.clsId, cr.objs.toArray(), cr.simple, cr.retained);
                suspiciousClasses.add(chr);
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        /*
         * build the results
         */
        final SuspectsResultTable ret = buildResult(suspiciousObjects, suspiciousClasses, totalHeap, compTree, rowmap, listener);

        // Indicate some interesting rows
        compTree.setSelectionProvider(new ISelectionProvider() {

            public boolean isSelected(Object row)
            {
                if (row == null)
                    return false;
                List<ContextProvider> provs = compTree.getResultMetaData().getContextProviders();
                IContextObject co = provs.get(provs.size() - 1).getContext(row);
                if (co == null)
                    return false;
                int objId = co.getObjectId();
                if (objId < 0)
                    return false;
                for (SuspectRecord sr : ret.getData())
                {
                    if (sr instanceof FindLeaksQuery.SuspectRecordGroupOfObjects)
                    {
                        // This is a group of objects, so check each one
                        FindLeaksQuery.SuspectRecordGroupOfObjects srg = (FindLeaksQuery.SuspectRecordGroupOfObjects)sr;
                        for (int o1 : srg.getSuspectInstances())
                        {
                            if (objId == o1)
                                return true;
                        }
                        /*
                         * Not required to check the suspect ID as that is the class
                         * but the dominator tree will be instances.
                         */
                    }
                    else
                    {
                        if (objId == sr.getSuspect().getObjectId())
                            return true;
                    }
                    org.eclipse.mat.inspections.FindLeaksQuery.AccumulationPoint accumulationPoint = sr.getAccumulationPoint();
                    if (accumulationPoint != null && objId == accumulationPoint.getObject().getObjectId())
                        return true;
                }
                return false;
            }

            public boolean isExpanded(Object row)
            {
                /* How deep - HtmlOutputter has a limit of 100, screen 25 */
                final int MAX_EXPAND = 30;
                if (row == null)
                    return false;
                List<ContextProvider> provs = compTree.getResultMetaData().getContextProviders();
                IContextObject co = provs.get(provs.size() - 1).getContext(row);
                if (co == null)
                    return false;
                int objId = co.getObjectId();
                if (objId < 0)
                    return false;
                for (SuspectRecord sr : ret.getData())
                {
                    if (sr instanceof FindLeaksQuery.SuspectRecordGroupOfObjects)
                    {
                        FindLeaksQuery.SuspectRecordGroupOfObjects srg = (FindLeaksQuery.SuspectRecordGroupOfObjects)sr;
                        for (int i = 0; i < Math.min(srg.getCommonPath().length - 1, MAX_EXPAND); ++i)
                        {
                            if (objId == srg.getCommonPath()[i])
                                return true;
                        }
                    }
                    org.eclipse.mat.inspections.FindLeaksQuery.AccumulationPoint accumulationPoint = sr.getAccumulationPoint();
                    if (accumulationPoint instanceof AccumulationPoint)
                    {
                        AccumulationPoint ap2 = (AccumulationPoint)accumulationPoint;
                        for (int i = 0; i < Math.min(ap2.getPath().length - 1, MAX_EXPAND); ++i)
                        {
                            if (objId == ap2.getPath()[i])
                                return true;
                        }
                    }
                }
                return false;
            }

        });
        CompositeResult cr = new CompositeResult();
        cr.addResult(Messages.FindLeaksQuery2_ComparedDominatorTrees, compTree);
        cr.addResult(Messages.FindLeaksQuery2_Leaks, ret);
        return cr;
    }

    /**
     * Read a column cell from the compared tree.
     * @param compTree
     * @param row
     * @param retainedDiffCol
     * @return
     */
    private long readCol(IResultTree compTree, final Object row, final int retainedDiffCol)
    {
        Object colv = compTree.getColumnValue(row, retainedDiffCol);
        long deltaRetained;
        if (colv instanceof Bytes)
            deltaRetained = ((Bytes)colv).getValue();
        else if (colv instanceof Number)
            deltaRetained = ((Number)colv).longValue();
        else 
            deltaRetained = 0;
        return deltaRetained;
    }

    private static class ClassRecord
    {
        String name;
        int clsId;
        ArrayInt objs;
        long simple;
        long retained;
        public ClassRecord(String name, int id)
        {
            this.name = name;
            clsId = id;
            objs = new ArrayInt();
        }

        public void addObj(int obj, long simple, long retained)
        {
            objs.add(obj);
            this.simple += simple;
            this.retained += retained;
        }
    }

    private IResultTree callDominatorTree(IProgressListener listener, ISnapshot snapshot) throws Exception
    {
        return (IResultTree) SnapshotQuery.lookup("dominator_tree", snapshot) //$NON-NLS-1$
                        .execute(listener);
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
            if (i % 10 == 0 && listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
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

    /**
     * Find the accumulation point by big drops in the delta size
     * @param bigObjectId
     * @param tree
     * @param row
     * @return
     * @throws SnapshotException
     */
    private FindLeaksQuery.AccumulationPoint findAccumulationPoint(int bigObjectId, IResultTree tree, Object row) throws SnapshotException
    {
        int dominator = bigObjectId;
        ArrayInt path = new ArrayInt();
        path.add(dominator);
        List<?> rows = null;
        long dominatorRetainedSize = readCol(tree, row, retainedDiffCol);

        List<ContextProvider> provs = tree.getResultMetaData().getContextProviders();
        ContextProvider cp = provs.get(provs.size() - 1);
        int depth = 0;
        while (tree.hasChildren(row) && (rows = tree.getChildren(row)) != null && rows.size() != 0 && depth < MAX_DEPTH)
        {
            long dominatedRetainedSize = this.readCol(tree, rows.get(0), retainedDiffCol); 
            if ((double)dominatedRetainedSize / dominatorRetainedSize < big_drop_ratio)
            {
                return new AccumulationPoint(snapshot.getObject(dominator), dominatorRetainedSize, path.toArray()); 
            }

            dominatorRetainedSize = dominatedRetainedSize;
            row = rows.get(0);
            // Should be a context if there was a retained size
            dominator = cp.getContext(row).getObjectId();
            path.add(dominator);
            depth++;
        }

        if (rows == null || rows.size() == 0)
            return new AccumulationPoint(snapshot.getObject(dominator), dominatorRetainedSize, path.toArray());

        return null;
    }

    private FindLeaksQuery.SuspectRecord buildSuspectRecordGroupOfObjects(ClassHistogramRecord record, IResultTree tree, HashMapIntObject<Object>rowmap, IProgressListener listener)
                    throws SnapshotException
    {
        int[] objectIds = getRandomIds(record.getObjectIds());
        IObject suspectClass = snapshot.getObject(record.getClassId());
        List<ContextProvider> provs = tree.getResultMetaData().getContextProviders();

        // calculate the shortest paths to all
        // avoid weak paths
        Map<IClass, Set<String>> excludeMap = new HashMap<IClass, Set<String>>();
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.WeakReference", true); //$NON-NLS-1$
        if (classes != null)
            for (IClass clazz : classes)
            {
                excludeMap.put(clazz, REFERENCE_FIELD_SET);
            }

        IMultiplePathsFromGCRootsComputer comp = snapshot.getMultiplePathsFromGCRoots(objectIds, excludeMap);

        MultiplePathsFromGCRootsRecord[] records = comp.getPathsByGCRoot(listener);
        ArrayIntBig commonPath = new ArrayIntBig();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        int numPaths = comp.getAllPaths(listener).length;
        int diff = objectIds.length - numPaths;
        if (diff > 0)
        {
            listener.sendUserMessage(IProgressListener.Severity.INFO, MessageUtil.format(
                            Messages.FindLeaksQuery_PathNotFound, diff, objectIds.length), null);
        }
        setRetainedSizesForMPaths(records, snapshot);
        Arrays.sort(records, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());

        MultiplePathsFromGCRootsRecord parentRecord = records[0];

        // parentRecord.getReferencedRetainedSize()
        int threshold = (int) (0.8 * objectIds.length);

        Object row = null;
        while (parentRecord.getCount() > threshold)
        {
            // System.out.println("count: " + parentRecord.getCount());
            commonPath.add(parentRecord.getObjectId());

            // Try to match path in dominator tree
            if (row == null)
            {
                // possibly find in dominator tree
                row = rowmap.get(parentRecord.getObjectId());
            }

            MultiplePathsFromGCRootsRecord[] children = parentRecord.nextLevel();
            if (children == null || children.length == 0)
            {
                // reached the end ?! report the parent as it is big enough
                int path[] = commonPath.toArray();
                FindLeaksQuery.AccumulationPoint accPoint = new FindLeaksQuery.AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()));
                if (rowmap.get(parentRecord.getObjectId()) == row)
                {
                    // Row is current, so use delta size
                    long deltaRetained = readCol(tree, row, retainedDiffCol);
                    accPoint = new AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()), deltaRetained, path);
                }
                FindLeaksQuery.SuspectRecordGroupOfObjects result = new FindLeaksQuery.SuspectRecordGroupOfObjects(suspectClass, record
                                .getObjectIds(), record.getRetainedHeapSize(), accPoint, commonPath.toArray(), comp);
                return result;
            }
            setRetainedSizesForMPaths(children, snapshot);
            Arrays.sort(children, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());

            long childReferencedRetainedSize = children[0].getReferencedRetainedSize();

            // Match child?
            if (row != null && tree.hasChildren(row))
            {
                //System.out.println("Finding ");
                for (Object row2 : tree.getChildren(row))
                {
                    IContextObject co = provs.get(provs.size() - 1).getContext(row2);
                    if (co != null && co.getObjectId() == children[0].getObjectId())
                    {
                        // Found again
                        row = row2;
                        //System.out.println("Found again "+parentRecord.getObjectId() + " " + row);
                        break;
                    }
                }
            }
            if ((double) childReferencedRetainedSize / (double) parentRecord.getReferencedRetainedSize() < big_drop_ratio)
            {
                // there is a big drop here - return the parent
                int path[] = commonPath.toArray();
                FindLeaksQuery.AccumulationPoint accPoint = new FindLeaksQuery.AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()));
                if (rowmap.get(parentRecord.getObjectId()) == row)
                {
                    // Row is current, so use delta size
                    long deltaRetained = readCol(tree, row, retainedDiffCol);
                    accPoint = new AccumulationPoint(snapshot.getObject(parentRecord.getObjectId()), deltaRetained, path);
                }
                FindLeaksQuery.SuspectRecordGroupOfObjects result = new FindLeaksQuery.SuspectRecordGroupOfObjects(suspectClass, record
                                .getObjectIds(), record.getRetainedHeapSize(), accPoint, path, comp);
                return result;
            }

            // no big drop - take the biggest child and try again
            parentRecord = children[0];
        }

        // return a SuspectRecord without an accumulation point
        return new FindLeaksQuery.SuspectRecordGroupOfObjects(suspectClass, record.getObjectIds(), record.getRetainedHeapSize(), null,
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

    private SuspectsResultTable buildResult(ArrayInt suspiciousObjects, ArrayList<ClassHistogramRecord> suspiciousClasses,
                    long totalHeap, IResultTree tree, HashMapIntObject<Object>rowmap, IProgressListener listener) throws SnapshotException
    {
        FindLeaksQuery.SuspectRecord[] allSuspects = new FindLeaksQuery.SuspectRecord[suspiciousObjects.size() + suspiciousClasses.size()];
        int j = 0;
        int[] suspectObjIds = suspiciousObjects.toArray();
        for (int objectId : suspectObjIds)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            IObject suspectObject = snapshot.getObject(objectId);
            FindLeaksQuery.AccumulationPoint accPoint = findAccumulationPoint(objectId, tree, rowmap.get(objectId));
            long suspectObjectRetained = suspectObject.getRetainedHeapSize();
            Object row = rowmap.get(objectId);
            long deltaRetained = readCol(tree, row, retainedDiffCol);
            suspectObjectRetained = deltaRetained;
            FindLeaksQuery.SuspectRecord r = new FindLeaksQuery.SuspectRecord(suspectObject, suspectObjectRetained, accPoint);

            allSuspects[j++] = r;
        }

        for (ClassHistogramRecord record : suspiciousClasses)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            FindLeaksQuery.SuspectRecord r = buildSuspectRecordGroupOfObjects(record, /*
             * (long)
             * (
             * threshold
             * 0.7),
             */tree, rowmap, listener);
            allSuspects[j++] = r;
        }

        return new SuspectsResultTable(allSuspects, totalHeap);
    }

    private int[] getRandomIds(int[] objectIds)
    {
        if (objectIds.length <= max_paths)
            return objectIds;

        MATPlugin.log(new Status(Status.INFO, MATPlugin.PLUGIN_ID, MessageUtil.format(
                        Messages.FindLeaksQuery_TooManySuspects,
                        objectIds.length, max_paths)));

        Random random = new Random();
        int length = objectIds.length;
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

    public static class AccumulationPoint extends FindLeaksQuery.AccumulationPoint
    {
        long retainedSize;
        int path[];

        public AccumulationPoint(IObject object, long retainedSize, int path[])
        {
            super(object);
            this.retainedSize = retainedSize;
            this.path = path;
        }

        public long getRetainedHeapSize()
        {
            return this.retainedSize;
        }

        public int[] getPath()
        {
            return path;
        }
    }

    public static class SuspectsResultTable extends FindLeaksQuery.SuspectsResultTable
    {

        public SuspectsResultTable(SuspectRecord[] data, long totalHeap)
        {
            super(data, totalHeap);
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.FindLeaksQuery_ColumnLeakSuspect), //
                            new Column(Messages.FindLeaksQuery_Column_NumObjects, Long.class), //
                            new Column(Messages.FindLeaksQuery2_Column_SuspectRetainedHeap, Bytes.class), //
                            new Column(Messages.FindLeaksQuery_Column_SuspectPercent, Double.class).formatting(NumberFormat.getPercentInstance()), //
                            new Column(Messages.FindLeaksQuery_Column_AccumulationPoint), //
                            new Column(Messages.FindLeaksQuery2_Column_AccPointRetainedHeap, Bytes.class), //
                            new Column(Messages.FindLeaksQuery_Column_AccPointPercent, Double.class).formatting(NumberFormat.getPercentInstance()) };
        }

    }

}
