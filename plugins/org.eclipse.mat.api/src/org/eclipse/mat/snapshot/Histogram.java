/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.BytesFormat;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleStringTokenizer;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberFormat;

/**
 * Class histogram - heap objects aggregated by their class. It holds the number
 * and consumed memory of the objects aggregated per class and aggregated per
 * class loader.
 */
public class Histogram extends HistogramRecord implements IResultTable, IIconProvider
{
    private static final long serialVersionUID = 3L;

    private boolean isDefaultHistogram = false;
    private boolean showPlusMinus = false;
    private ArrayList<ClassHistogramRecord> classHistogramRecords;
    private ArrayList<ClassLoaderHistogramRecord> classLoaderHistogramRecords;

    /* package */Histogram()
    {
    // needed for serialization
    }

    /**
     * Construct a histogram
     * @param label the name of the whole histogram
     * @param classHistogramRecords the classes
     * @param classLoaderHistogramRecords the class loaders
     * @param numberOfObjects the total number of objects
     * @param usedHeapSize the shallow size of all the objects
     * @param retainedHeapSize the retained size of all the objects, or 0 if unknown
     */
    public Histogram(String label, ArrayList<ClassHistogramRecord> classHistogramRecords,
                    ArrayList<ClassLoaderHistogramRecord> classLoaderHistogramRecords, long numberOfObjects,
                    long usedHeapSize, long retainedHeapSize)
    {
        this(label, classHistogramRecords, classLoaderHistogramRecords, numberOfObjects, usedHeapSize,
                        retainedHeapSize, false);
    }

    /**
     * Construct a histogram
     * @param label the name of the whole histogram
     * @param classHistogramRecords the classes
     * @param classLoaderHistogramRecords the class loaders
     * @param numberOfObjects the total number of objects
     * @param usedHeapSize the shallow size of all the objects
     * @param retainedHeapSize the retained size of all the objects, or 0 if unknown
     * @param isDefaultHistogram a histogram of the whole snapshot
     */
    public Histogram(String label, ArrayList<ClassHistogramRecord> classHistogramRecords,
                    ArrayList<ClassLoaderHistogramRecord> classLoaderHistogramRecords, long numberOfObjects,
                    long usedHeapSize, long retainedHeapSize, boolean isDefaultHistogram)
    {
        super(label, numberOfObjects, usedHeapSize, retainedHeapSize);
        this.classHistogramRecords = classHistogramRecords;
        this.classLoaderHistogramRecords = classLoaderHistogramRecords;
        this.isDefaultHistogram = isDefaultHistogram;
    }

    /**
     * Get collection of all the classes for all the objects which were found in
     * the set of objects on which the class histogram was computed.
     * 
     * @return collection of all the classes for all the objects which were
     *         found in the set of objects on which the class histogram was
     *         computed
     */
    public Collection<ClassHistogramRecord> getClassHistogramRecords()
    {
        return classHistogramRecords;
    }

    /**
     * Get collection of all the class loaders for all the classes for all the
     * objects which were found in the set of objects on which the class
     * histogram was computed.
     * 
     * @return collection of all the class loaders for all the classes for all
     *         the objects which were found in the set of objects on which the
     *         class histogram was computed
     */
    public Collection<ClassLoaderHistogramRecord> getClassLoaderHistogramRecords()
    {
        return classLoaderHistogramRecords;
    }

    /**
     * Compute a new histogram as difference of this histogram compared to
     * (minus) the given baseline histogram.
     * <p>
     * This method can be used to check what has changed from one histogram to
     * another, to compute a delta.
     * 
     * @param baseline
     *            baseline histogram
     * @return difference histogram between this histogram compared to (minus)
     *         the given baseline histogram
     */
    public Histogram diffWithBaseline(Histogram baseline)
    {
        int classIdBase = -1000000000;
        int classLoaderIdBase = -2000000000;
        int classIdCurrent = classIdBase;
        int classLoaderIdCurrent = classLoaderIdBase;
        Map<String, Map<String, ClassHistogramRecord>> classLoaderDifferences = new HashMap<String, Map<String, ClassHistogramRecord>>();

        for (ClassLoaderHistogramRecord classLoaderHistogramRecord : classLoaderHistogramRecords)
        {
            String classLoaderName = classLoaderHistogramRecord.getLabel();
            Map<String, ClassHistogramRecord> classDifferences = classLoaderDifferences.get(classLoaderName);
            if (classDifferences == null)
            {
                classLoaderDifferences.put(classLoaderName,
                                classDifferences = new HashMap<String, ClassHistogramRecord>());
            }
            for (ClassHistogramRecord classHistogramRecord : classLoaderHistogramRecord.getClassHistogramRecords())
            {
                String className = classHistogramRecord.getLabel();
                ClassHistogramRecord classDifference = classDifferences.get(className);
                if (classDifference == null)
                {
                    classDifferences.put(className, classDifference = new ClassHistogramRecord(className,
                                    --classIdCurrent, 0, 0, 0));
                }
                classDifference.incNumberOfObjects(classHistogramRecord.getNumberOfObjects());
                classDifference.incUsedHeapSize(classHistogramRecord.getUsedHeapSize());
            }
        }
        for (ClassLoaderHistogramRecord classLoaderHistogramRecord : baseline.classLoaderHistogramRecords)
        {
            String classLoaderName = classLoaderHistogramRecord.getLabel();
            Map<String, ClassHistogramRecord> classDifferences = classLoaderDifferences.get(classLoaderName);
            if (classDifferences == null)
            {
                classLoaderDifferences.put(classLoaderName,
                                classDifferences = new HashMap<String, ClassHistogramRecord>());
            }
            for (ClassHistogramRecord classHistogramRecord : classLoaderHistogramRecord.getClassHistogramRecords())
            {
                String className = classHistogramRecord.getLabel();
                ClassHistogramRecord classDifference = classDifferences.get(className);
                if (classDifference == null)
                {
                    classDifferences.put(className, classDifference = new ClassHistogramRecord(className,
                                    --classIdCurrent, 0, 0, 0));
                }
                classDifference.incNumberOfObjects(-classHistogramRecord.getNumberOfObjects());
                classDifference.incUsedHeapSize(-classHistogramRecord.getUsedHeapSize());
            }
        }
        Map<String, ClassHistogramRecord> classDiffRecordsMerged = new HashMap<String, ClassHistogramRecord>();
        ArrayList<ClassLoaderHistogramRecord> classLoaderDiffRecordsMerged = new ArrayList<ClassLoaderHistogramRecord>();
        for (Map.Entry<String, Map<String, ClassHistogramRecord>> classDifferences : classLoaderDifferences.entrySet())
        {
            ArrayList<ClassHistogramRecord> records = new ArrayList<ClassHistogramRecord>(classDifferences.getValue()
                            .values().size());
            int numberOfObjects = 0;
            long usedHeapSize = 0;
            for (ClassHistogramRecord classDifference : classDifferences.getValue().values())
            {
                ClassHistogramRecord classDifferenceMerged = classDiffRecordsMerged.get(classDifference.getLabel());
                if (classDifferenceMerged == null)
                {
                    classDiffRecordsMerged.put(classDifference.getLabel(),
                                    classDifferenceMerged = new ClassHistogramRecord(classDifference.getLabel(),
                                                    --classIdCurrent, 0, 0, 0));
                }
                classDifferenceMerged.incNumberOfObjects(classDifference.getNumberOfObjects());
                classDifferenceMerged.incUsedHeapSize(classDifference.getUsedHeapSize());
                records.add(classDifference);
                numberOfObjects += classDifference.getNumberOfObjects();
                usedHeapSize += classDifference.getUsedHeapSize();
            }
            classLoaderDiffRecordsMerged.add(new ClassLoaderHistogramRecord(classDifferences.getKey(),
                            --classLoaderIdCurrent, records, numberOfObjects, usedHeapSize, 0));
        }
        Histogram histogram = new Histogram(
                        MessageUtil.format(Messages.Histogram_Difference, getLabel(), baseline
                                        .getLabel()), //
                        new ArrayList<ClassHistogramRecord>(classDiffRecordsMerged.values()),
                        classLoaderDiffRecordsMerged, //
                        Math.abs(this.getNumberOfObjects() - baseline.getNumberOfObjects()), //
                        Math.abs(this.getUsedHeapSize() - baseline.getUsedHeapSize()), //
                        Math.abs(this.getRetainedHeapSize() - baseline.getRetainedHeapSize()));
        histogram.showPlusMinus = true;
        return histogram;
    }

    /**
     * Compute a new histogram as intersection of this histogram compared to
     * (equals) the given another histogram.
     * <p>
     * This method can be used to check what remains the same within two
     * histograms, e.g. if you have two histograms it shows what hasn't changed,
     * e.g. if you have two difference histograms it shows what remained the
     * same change (increase or decrease; used in gradient memory leak
     * analysis).
     * <p>
     * Note: Heap space is not taken into account in this analysis, only the
     * number of objects, i.e. when the number of objects is the same, you will
     * see this number of objects, otherwise or if there are no objects of a
     * particular class you won't get a histogram record for it!
     * 
     * @param another
     *            another histogram
     * @return intersection histogram of this histogram compared to (equals) the
     *         given another histogram
     */
    public Histogram intersectWithAnother(Histogram another)
    {
        int classIdBase = -1000000000;
        int classLoaderIdBase = -2000000000;
        int classIdCurrent = classIdBase;
        int classLoaderIdCurrent = classLoaderIdBase;
        Map<String, Map<String, ClassHistogramRecord>> classLoaderDifferences = new HashMap<String, Map<String, ClassHistogramRecord>>();

        final String prefix = "Class$%"; //$NON-NLS-1$

        for (ClassLoaderHistogramRecord classLoaderHistogramRecord : classLoaderHistogramRecords)
        {
            String classLoaderName = classLoaderHistogramRecord.getLabel();
            Map<String, ClassHistogramRecord> classDifferences = classLoaderDifferences.get(classLoaderName);
            if (classDifferences == null)
            {
                classLoaderDifferences.put(classLoaderName,
                                classDifferences = new HashMap<String, ClassHistogramRecord>());
            }
            for (ClassHistogramRecord classHistogramRecord : classLoaderHistogramRecord.getClassHistogramRecords())
            {
                String className = prefix + classHistogramRecord.getLabel();
                ClassHistogramRecord classDifference = classDifferences.get(className);
                if (classDifference == null)
                {
                    classDifferences.put(className, classDifference = new ClassHistogramRecord(className,
                                    --classIdCurrent, 0, 0, 0));
                }
                classDifference.incNumberOfObjects(classHistogramRecord.getNumberOfObjects());
                classDifference.incUsedHeapSize(classHistogramRecord.getUsedHeapSize());
            }
        }
        for (ClassLoaderHistogramRecord classLoaderHistogramRecord : another.classLoaderHistogramRecords)
        {
            String classLoaderName = classLoaderHistogramRecord.getLabel();
            Map<String, ClassHistogramRecord> classDifferences = classLoaderDifferences.get(classLoaderName);
            if (classDifferences == null)
            {
                classLoaderDifferences.put(classLoaderName,
                                classDifferences = new HashMap<String, ClassHistogramRecord>());
            }
            for (ClassHistogramRecord classHistogramRecord : classLoaderHistogramRecord.getClassHistogramRecords())
            {
                String className = classHistogramRecord.getLabel();
                ClassHistogramRecord classDifference = classDifferences.get(prefix + className);
                if ((classDifference != null) && (classDifference.getNumberOfObjects() > 0)
                                && (classDifference.getNumberOfObjects() == classHistogramRecord.getNumberOfObjects()))
                {
                    classDifferences.put(className, new ClassHistogramRecord(className, --classIdCurrent,
                                    classDifference.getNumberOfObjects(), classDifference.getUsedHeapSize()
                                                    + classHistogramRecord.getUsedHeapSize(), 0));
                }
            }
        }
        Map<String, ClassHistogramRecord> classDiffRecordsMerged = new HashMap<String, ClassHistogramRecord>();
        ArrayList<ClassLoaderHistogramRecord> classLoaderDiffRecordsMerged = new ArrayList<ClassLoaderHistogramRecord>();
        int numberOfObjectsOverall = 0;
        long usedHeapSizeOverall = 0;
        for (Map.Entry<String, Map<String, ClassHistogramRecord>> classDifferences : classLoaderDifferences.entrySet())
        {
            ArrayList<ClassHistogramRecord> records = new ArrayList<ClassHistogramRecord>(classDifferences.getValue()
                            .values().size());
            int numberOfObjects = 0;
            long usedHeapSize = 0;
            for (ClassHistogramRecord classDifference : classDifferences.getValue().values())
            {
                if (!classDifference.getLabel().startsWith(prefix))
                {
                    ClassHistogramRecord classDifferenceMerged = classDiffRecordsMerged.get(classDifference.getLabel());
                    if (classDifferenceMerged == null)
                    {
                        classDiffRecordsMerged.put(classDifference.getLabel(),
                                        classDifferenceMerged = new ClassHistogramRecord(classDifference.getLabel(),
                                                        --classIdCurrent, 0, 0, 0));
                    }
                    classDifferenceMerged.incNumberOfObjects(classDifference.getNumberOfObjects());
                    classDifferenceMerged.incUsedHeapSize(classDifference.getUsedHeapSize());
                    records.add(classDifference);
                    numberOfObjects += classDifference.getNumberOfObjects();
                    usedHeapSize += classDifference.getUsedHeapSize();
                    numberOfObjectsOverall += numberOfObjects;
                    usedHeapSizeOverall += usedHeapSize;
                }
            }
            if (records.size() > 0)
            {
                classLoaderDiffRecordsMerged.add(new ClassLoaderHistogramRecord(classDifferences.getKey(),
                                --classLoaderIdCurrent, records, numberOfObjects, usedHeapSize, 0));
            }
        }
        Histogram histogram = new Histogram(MessageUtil.format(Messages.Histogram_Intersection, getLabel(),
                        another.getLabel()), new ArrayList<ClassHistogramRecord>(classDiffRecordsMerged.values()),
                        classLoaderDiffRecordsMerged, numberOfObjectsOverall, usedHeapSizeOverall, 0);
        histogram.showPlusMinus = true;
        return histogram;
    }

    public boolean isDefaultHistogram()
    {
        return isDefaultHistogram;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(1024);
        buf.append(MessageUtil.format(
                        Messages.Histogram_Description, //
                        label, //
                        (classLoaderHistogramRecords != null) ? classLoaderHistogramRecords.size() : 0, //
                        (classHistogramRecords != null) ? classHistogramRecords.size() : 0, //
                        numberOfObjects, //
                        usedHeapSize));

        if (classHistogramRecords != null)
        {
            buf.append("\n\n" + Messages.Histogram_ClassStatistics + ":\n"); //$NON-NLS-1$//$NON-NLS-2$
            buf.append(alignRight(Messages.Column_Objects, 17));
            buf.append(alignRight(Messages.Column_ShallowHeap, 17));
            buf.append(alignRight(Messages.Column_RetainedHeap, 17));
            buf.append("  "); //$NON-NLS-1$
            buf.append(alignLeft(Messages.Column_ClassName, 0));
            buf.append("\n"); //$NON-NLS-1$

            appendRecords(buf, classHistogramRecords);
        }

        if (classLoaderHistogramRecords != null)
        {
            buf.append("\n\n" + Messages.Histogram_ClassLoaderStatistics + ":\n"); //$NON-NLS-1$ //$NON-NLS-2$
            buf.append(alignRight(Messages.Column_Objects, 17));
            buf.append(alignRight(Messages.Column_ShallowHeap, 17));
            buf.append(alignRight(Messages.Column_RetainedHeap, 17));
            buf.append("  "); //$NON-NLS-1$
            buf.append(alignLeft(Messages.Column_ClassName, 0));
            buf.append("\n"); //$NON-NLS-1$

            appendRecords(buf, classLoaderHistogramRecords);
        }

        return buf.toString();
    }

    private static void appendRecords(StringBuilder summary, List<? extends HistogramRecord> records)
    {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        BytesFormat bytesFormatter = BytesFormat.getInstance();
        for (HistogramRecord record : records)
        {
            summary.append(alignRight(formatter.format(record.getNumberOfObjects()), 17));
            summary.append(alignRight(bytesFormatter.format(record.getUsedHeapSize()), 17));
            if (record.getRetainedHeapSize() < 0)
                summary.append(alignRight(">=" + bytesFormatter.format(-record.getRetainedHeapSize()), 17)); //$NON-NLS-1$
            else
                summary.append(alignRight(bytesFormatter.format(record.getRetainedHeapSize()), 17));
            summary.append("  "); //$NON-NLS-1$
            summary.append(alignLeft(record.getLabel(), 0));
            summary.append("\n"); //$NON-NLS-1$
        }
    }

    private static String alignLeft(String text, int length)
    {
        if (text.length() >= length) { return text; }
        StringBuilder buf = new StringBuilder(length);
        int blanks = length - text.length();
        buf.append(text);
        for (int i = 0; i < blanks; i++)
        {
            buf.append(' ');
        }
        return buf.toString();
    }

    private static String alignRight(String text, int length)
    {
        if (text.length() >= length) { return text; }
        StringBuilder buf = new StringBuilder(length);
        int blanks = length - text.length();
        for (int i = 0; i < blanks; i++)
        {
            buf.append(' ');
        }
        buf.append(text);
        return buf.toString();
    }

    /**
     * Generate human readable text based report from a histogram.
     * 
     * @param histogram
     *            histogram you want a human reable text based report for
     * @param comparator
     *            comparator to be used for sorting the histogram records (
     *            {@link HistogramRecord} provides some default comparators)
     * @return human redable text based report for the given histogram
     */
    public static String generateClassHistogramRecordTextReport(Histogram histogram,
                    Comparator<HistogramRecord> comparator)
    {
        return generateHistogramRecordTextReport(new ArrayList<HistogramRecord>(histogram.getClassHistogramRecords()),
                        comparator, new String[] { Messages.Column_ClassName, Messages.Column_Objects, Messages.Column_Heap, Messages.Column_RetainedHeap });
    }

    private static String generateHistogramRecordTextReport(List<HistogramRecord> records,
                    Comparator<HistogramRecord> comparator, String[] headers)
    {
        Collections.sort(records, comparator);
        int labelLength = headers[0].length();
        int numberOfObjectsLength = headers[1].length();
        int usedHeapSizeLength = headers[2].length();
        int retainedHeapSizeLength = headers[3].length();
        for (HistogramRecord record : records)
        {
            if (record.getLabel().length() > labelLength)
            {
                labelLength = record.getLabel().length();
            }
            if (Long.toString(record.getNumberOfObjects()).length() > numberOfObjectsLength)
            {
                numberOfObjectsLength = Long.toString(record.getNumberOfObjects()).length();
            }
            if (Long.toString(record.getUsedHeapSize()).length() > usedHeapSizeLength)
            {
                usedHeapSizeLength = Long.toString(record.getUsedHeapSize()).length();
            }
            if (Long.toString(record.getRetainedHeapSize()).length() > retainedHeapSizeLength)
            {
                retainedHeapSizeLength = Long.toString(record.getRetainedHeapSize()).length();
            }
        }
        StringBuilder report = new StringBuilder((4 + records.size())
                        * (2 + labelLength + 3 + numberOfObjectsLength + 3 + usedHeapSizeLength + 3
                                        + retainedHeapSizeLength + 2 + 2));
        appendStringAndFillUp(report, null, '-', 2 + labelLength + 3 + numberOfObjectsLength + 3 + usedHeapSizeLength
                        + 3 + retainedHeapSizeLength + 2);
        report.append("\r\n"); //$NON-NLS-1$
        report.append("| "); //$NON-NLS-1$
        appendStringAndFillUp(report, headers[0], ' ', labelLength);
        report.append(" | "); //$NON-NLS-1$
        appendStringAndFillUp(report, headers[1], ' ', numberOfObjectsLength);
        report.append(" | "); //$NON-NLS-1$
        appendStringAndFillUp(report, headers[2], ' ', usedHeapSizeLength);
        report.append(" | "); //$NON-NLS-1$
        appendStringAndFillUp(report, headers[3], ' ', retainedHeapSizeLength);
        report.append(" |\r\n"); //$NON-NLS-1$
        appendStringAndFillUp(report, null, '-', 2 + labelLength + 3 + numberOfObjectsLength + 3 + usedHeapSizeLength
                        + 3 + retainedHeapSizeLength + 2);
        report.append("\r\n"); //$NON-NLS-1$
        for (HistogramRecord record : records)
        {
            report.append("| "); //$NON-NLS-1$
            appendStringAndFillUp(report, record.getLabel(), ' ', labelLength);
            report.append(" | "); //$NON-NLS-1$
            appendPreFillAndString(report, Long.toString(record.getNumberOfObjects()), ' ', numberOfObjectsLength);
            report.append(" | "); //$NON-NLS-1$
            appendPreFillAndString(report, Long.toString(record.getUsedHeapSize()), ' ', usedHeapSizeLength);
            report.append(" | "); //$NON-NLS-1$
            appendPreFillAndString(report, Long.toString(record.getRetainedHeapSize()), ' ', retainedHeapSizeLength);
            report.append(" |\r\n"); //$NON-NLS-1$
        }
        appendStringAndFillUp(report, null, '-', 2 + labelLength + 3 + numberOfObjectsLength + 3 + usedHeapSizeLength
                        + 3 + retainedHeapSizeLength + 2);
        report.append("\r\n"); //$NON-NLS-1$
        return report.toString();
    }

    /**
     * Generate machine/human readable comma separated report from an histogram.
     * 
     * @param histogram
     *            histogram you want a machine/human readable comma separated
     *            report for
     * @param comparator
     *            comparator to be used for sorting the histogram records (
     *            {@link HistogramRecord} provides some default comparators)
     * @return machine/human readable comma separated report for the given
     *         histogram
     */
    public static String generateClassHistogramRecordCsvReport(Histogram histogram,
                    Comparator<HistogramRecord> comparator)
    {
        return generateHistogramRecordCsvReport(new ArrayList<ClassHistogramRecord>(histogram
                        .getClassHistogramRecords()), comparator, new String[] { Messages.Column_ClassName, Messages.Column_Objects,
                        Messages.Column_ShallowHeap, Messages.Column_RetainedHeap });
    }

    /**
     * Generate machine/human readable comma separated report from an histogram.
     * 
     * @param histogram
     *            histogram you want a machine/human readable comma separated
     *            report for
     * @param comparator
     *            comparator to be used for sorting the histogram records (
     *            {@link HistogramRecord} provides some default comparators)
     * @return machine/human readable comma separated report for the given
     *         histogram
     */
    public static String generateClassLoaderHistogramRecordCsvReport(Histogram histogram,
                    Comparator<HistogramRecord> comparator)
    {
        return generateClassloaderHistogramCsvReport(new ArrayList<ClassLoaderHistogramRecord>(histogram
                        .getClassLoaderHistogramRecords()), comparator, new String[] { Messages.Column_ClassLoaderName,
                        Messages.Column_ClassName, Messages.Column_Objects, Messages.Column_ShallowHeap, Messages.Column_RetainedHeap });
    }

    private static final char SEPARATOR_CHAR = new DecimalFormatSymbols().getDecimalSeparator() == ',' ? ';' : ',';

    private static String generateClassloaderHistogramCsvReport(List<ClassLoaderHistogramRecord> records,
                    Comparator<HistogramRecord> comparator, String[] headers)
    {
        StringBuilder report = new StringBuilder((1 + records.size()) * 256);
        report.append(headers[0]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[1]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[2]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[3]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[4]);
        report.append(SEPARATOR_CHAR + "\r\n"); //$NON-NLS-1$

        Collections.sort(records, comparator);
        
        BytesFormat bytesFormatter = BytesFormat.getInstance();

        for (ClassLoaderHistogramRecord classloaderRecord : records)
        {
            Collection<ClassHistogramRecord> classRecords = classloaderRecord.getClassHistogramRecords();
            List<ClassHistogramRecord> list = new ArrayList<ClassHistogramRecord>(classRecords);
            Collections.sort(list, Histogram.COMPARATOR_FOR_USEDHEAPSIZE);
            for (int i = list.size() - 1; i >= 0; i--)
            {
                ClassHistogramRecord record = list.get(i);
                report.append(classloaderRecord.getLabel());
                report.append(SEPARATOR_CHAR);
                report.append(record.getLabel());
                report.append(SEPARATOR_CHAR);
                report.append(record.getNumberOfObjects());
                report.append(SEPARATOR_CHAR);
                report.append(record.getUsedHeapSize());
                report.append(SEPARATOR_CHAR);
                if (record.getRetainedHeapSize() < 0)
                    report.append(">=" + bytesFormatter.format(-record.getRetainedHeapSize())); //$NON-NLS-1$
                else
                    report.append(record.getRetainedHeapSize());
                report.append(SEPARATOR_CHAR + "\r\n"); //$NON-NLS-1$
            }
        }
        return report.toString();
    }

    private static String generateHistogramRecordCsvReport(List<ClassHistogramRecord> records,
                    Comparator<HistogramRecord> comparator, String[] headers)
    {
        Collections.sort(records, comparator);
        StringBuilder report = new StringBuilder((1 + records.size()) * 256);
        report.append(headers[0]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[1]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[2]);
        report.append(SEPARATOR_CHAR);
        report.append(headers[3]);
        report.append(SEPARATOR_CHAR + "\r\n"); //$NON-NLS-1$
        BytesFormat bytesFormatter = BytesFormat.getInstance();
        for (HistogramRecord record : records)
        {
            report.append(record.getLabel());
            report.append(SEPARATOR_CHAR);
            report.append(record.getNumberOfObjects());
            report.append(SEPARATOR_CHAR);
            report.append(record.getUsedHeapSize());
            report.append(SEPARATOR_CHAR);
            if (record.getRetainedHeapSize() < 0)
                report.append(">=" + bytesFormatter.format(-record.getRetainedHeapSize())); //$NON-NLS-1$
            else
                report.append(bytesFormatter.format(record.getRetainedHeapSize()));
            report.append(SEPARATOR_CHAR + "\r\n"); //$NON-NLS-1$
        }
        return report.toString();
    }

    private static void appendStringAndFillUp(StringBuilder report, String string, char character, int completeLength)
    {
        if (string != null)
        {
            report.append(string);
        }
        if (string != null)
        {
            completeLength -= string.length();
        }
        if (completeLength > 0)
        {
            for (int i = 0; i < completeLength; i++)
            {
                report.append(character);
            }
        }
    }

    private static void appendPreFillAndString(StringBuilder report, String string, char character, int completeLength)
    {
        if (string != null)
        {
            completeLength -= string.length();
        }
        if (completeLength > 0)
        {
            for (int i = 0; i < completeLength; i++)
            {
                report.append(character);
            }
        }
        if (string != null)
        {
            report.append(string);
        }
    }

    // //////////////////////////////////////////////////////////////
    // implementation as a IResultTable
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    private static Format addPositiveIndicator(Format formatter)
    {
        if (formatter instanceof DecimalFormat)
        {
            DecimalFormat pctFmt = (DecimalFormat) formatter;
            if ((pctFmt.getPositivePrefix().length() == 0
                            || pctFmt.getPositivePrefix().equals(pctFmt.getNegativePrefix()))
                            && (pctFmt.getPositiveSuffix().length() == 0
                                            || pctFmt.getPositiveSuffix().equals(pctFmt.getNegativeSuffix())))
            {
                // No positive prefix, or positive suffix
                DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance();
                // find the symbol
                String plus = Character.toString(sym.getPlusSign());
                // Make it a prefix, unless there is a prefix (same as negative) but no suffix
                if (pctFmt.getPositivePrefix().length() > 0 && pctFmt.getPositiveSuffix().length() == 0)
                    pctFmt.setPositiveSuffix(plus);
                else
                    pctFmt.setPositivePrefix(plus);
            }
        }
        return formatter;
    }

    public Column[] getColumns()
    {
        Column[] columns = new Column[] {
                        new Column(Messages.Column_ClassName, String.class).comparing(HistogramRecord.COMPARATOR_FOR_LABEL), //
                        new Column(Messages.Column_Objects, long.class).comparing(HistogramRecord.COMPARATOR_FOR_NUMBEROFOBJECTS), //
                        new Column(Messages.Column_ShallowHeap, Bytes.class) //
                                        .sorting(Column.SortDirection.DESC) //
                                        .comparing(HistogramRecord.COMPARATOR_FOR_USEDHEAPSIZE) };

        if (showPlusMinus)
        {
            Format formatter = addPositiveIndicator(NumberFormat.getIntegerInstance());
            DecimalFormat detailedFormatter = new DecimalFormat("+" + BytesFormat.DETAILED_DECIMAL_FORMAT + ";-" //$NON-NLS-1$ //$NON-NLS-2$
                            + BytesFormat.DETAILED_DECIMAL_FORMAT + ""); //$NON-NLS-1$
            NumberFormat nf = NumberFormat.getNumberInstance();
            if (nf instanceof DecimalFormat)
            {
                DecimalFormat bcf2 = (DecimalFormat)nf;
                bcf2.setMinimumFractionDigits(detailedFormatter.getMinimumFractionDigits());
                bcf2.setMaximumFractionDigits(detailedFormatter.getMaximumFractionDigits());
                addPositiveIndicator(bcf2);
                detailedFormatter = bcf2;
            }
            columns[1].formatting(formatter);
            columns[2].formatting(new BytesFormat(formatter, detailedFormatter));
        }

        return columns;
    }

    public int getRowCount()
    {
        return classHistogramRecords.size();
    }

    public Object getRow(int rowId)
    {
        return classHistogramRecords.get(rowId);
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        ClassHistogramRecord record = (ClassHistogramRecord) row;
        switch (columnIndex)
        {
            case 0:
                return record.getLabel();
            case 1:
                return record.getNumberOfObjects();
            case 2:
                return new Bytes(record.getUsedHeapSize());
        }
        return null;
    }

    public IContextObject getContext(final Object row)
    {
        final ClassHistogramRecord record = (ClassHistogramRecord) row;

        if (record.getClassId() < 0)
            return null;

        return new IContextObjectSet()
        {
            public int getObjectId()
            {
                return record.getClassId();
            }

            public int[] getObjectIds()
            {
                return record.getObjectIds();
            }

            public String getOQL()
            {
                return isDefaultHistogram ? OQL.forObjectsOfClass(record.getClassId()) : null;
            }
        };
    }

    public URL getIcon(Object row)
    {
        return Icons.CLASS;
    }

    /**
     * implementation as result tree grouped by class loader
     */
    public IResultTree groupByClassLoader()
    {
        return new ClassLoaderTree(this);
    }

    public final static class ClassLoaderTree implements IResultTree, IIconProvider
    {
        private Histogram histogram;

        public ClassLoaderTree(Histogram histogram)
        {
            this.histogram = histogram;
        }

        public Histogram getHistogram()
        {
            return histogram;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.Histogram_Column_ClassLoaderPerClass, String.class)
                                            .comparing(HistogramRecord.COMPARATOR_FOR_LABEL), //
                            new Column(Messages.Column_Objects, long.class) //
                                            .comparing(HistogramRecord.COMPARATOR_FOR_NUMBEROFOBJECTS), //
                            new Column(Messages.Column_ShallowHeap, Bytes.class) //
                                            .sorting(Column.SortDirection.DESC)//
                                            .comparing(HistogramRecord.COMPARATOR_FOR_USEDHEAPSIZE) };
        }

        public List<?> getElements()
        {
            return histogram.classLoaderHistogramRecords;
        }

        public boolean hasChildren(Object element)
        {
            return element instanceof ClassLoaderHistogramRecord;
        }

        public List<?> getChildren(Object parent)
        {
            return new ArrayList<ClassHistogramRecord>(((ClassLoaderHistogramRecord) parent).getClassHistogramRecords());
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            HistogramRecord record = (HistogramRecord) row;
            switch (columnIndex)
            {
                case 0:
                    return record.getLabel();
                case 1:
                    return record.getNumberOfObjects();
                case 2:
                    return new Bytes(record.getUsedHeapSize());
            }
            return null;
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof ClassLoaderHistogramRecord)
            {
                final ClassLoaderHistogramRecord record = (ClassLoaderHistogramRecord) row;

                if (record.getClassLoaderId() < 0)
                    return null;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return record.getClassLoaderId();
                    }

                    public int[] getObjectIds()
                    {
                        try
                        {
                            return record.getObjectIds();
                        }
                        catch (SnapshotException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram)
                            return OQL.classesByClassLoaderId(record.getClassLoaderId());
                        else
                            return null;
                    }
                };
            }
            else if (row instanceof ClassHistogramRecord)
            {
                final ClassHistogramRecord record = (ClassHistogramRecord) row;
                if (record.getClassId() < 0)
                    return null;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return record.getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        return record.getObjectIds();
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram)
                            return OQL.forObjectsOfClass(record.getClassId());
                        else
                            return null;
                    }
                };
            }
            else
            {
                return null;
            }

        }

        public URL getIcon(Object row)
        {
            return row instanceof ClassLoaderHistogramRecord ? Icons.CLASSLOADER_INSTANCE : Icons.CLASS;
        }
    }

    /**
     * implementation as result tree grouped by package
     */
    public IResultTree groupByPackage()
    {
        return new PackageTree(this);
    }

    private static class PackageNode extends HistogramRecord
    {
        private static final long serialVersionUID = 1L;

        /* package */Map<String, PackageNode> subPackages = new HashMap<String, PackageNode>();
        /* package */List<ClassHistogramRecord> classes = new ArrayList<ClassHistogramRecord>();
        PackageNode parent;

        public PackageNode(String name, PackageNode parent)
        {
            super(name);
            this.parent = parent;
        }

    }

    public static final class PackageTree implements IResultTree, IIconProvider
    {
        private Histogram histogram;
        PackageNode root;

        public PackageTree(Histogram histogram)
        {
            this.histogram = histogram;

            buildTree(histogram);
        }

        private void buildTree(Histogram histogram)
        {
            root = new PackageNode("<ROOT>", null); //$NON-NLS-1$

            for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
            {
                PackageNode current = root;

                String path[] = SimpleStringTokenizer.split(record.getLabel(), '.');
                for (int ii = 0; ii < path.length - 1; ii++)
                {
                    PackageNode child = current.subPackages.get(path[ii]);
                    if (child == null)
                        current.subPackages.put(path[ii], child = new PackageNode(path[ii], current));
                    child.incNumberOfObjects(record.numberOfObjects);
                    child.incUsedHeapSize(record.getUsedHeapSize());

                    current = child;
                }

                current.classes.add(record);
            }
        }

        public Histogram getHistogram()
        {
            return histogram;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.Histogram_Column_PackagePerClass, String.class).comparing(HistogramRecord.COMPARATOR_FOR_LABEL), //
                            new Column(Messages.Column_Objects, long.class) //
                                            .comparing(HistogramRecord.COMPARATOR_FOR_NUMBEROFOBJECTS), //
                            new Column(Messages.Column_ShallowHeap, Bytes.class) //
                                            .sorting(Column.SortDirection.DESC)//
                                            .comparing(HistogramRecord.COMPARATOR_FOR_USEDHEAPSIZE) };
        }

        public List<?> getElements()
        {
            return getChildren(root);
        }

        public boolean hasChildren(Object element)
        {
            return element instanceof PackageNode;
        }

        public List<?> getChildren(Object parent)
        {
            PackageNode node = (PackageNode) parent;
            List<HistogramRecord> answer = new ArrayList<HistogramRecord>();
            answer.addAll(node.subPackages.values());
            answer.addAll(node.classes);
            return answer;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            HistogramRecord record = (HistogramRecord) row;
            switch (columnIndex)
            {
                case 0:
                    String label = record.getLabel();
                    if (!(record instanceof PackageNode))
                    {
                        int p = label.lastIndexOf('.');
                        if (p > 0)
                            label = label.substring(p + 1);
                    }
                    return label;
                case 1:
                    return record.getNumberOfObjects();
                case 2:
                    return new Bytes(record.getUsedHeapSize());
            }
            return null;
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof PackageNode)
            {
                final PackageNode node = (PackageNode) row;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return -1;
                    }

                    public int[] getObjectIds()
                    {
                        ArrayInt objectIds = new ArrayInt();

                        LinkedList<PackageNode> nodes = new LinkedList<PackageNode>();
                        nodes.add(node);

                        while (!nodes.isEmpty())
                        {
                            PackageNode n = nodes.removeFirst();
                            for (ClassHistogramRecord record : n.classes)
                                objectIds.addAll(record.getObjectIds());

                            nodes.addAll(n.subPackages.values());
                        }

                        return objectIds.toArray();
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram())
                        {
                            StringBuilder partialPack = new StringBuilder();
                            PackageNode n = node;
                            while (n.parent != null)
                            {
                                // Escape the dot
                                partialPack.insert(0, "\\."); //$NON-NLS-1$
                                partialPack.insert(0, n.label);
                                n = n.parent;
                            }
                            Pattern pattern = Pattern.compile(partialPack.toString() + ".*"); //$NON-NLS-1$
                            return OQL.instancesByPattern(pattern, false);
                        }
                        else
                        {
                            return null;
                        }
                    }
                };
            }
            else if (row instanceof ClassHistogramRecord)
            {
                final ClassHistogramRecord record = (ClassHistogramRecord) row;
                if (record.getClassId() < 0)
                    return null;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return record.getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        return record.getObjectIds();
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram())
                            return OQL.forObjectsOfClass(record.getClassId());
                        else
                            return null;
                    }
                };
            }
            else
            {
                return null;
            }

        }

        public URL getIcon(Object row)
        {
            return row instanceof PackageNode ? Icons.PACKAGE : Icons.CLASS;
        }

    }

    /**
     * implementation as result tree grouped by superclass
     * @since 1.0
     */
    // perhaps we should have some common code between this and the package code
    public IResultTree groupBySuperclass(ISnapshot snapshot)
    {
        return new SuperclassTree(this, snapshot);
    }

    private static class SuperclassNode extends HistogramRecord
    {
        private static final long serialVersionUID = 1L;

        private final int classId;

        /* package */Map<Integer, SuperclassNode> subClasses = new HashMap<Integer, SuperclassNode>();
        /* package */List<ClassHistogramRecord> classes = new ArrayList<ClassHistogramRecord>();

        public SuperclassNode(String name, int id)
        {
            super(name);
            classId = id;
        }

        public SuperclassNode(String name)
        {
            this(name, -1);
        }

        public boolean isSimple()
        {
            return subClasses.size() == 0 && classes.size() == 1;
        }

        /**
         * Get id of the class this superclass histogram record stands for.
         * 
         * @return id of the class this superclass histogram record stands for
         */
        public int getClassId()
        {
            return classId;
        }

    }

    /**
	 * @since 1.0
	 */
    public static final class SuperclassTree implements IResultTree, IIconProvider
    {
        private Histogram histogram;
        SuperclassNode root;

        public SuperclassTree(Histogram histogram, ISnapshot snapshot)
        {
            this.histogram = histogram;

            buildTree(histogram, snapshot);
        }

        private void buildTree(Histogram histogram, ISnapshot snapshot)
        {
            root = new SuperclassNode("<ROOT>"); //$NON-NLS-1$

            for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
            {
                SuperclassNode current = root;

                List<IClass> l1 = new ArrayList<IClass>();
                try
                {
                    
                    int classId = record.getClassId();
                    if (classId >= 0)
                    {
                        for (IClass cl = ((IClass) snapshot.getObject(classId)); cl != null; cl = cl.getSuperClass())
                        {
                            l1.add(cl);
                        }
                    }
                }
                catch (SnapshotException e)
                {}
                for (int i = l1.size() - 1; i >= 0; --i)
                {
                    IClass superclass = l1.get(i);
                    SuperclassNode child = current.subClasses.get(superclass.getObjectId());
                    if (child == null)
                        current.subClasses.put(superclass.getObjectId(), child = new SuperclassNode(superclass
                                        .getName(), superclass.getObjectId()));
                    child.incNumberOfObjects(record.numberOfObjects);
                    child.incUsedHeapSize(record.getUsedHeapSize());

                    current = child;
                }

                current.classes.add(record);
            }
        }

        public Histogram getHistogram()
        {
            return histogram;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public Column[] getColumns()
        {
            return new Column[] {
                            new Column(Messages.Histogram_Column_SuperclassPerClass, String.class).comparing(HistogramRecord.COMPARATOR_FOR_LABEL), //
                            new Column(Messages.Column_Objects, long.class) //
                                            .comparing(HistogramRecord.COMPARATOR_FOR_NUMBEROFOBJECTS), //
                            new Column(Messages.Column_ShallowHeap, Bytes.class) //
                                            .sorting(Column.SortDirection.DESC)//
                                            .comparing(HistogramRecord.COMPARATOR_FOR_USEDHEAPSIZE) };
        }

        public List<?> getElements()
        {
            return getChildren(root);
        }

        public boolean hasChildren(Object element)
        {
            return element instanceof SuperclassNode;
        }

        public List<?> getChildren(Object parent)
        {
            SuperclassNode node = (SuperclassNode) parent;
            List<HistogramRecord> answer = new ArrayList<HistogramRecord>();
            for (SuperclassNode sub : node.subClasses.values())
            {
                if (sub.isSimple()) answer.add(sub.classes.get(0));
                else answer.add(sub);
            }
            answer.addAll(node.classes);
            return answer;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            HistogramRecord record = (HistogramRecord) row;
            switch (columnIndex)
            {
                case 0:
                    String label = record.getLabel();
                    return label;
                case 1:
                    return record.getNumberOfObjects();
                case 2:
                    return new Bytes(record.getUsedHeapSize());
            }
            return null;
        }

        public IContextObject getContext(Object row)
        {
            if (row instanceof SuperclassNode)
            {
                final SuperclassNode node = (SuperclassNode) row;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return node.getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        ArrayInt objectIds = new ArrayInt();

                        LinkedList<SuperclassNode> nodes = new LinkedList<SuperclassNode>();
                        nodes.add(node);

                        while (!nodes.isEmpty())
                        {
                            SuperclassNode n = nodes.removeFirst();
                            for (ClassHistogramRecord record : n.classes)
                                objectIds.addAll(record.getObjectIds());

                            nodes.addAll(n.subClasses.values());
                        }

                        return objectIds.toArray();
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram())
                            return "SELECT * FROM INSTANCEOF "+getObjectId(); //$NON-NLS-1$
                        else
                            return null;
                    }
                };
            }
            else if (row instanceof ClassHistogramRecord)
            {
                final ClassHistogramRecord record = (ClassHistogramRecord) row;
                if (record.getClassId() < 0)
                    return null;

                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return record.getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        return record.getObjectIds();
                    }

                    public String getOQL()
                    {
                        if (histogram.isDefaultHistogram())
                            return OQL.forObjectsOfClass(record.getClassId());
                        else
                            return null;
                    }
                };
            }
            else
            {
                return null;
            }

        }

        public URL getIcon(Object row)
        {
            return row instanceof SuperclassNode ? Icons.SUPERCLASS : Icons.CLASS;
        }

    }

}
