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

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.ArrayUtils;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.HistogramRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleStringTokenizer;

import com.ibm.icu.text.DecimalFormat;

@CommandName("top_consumers_html")
public class TopConsumers2Query implements IQuery
{
    private static final Column COL_RETAINED_HEAP = new Column(Messages.TopConsumers2Query_Column_RetainedHeapPercent,
                    double.class).formatting(new DecimalFormat("0.00%")).noTotals(); //$NON-NLS-1$

    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.HEAP_OBJECT, isMandatory = false, flag = "none")
    public int[] objects;

    @Argument(isMandatory = false, flag = "t")
    public int thresholdPercent = 1;

    private long totalHeap;
    private int[] topDominators;
    private long[] topDominatorRetainedHeap;
    private long threshold;

    public IResult execute(IProgressListener listener) throws Exception
    {
        if (objects != null && objects.length == 0)
            return new TextResult(Messages.TopConsumers2Query_MsgNoObjects);

        SectionSpec spec = new SectionSpec(Messages.TopConsumers2Query_TopConsumers);

        setup(listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        addBiggestObjects(spec);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = getDominatedHistogramWithRetainedSizes(listener);

        addTopLevelDominatorClasses(spec, histogram, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        addTopLevelDominatorClassloader(spec, histogram, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        addPackageTree(spec, listener);

        return spec;
    }

    private void setup(IProgressListener listener) throws SnapshotException
    {
        // nothing specified -> use the top-level dominators
        if (objects == null)
        {
            topDominators = snapshot.getImmediateDominatedIds(-1);
            totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();

            topDominatorRetainedHeap = new long[topDominators.length];
            for (int ii = 0; ii < topDominators.length; ii++)
                topDominatorRetainedHeap[ii] = snapshot.getRetainedHeapSize(topDominators[ii]);
        }
        else
        {
            topDominators = snapshot.getTopAncestorsInDominatorTree(objects, listener);
            topDominatorRetainedHeap = new long[topDominators.length];

            totalHeap = 0;
            for (int ii = 0; ii < topDominators.length; ii++)
            {
                topDominatorRetainedHeap[ii] = snapshot.getRetainedHeapSize(topDominators[ii]);
                totalHeap += topDominatorRetainedHeap[ii];
            }
        }

        threshold = thresholdPercent * totalHeap / 100;
    }

    /** find biggest single objects */
    private void addBiggestObjects(SectionSpec composite) throws SnapshotException
    {
        if (objects == null)
        {
            ArrayInt suspects = new ArrayInt();
            PieFactory pie = new PieFactory(snapshot, totalHeap);

            for (int ii = 0; ii < topDominators.length; ii++)
            {
                if (topDominatorRetainedHeap[ii] > threshold)
                {
                    suspects.add(topDominators[ii]);
                    pie.addSlice(topDominators[ii]);
                }
                else
                {
                    break; // we know the roots are sorted!
                }
            }

            if (suspects.isEmpty())
            {
                String msg = MessageUtil.format(Messages.TopConsumers2Query_NoObjectsBiggerThan, thresholdPercent);
                composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestObjects, new TextResult(msg, true)));
            }
            else
            {
                composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestObjectsOverview, pie.build()));
                QuerySpec spec = new QuerySpec(Messages.TopConsumers2Query_BiggestObjects,
                                new ObjectListResult.Outbound(snapshot, suspects.toArray()));
                spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
                composite.add(spec);
            }
        }
        else
        {
            ArrayInt suspects = new ArrayInt();
            ArrayLong sizes = new ArrayLong();
            for (int ii = 0; ii < topDominators.length; ii++)
            {
                long size = topDominatorRetainedHeap[ii];
                if (size > threshold)
                {
                    suspects.add(topDominators[ii]);
                    sizes.add(size);
                }
            }

            if (suspects.isEmpty())
            {
                String msg = MessageUtil.format(Messages.TopConsumers2Query_NoObjectsBiggerThan, thresholdPercent);
                composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestObjects, new TextResult(msg, true)));
            }
            else
            {
                int[] ids = suspects.toArray();
                long[] s = sizes.toArray();
                ArrayUtils.sortDesc(s, ids);

                PieFactory pie = new PieFactory(snapshot, totalHeap);
                for (int ii = 0; ii < ids.length; ii++)
                    pie.addSlice(ids[ii]);

                composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestObjectsOverview, pie.build()));
                QuerySpec spec = new QuerySpec(Messages.TopConsumers2Query_BiggestObjects,
                                new ObjectListResult.Outbound(snapshot, ids));
                spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
                composite.add(spec);
            }
        }

    }

    /** find suspect classes */
    private void addTopLevelDominatorClasses(SectionSpec composite, Histogram histogram, IProgressListener listener)
    {
        ClassHistogramRecord[] records = histogram.getClassHistogramRecords().toArray(new ClassHistogramRecord[0]);
        Arrays.sort(records, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        PieFactory pie = new PieFactory(snapshot, totalHeap);
        ArrayList<ClassHistogramRecord> suspects = new ArrayList<ClassHistogramRecord>();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        for (ClassHistogramRecord record : records)
        {
            if (record.getRetainedHeapSize() <= threshold)
                break;

            suspects.add(record);
            pie.addSlice(record.getClassId(), record.getLabel(), //
                            record.getUsedHeapSize(), record.getRetainedHeapSize());
        }

        if (suspects.isEmpty())
        {
            String msg = MessageUtil.format(Messages.TopConsumers2Query_NoClassesBiggerThan, thresholdPercent);
            composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestClasses, new TextResult(msg, true)));
        }
        else
        {

            ListResult result = new ListResult(ClassHistogramRecord.class, suspects, //
                            "label", "numberOfObjects", "usedHeapSize", "retainedHeapSize") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            {
                @Override
                public URL getIcon(Object row)
                {
                    return Icons.forObject(snapshot, ((ClassHistogramRecord) row).getClassId());
                }

                @Override
                public IContextObject getContext(final Object row)
                {
                    return new IContextObject()
                    {

                        public int getObjectId()
                        {
                            return ((ClassHistogramRecord) row).getClassId();
                        }

                    };
                }
            };

            result.addColumn(COL_RETAINED_HEAP, new PercentageValueProvider(totalHeap));

            composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestClassesOverview, pie.build()));

            QuerySpec spec = new QuerySpec(Messages.TopConsumers2Query_BiggestClasses, result);
            spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.add(spec);
        }
    }

    /** find suspect class loaders */
    private void addTopLevelDominatorClassloader(SectionSpec composite, Histogram histogram, IProgressListener listener)
    {
        ClassLoaderHistogramRecord[] records = histogram.getClassLoaderHistogramRecords().toArray(
                        new ClassLoaderHistogramRecord[0]);
        Arrays.sort(records, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        PieFactory pie = new PieFactory(snapshot, totalHeap);
        ArrayList<ClassLoaderHistogramRecord> suspects = new ArrayList<ClassLoaderHistogramRecord>();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        for (ClassLoaderHistogramRecord record : records)
        {
            if (record.getRetainedHeapSize() <= threshold)
                break;
            suspects.add(record);
            pie.addSlice(record.getClassLoaderId(), record.getLabel(), //
                            record.getUsedHeapSize(), record.getRetainedHeapSize());
        }

        if (suspects.isEmpty())
        {
            String msg = MessageUtil.format(Messages.TopConsumers2Query_NoClassLoaderBiggerThan, thresholdPercent);
            composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestClassLoaders, new TextResult(msg, true)));
        }
        else
        {
            ListResult result = new ListResult(ClassLoaderHistogramRecord.class, suspects, //
                            "label", "numberOfObjects", "usedHeapSize", "retainedHeapSize") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            {
                @Override
                public URL getIcon(Object row)
                {
                    return Icons.forObject(snapshot, ((ClassLoaderHistogramRecord) row).getClassLoaderId());
                }

                @Override
                public IContextObject getContext(final Object row)
                {
                    return new IContextObject()
                    {

                        public int getObjectId()
                        {
                            return ((ClassLoaderHistogramRecord) row).getClassLoaderId();
                        }

                    };
                }
            };
            result.addColumn(COL_RETAINED_HEAP, new PercentageValueProvider(totalHeap));

            composite.add(new QuerySpec(Messages.TopConsumers2Query_BiggestClassLoadersOverview, pie.build()));

            QuerySpec spec = new QuerySpec(Messages.TopConsumers2Query_BiggestClassLoaders, result);
            spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.add(spec);
        }
    }

    /** top dominators as package tree */
    private void addPackageTree(SectionSpec spec, IProgressListener listener) throws SnapshotException
    {
        PackageTreeNode root = groupByPackage(listener);
        root.retainedSize = totalHeap;
        root.dominatorsCount = topDominators.length;

        pruneTree(root);

        spec.add(new QuerySpec(Messages.TopConsumers2Query_BiggestPackages, new PackageTreeResult(root, totalHeap)));
    }

    // //////////////////////////////////////////////////////////////
    // calculate histogram
    // //////////////////////////////////////////////////////////////

    private Histogram getDominatedHistogramWithRetainedSizes(IProgressListener listener) throws SnapshotException
    {
        listener.beginTask(Messages.TopConsumers2Query_CreatingHistogram, topDominators.length / 1000);

        // calculate histogram ourselves -> keep id:retained size relation
        HashMapIntObject<ClassHistogramRecord> id2class = new HashMapIntObject<ClassHistogramRecord>();
        HashMapIntObject<ClassLoaderHistogramRecord> id2loader = new HashMapIntObject<ClassLoaderHistogramRecord>();
        long totalShallow = 0;

        for (int ii = 0; ii < topDominators.length; ii++)
        {
            int usedHeap = snapshot.getHeapSize(topDominators[ii]);
            totalShallow += usedHeap;

            IClass clazz = snapshot.getClassOf(topDominators[ii]);

            ClassHistogramRecord classRecord = id2class.get(clazz.getObjectId());
            if (classRecord == null)
            {
                classRecord = new ClassHistogramRecord(clazz.getName(), clazz.getObjectId(), 0, 0, 0);
                id2class.put(clazz.getObjectId(), classRecord);
            }
            classRecord.incNumberOfObjects();
            classRecord.incUsedHeapSize(usedHeap);
            classRecord.incRetainedHeapSize(topDominatorRetainedHeap[ii]);

            ClassLoaderHistogramRecord loaderRecord = id2loader.get(clazz.getClassLoaderId());
            if (loaderRecord == null)
            {
                IObject loader = snapshot.getObject(clazz.getClassLoaderId());
                String name = loader.getClassSpecificName();
                if (name == null)
                    name = loader.getTechnicalName();
                loaderRecord = new ClassLoaderHistogramRecord(name, loader.getObjectId(), null, 0, 0, 0);
                id2loader.put(clazz.getClassLoaderId(), loaderRecord);
            }
            loaderRecord.incNumberOfObjects();
            loaderRecord.incUsedHeapSize(usedHeap);
            loaderRecord.incRetainedHeapSize(topDominatorRetainedHeap[ii]);

            if (ii % 1000 == 0)
            {
                listener.worked(1);
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

        ArrayList<ClassHistogramRecord> classList = new ArrayList<ClassHistogramRecord>(id2class.size());
        for (Iterator<?> ee = id2class.values(); ee.hasNext();)
            classList.add((ClassHistogramRecord) ee.next());

        ArrayList<ClassLoaderHistogramRecord> loaderList = new ArrayList<ClassLoaderHistogramRecord>(id2loader.size());
        for (Iterator<?> ee = id2loader.values(); ee.hasNext();)
            loaderList.add((ClassLoaderHistogramRecord) ee.next());

        listener.done();

        return new Histogram(null, classList, loaderList, topDominators.length, totalShallow, totalHeap);
    }

    private static class PackageTreeNode implements Comparable<PackageTreeNode>
    {
        private String packageName;
        private Map<String, PackageTreeNode> subpackages = new HashMap<String, PackageTreeNode>();
        private int dominatorsCount;
        private long retainedSize;

        public PackageTreeNode(String packageName)
        {
            this.packageName = packageName;
        }

        public int compareTo(PackageTreeNode o)
        {
            if (retainedSize < o.retainedSize)
                return 1;
            if (retainedSize > o.retainedSize)
                return -1;
            return 0;
        }
    }

    private PackageTreeNode groupByPackage(IProgressListener listener) throws SnapshotException
    {
        PackageTreeNode root = new PackageTreeNode(Messages.TopConsumers2Query_Label_all);
        PackageTreeNode current;

        listener.beginTask(Messages.TopConsumers2Query_GroupingByPackage, topDominators.length / 1000);

        for (int ii = 0; ii < topDominators.length; ii++)
        {
            int dominatorId = topDominators[ii];
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            long retainedSize = topDominatorRetainedHeap[ii];
            current = root;

            // for classes take their name instead of java.lang.Class
            String className;
            if (snapshot.isClass(dominatorId))
                className = ((IClass) snapshot.getObject(dominatorId)).getName();
            else
                className = snapshot.getClassOf(dominatorId).getName();

            for (String subpack : new SimpleStringTokenizer(className, '.'))
            {
                PackageTreeNode childNode = current.subpackages.get(subpack);
                if (childNode == null)
                {
                    childNode = new PackageTreeNode(subpack);
                    current.subpackages.put(subpack, childNode);
                }
                childNode.retainedSize += retainedSize;
                childNode.dominatorsCount++;

                current = childNode;
            }

            if (ii % 1000 == 0)
            {
                listener.worked(1);
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

        listener.done();

        return root;
    }

    private void pruneTree(PackageTreeNode node)
    {
        for (Iterator<PackageTreeNode> iter = node.subpackages.values().iterator(); iter.hasNext();)
        {
            PackageTreeNode current = iter.next();

            if (current.retainedSize < threshold)
                iter.remove();
            else
                pruneTree(current);
        }
    }

    private static class PercentageValueProvider implements ListResult.ValueProvider
    {
        double base;

        private PercentageValueProvider(long base)
        {
            this.base = base;
        }

        public Object getValueFor(Object row)
        {
            HistogramRecord record = (HistogramRecord) row;
            return record.getRetainedHeapSize() / base;
        }
    }

    private static class PackageTreeResult implements IResultTree, IIconProvider, ISelectionProvider
    {
        PackageTreeNode root;
        double base;

        private PackageTreeResult(PackageTreeNode root, long base)
        {
            this.root = root;
            this.base = base;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.TopConsumers2Query_Column_Package), //
                            new Column(Messages.Column_RetainedHeap, long.class).sorting(Column.SortDirection.DESC), //
                            COL_RETAINED_HEAP, //
                            new Column(Messages.TopConsumers2Query_Column_TopDominators, int.class) };
        }

        public List<?> getElements()
        {
            List<Object> elements = new ArrayList<Object>(1);
            elements.add(root);
            return elements;
        }

        public boolean hasChildren(Object element)
        {
            return !((PackageTreeNode) element).subpackages.isEmpty();
        }

        public List<?> getChildren(Object parent)
        {
            return new ArrayList<PackageTreeNode>(((PackageTreeNode) parent).subpackages.values());
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            PackageTreeNode node = (PackageTreeNode) row;
            switch (columnIndex)
            {
                case 0:
                    return node.packageName;
                case 1:
                    return node.retainedSize;
                case 2:
                    return node.retainedSize / base;
                case 3:
                    return node.dominatorsCount;
            }
            return null;
        }

        public IContextObject getContext(Object row)
        {
            return null;
        }

        public URL getIcon(Object row)
        {
            PackageTreeNode node = (PackageTreeNode) row;
            return node.subpackages.isEmpty() ? Icons.CLASS : Icons.PACKAGE;
        }

        public boolean isExpanded(Object row)
        {
            return true;
        }

        public boolean isSelected(Object row)
        {
            return false;
        }
    }
}
