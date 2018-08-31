/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - add icon
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.BytesFormat;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectComparators;
import org.eclipse.mat.util.IProgressListener;

import com.ibm.icu.text.NumberFormat;

@CommandName("top_consumers")
@Icon("/META-INF/icons/pie_chart.gif")
@Category(Category.HIDDEN)
public class TopConsumersQuery implements IQuery
{
    static NumberFormat percentFormatter = NumberFormat.getIntegerInstance();
    static NumberFormat numberFormatter = NumberFormat.getNumberInstance();
    static BytesFormat bytesFormatter = BytesFormat.getInstance();
    static
    {
        percentFormatter.setMaximumFractionDigits(2);
        percentFormatter.setMinimumFractionDigits(2);
    }

    static final String SEPARATOR = "--------------------------------------------------------------------------------"; //$NON-NLS-1$

    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.HEAP_OBJECT, isMandatory = false, flag = Argument.UNFLAGGED)
    public int[] objects;

    @Argument(isMandatory = false, flag = "t")
    public int thresholdPercent = 1;

    public IResult execute(IProgressListener listener) throws Exception
    {
        CharArrayWriter outWriter = new CharArrayWriter(1000);
        PrintWriter out = new PrintWriter(outWriter);

        long totalHeap;
        int[] topDominators;

        // nothing specified ->
        // use the top-level dominators
        if (objects == null)
        {
            totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();
            topDominators = snapshot.getImmediateDominatedIds(-1);
        }
        else if (objects.length == 0)
        {
            return new TextResult(Messages.TopConsumers2Query_MsgNoObjects);
        }
        else
        {
            topDominators = snapshot.getTopAncestorsInDominatorTree(objects, listener);
            totalHeap = 0;
            for (int topDominator : topDominators)
            {
                totalHeap += snapshot.getRetainedHeapSize(topDominator);
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        long threshold = thresholdPercent * totalHeap / 100;

        // find the biggest objects first
        ArrayInt suspects = new ArrayInt();
        if (objects == null || objects.length == 0) // nothing specified
        {
            int i = 0;
            while (i < topDominators.length && snapshot.getRetainedHeapSize(topDominators[i]) > threshold)
            {
                suspects.add(topDominators[i]);
                i++;
            }
        }
        else
        {
            for (int i = 0; i < topDominators.length; i++)
            {
                if (snapshot.getRetainedHeapSize(topDominators[i]) > threshold)
                    suspects.add(topDominators[i]);
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = groupByClasses(topDominators, listener);

        // find suspect classes
        ClassHistogramRecord[] classRecords = histogram.getClassHistogramRecords().toArray(new ClassHistogramRecord[0]);
        Arrays.sort(classRecords, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        int k = 0;
        ArrayList<ClassHistogramRecord> suspectRecords = new ArrayList<ClassHistogramRecord>();
        while (k < classRecords.length && classRecords[k].getRetainedHeapSize() > threshold)
        {
            suspectRecords.add(classRecords[k]);
            k++;
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        // find suspect class loaders
        ClassLoaderHistogramRecord[] classloaderRecords = histogram.getClassLoaderHistogramRecords().toArray(
                        new ClassLoaderHistogramRecord[0]);
        Arrays.sort(classloaderRecords, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        k = 0;
        ArrayList<ClassLoaderHistogramRecord> classloaderSuspectRecords = new ArrayList<ClassLoaderHistogramRecord>();
        while (k < classloaderRecords.length && classloaderRecords[k].getRetainedHeapSize() > threshold)
        {
            classloaderSuspectRecords.add(classloaderRecords[k]);
            k++;
        }

        // group by packages
        PackageTreeNode root = groupByPackage(topDominators, snapshot, listener);
        root.retainedSize = totalHeap;
        root.dominators = new ArrayInt(topDominators);

        // Print the results
        out.println();
        out.println(Messages.TopConsumers2Query_BiggestObjects + ":"); //$NON-NLS-1$
        out.println(SEPARATOR);
        int[] suspectsArr = suspects.toArray();
        IObject[] objectArr = new IObject[suspectsArr.length];
        for (int j = 0; j < suspectsArr.length; j++)
        {
            objectArr[j] = snapshot.getObject(suspectsArr[j]);
        }
        Arrays.sort(objectArr, ObjectComparators.getComparatorForRetainedHeapSizeDescending());

        for (IObject obj : objectArr)
        {
            long retained = snapshot.getRetainedHeapSize(obj.getObjectId());
            out.print(percentFormatter.format((double) (retained * 100) / (double) totalHeap));
            out.print("%  "); //$NON-NLS-1$
            out.print(bytesFormatter.format(retained));
            out.print("  "); //$NON-NLS-1$
            out.print(obj.getTechnicalName());
            String details = obj.getClassSpecificName();
            if (details != null)
            {
                out.print("  "); //$NON-NLS-1$
                out.print(details);
            }
            out.println();
        }

        out.println();
        out.println(Messages.TopConsumers2Query_BiggestClasses + ":"); //$NON-NLS-1$
        out.println(SEPARATOR);
        for (ClassHistogramRecord rec : suspectRecords)
        {
            long retained = rec.getRetainedHeapSize();
            out.print(percentFormatter.format((double) (retained * 100) / (double) totalHeap));
            out.print("%  "); //$NON-NLS-1$
            out.print(bytesFormatter.format(retained));
            out.print("  "); //$NON-NLS-1$
            out.print(numberFormatter.format(rec.getNumberOfObjects()));
            out.print("  "); //$NON-NLS-1$
            out.print(rec.getLabel());
            out.println();
        }

        out.println();
        out.println(Messages.TopConsumers2Query_BiggestClassLoaders + ":"); //$NON-NLS-1$
        out.println(SEPARATOR);
        for (ClassLoaderHistogramRecord rec : classloaderSuspectRecords)
        {
            long retained = rec.getRetainedHeapSize();
            out.print(percentFormatter.format((double) (retained * 100) / (double) totalHeap));
            out.print("%  "); //$NON-NLS-1$
            out.print(bytesFormatter.format(retained));
            out.print("  "); //$NON-NLS-1$
            out.print(numberFormatter.format(rec.getNumberOfObjects()));
            out.print("  "); //$NON-NLS-1$
            out.print(rec.getLabel());
            out.println();
        }

        out.println();
        out.println(Messages.TopConsumers2Query_BiggestPackages + ":"); //$NON-NLS-1$
        out.println(SEPARATOR);
        out.println(Messages.TopConsumersQuery_ColumnLabels);
        out.println(SEPARATOR);
        printPackageTree(root, new StringBuilder(), totalHeap, threshold, out, snapshot);

        out.close();
        return new TextResult(outWriter.toString());
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
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            record.setRetainedHeapSize(sumRetainedSize(record.getObjectIds(), snapshot));
            arr[i++] = record;
        }

        Collection<ClassLoaderHistogramRecord> loaderRecords = histogram.getClassLoaderHistogramRecords();
        ClassLoaderHistogramRecord[] loaderArr = new ClassLoaderHistogramRecord[loaderRecords.size()];
        i = 0;

        for (ClassLoaderHistogramRecord record : loaderRecords)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

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

    class PackageTreeNode implements Comparable<PackageTreeNode>
    {
        public PackageTreeNode(String packageName)
        {
            this.packageName = packageName;
        }

        String packageName;
        ArrayInt dominators = new ArrayInt();
        Map<String, PackageTreeNode> subpackages = new HashMap<String, PackageTreeNode>();
        long retainedSize;

        public int compareTo(PackageTreeNode o)
        {
            if (retainedSize < o.retainedSize)
                return 1;
            if (retainedSize > o.retainedSize)
                return -1;
            return 0;
        }
    }

    private PackageTreeNode groupByPackage(int[] dominators, ISnapshot snapshot, IProgressListener listener)
                    throws SnapshotException
    {
        PackageTreeNode root = new PackageTreeNode(Messages.TopConsumers2Query_Label_all);
        PackageTreeNode current;

        listener.beginTask(Messages.TopConsumers2Query_GroupingByPackage, dominators.length / 1000);
        int counter = 0;

        for (int dominatorId : dominators)
        {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            long retainedSize = snapshot.getRetainedHeapSize(dominatorId);
            current = root;

            // for classes take their name instead of java.lang.Class
            IClass objClass = snapshot.getClassOf(dominatorId);
            if (IClass.JAVA_LANG_CLASS.equals(objClass.getName()))
            {
                IObject dominatorObj = snapshot.getObject(dominatorId);
                if (dominatorObj instanceof IClass)
                    objClass = (IClass) dominatorObj;
            }
            String className = objClass.getName();
            StringTokenizer tokenizer = new StringTokenizer(className, "."); //$NON-NLS-1$

            while (tokenizer.hasMoreTokens())
            {
                String subpack = tokenizer.nextToken();
                PackageTreeNode childNode = current.subpackages.get(subpack);
                if (childNode == null)
                {
                    childNode = new PackageTreeNode(subpack);
                    current.subpackages.put(subpack, childNode);
                }
                childNode.dominators.add(dominatorId);
                childNode.retainedSize += retainedSize;

                current = childNode;
            }

            if (++counter % 1000 == 0)
            {
                listener.worked(1);
            }
        }

        return root;
    }

    private void printPackageTree(PackageTreeNode node, StringBuilder level, long totalHeap, long threshold,
                    PrintWriter out, ISnapshot snapshot) throws SnapshotException
    {
        // long totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();

        StringBuilder output = new StringBuilder(level.length() + 100);

        /* print the leading lines */
        output.append(level);

        /* print the object technical name */
        output.append(node.packageName);
        output.append("  ("); //$NON-NLS-1$
        double percent = (double) (node.retainedSize * 100) / (double) totalHeap;
        output.append(percentFormatter.format(percent));
        output.append("%)  "); //$NON-NLS-1$
        output.append(bytesFormatter.format(node.retainedSize));
        output.append("  "); //$NON-NLS-1$
        output.append(numberFormatter.format(node.dominators.size()));

        out.println(output);

        PackageTreeNode[] children = node.subpackages.values().toArray(new PackageTreeNode[0]);

        if (children != null)
        {
            Arrays.sort(children);
            for (int i = 0; i < children.length; i++)
            {
                if (children[i].retainedSize < threshold)
                {
                    break;
                }
                int k = level.indexOf("'-"); //$NON-NLS-1$
                if (k != -1)
                {
                    level.replace(k, k + 2, "  "); //$NON-NLS-1$
                }
                else
                {
                    k = level.indexOf("|-"); //$NON-NLS-1$
                    if (k != -1)
                    {
                        level.replace(k + 1, k + 2, " "); //$NON-NLS-1$
                    }
                }

                if ((i == children.length - 1) || (children[i + 1].retainedSize < threshold))
                {
                    level.append('\'');
                }
                else
                {
                    level.append('|');
                }
                level.append("- "); //$NON-NLS-1$

                printPackageTree(children[i], level, totalHeap, threshold, out, snapshot);

            }
        }
        if (level.length() >= 3)
        {
            level.delete(level.length() - 3, level.length());

        }
    }
}
