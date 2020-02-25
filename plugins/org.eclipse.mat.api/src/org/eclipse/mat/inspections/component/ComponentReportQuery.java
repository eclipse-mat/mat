/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others
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
package org.eclipse.mat.inspections.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.inspections.ReferenceQuery;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SilentProgressListener;
import org.eclipse.mat.util.Units;

@CommandName("component_report")
@Icon("/META-INF/icons/component_report.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/component_report.html")
public class ComponentReportQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public boolean aggressive;

    private static final String HTML_BREAK = "<br>"; //$NON-NLS-1$

    public IResult execute(IProgressListener listener) throws Exception
    {
        SectionSpec componentReport = new SectionSpec(MessageUtil.format(Messages.ComponentReportQuery_ComponentReport,
                        objects.getLabel()));

        Ticks ticks = new Ticks(listener, componentReport.getName(), 13);

        // calculate retained set
        int[] retained = calculateRetainedSize(ticks);

        ticks.tick();

        Histogram histogram = snapshot.getHistogram(retained, ticks);
        if (ticks.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        ticks.tick();

        long totalSize = snapshot.getHeapSize(retained);
        ticks.tick();

        addOverview(componentReport, totalSize, retained, histogram, ticks);

        SectionSpec possibleWaste = new SectionSpec(Messages.ComponentReportQuery_PossibleMemoryWaste);
        componentReport.add(possibleWaste);

        try
        {
            addDuplicateStrings(possibleWaste, histogram, ticks);
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        try
        {
            addEmptyCollections(possibleWaste, totalSize, histogram, ticks);
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        try
        {
            addCollectionFillRatios(possibleWaste, totalSize, histogram, ticks);
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        SectionSpec miscellaneous = new SectionSpec(Messages.ComponentReportQuery_Miscellaneous);
        componentReport.add(miscellaneous);

        try
        {
            ReferenceMessages msg = new SoftReferenceMessages();
            addReferenceStatistic(miscellaneous, histogram, ticks, "java.lang.ref.SoftReference", msg); //$NON-NLS-1$
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        try
        {
            ReferenceMessages msg = new WeakReferenceMessages();
            addReferenceStatistic(miscellaneous, histogram, ticks, "java.lang.ref.WeakReference", msg); //$NON-NLS-1$
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        try
        {
            addFinalizerStatistic(miscellaneous, retained, ticks);
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        try
        {
            addHashMapsCollisionRatios(miscellaneous, totalSize, histogram, ticks);
        }
        catch (UnsupportedOperationException e)
        { /* ignore, if not supported by heap format */}

        ticks.delegate.done();

        return componentReport;
    }

    /**
     * Purpose: absorb progress reports from sub-queries to report a smoother
     * overall progress
     */
    private static class Ticks implements IProgressListener
    {
        IProgressListener delegate;

        public Ticks(IProgressListener delegate, String task, int totalTicks)
        {
            this.delegate = delegate;
            this.delegate.beginTask(task, totalTicks);
        }

        public void tick()
        {
            delegate.worked(1);
        }

        public void beginTask(String name, int totalWork)
        {
            delegate.subTask(name);
        }

        public void subTask(String name)
        {
            delegate.subTask(name);
        }

        public void done()
        {}

        public boolean isCanceled()
        {
            return delegate.isCanceled();
        }

        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {
            delegate.sendUserMessage(severity, message, exception);
        }

        public void setCanceled(boolean value)
        {
            delegate.setCanceled(true);
        }

        public void worked(int work)
        {}

    }

    // //////////////////////////////////////////////////////////////
    // calculate retained size
    // //////////////////////////////////////////////////////////////

    private String oqlretained;
    private int[] calculateRetainedSize(Ticks ticks) throws SnapshotException
    {
        int[] retained = null;

        List<ExcludedReferencesDescriptor> excludes = new ArrayList<ExcludedReferencesDescriptor>();

        addExcludes(excludes, "java.lang.ref.Finalizer", "referent"); //$NON-NLS-1$ //$NON-NLS-2$
        addExcludes(excludes, "java.lang.ref.PhantomReference", "referent"); //$NON-NLS-1$ //$NON-NLS-2$
        addExcludes(excludes, "java.lang.ref.WeakReference", "referent"); //$NON-NLS-1$ //$NON-NLS-2$
        addExcludes(excludes, "java.lang.ref.SoftReference", "referent"); //$NON-NLS-1$ //$NON-NLS-2$
        int[] ids = objects.getIds(ticks);
        String label = objects.getLabel().toLowerCase(Locale.ENGLISH);
        boolean useoql = label.startsWith("select * ") || label.startsWith("select objects "); //$NON-NLS-1$ //$NON-NLS-2$
        if (excludes.isEmpty())
        {
            retained = snapshot.getRetainedSet(ids, ticks);
        }
        else
        {
            int retlen = -1;
            long hs = 0;
            if (useoql)
            {
                /*
                 * See if the ordinary retained set as used by OQL returns the same results
                 * Do this first so we can discard the result and just check the length
                 * Consider o is component roots, z = other roots, SR=SoftReference
                 * o->SR->A
                 * o->B
                 * z->SR->B
                 * If SoftRefs followed, retained set = SR,A
                 * if SoftRefs not followed, retained set = SR,B
                 * so same number of refs doesn't prove equality.
                 * Order doesn't matter though.
                 */
                int retained1[] = snapshot.getRetainedSet(ids, new SilentProgressListener(ticks));
                retlen = retained1.length;
                for (int i : retained1)
                {
                    hs += snapshot.mapIdToAddress(i);
                }
                retained1 = null;
            }
            retained = snapshot.getRetainedSet(ids, //
                            excludes.toArray(new ExcludedReferencesDescriptor[0]), //
                            ticks);
            if (useoql)
            {
                if (retained.length != retlen)
                {
                    useoql = false;
                }
                else
                {
                    // Further test that they seem the same
                    long hs1 = 0;
                    for (int i : retained)
                    {
                        hs1 += snapshot.mapIdToAddress(i);
                    }
                    if (hs != hs1)
                    {
                        useoql = false;
                    }
                }
            }
        }
        if (useoql)
        {
            // Excluded makes no difference, so can use standard OQL retained set
            String oql0 = objects.getLabel().trim();
            if (oql0.endsWith(";")) //$NON-NLS-1$
                oql0 = oql0.substring(0, oql0.length() - 1);
            oqlretained = oql0.substring(0, 6) + " as retained set" + oql0.substring(6); //$NON-NLS-1$
        }
        return retained;
    }

    private void addExcludes(List<ExcludedReferencesDescriptor> excludes, String className, String... fields)
                    throws SnapshotException
    {
        Collection<IClass> finalizer = snapshot.getClassesByName(className, true);
        if (finalizer != null)
        {
            ArrayInt objectIds = new ArrayInt();
            for (IClass c : finalizer)
                objectIds.addAll(c.getObjectIds());

            excludes.add(new ExcludedReferencesDescriptor(objectIds.toArray(), fields));
        }
    }

    // //////////////////////////////////////////////////////////////
    // overview section
    // //////////////////////////////////////////////////////////////

    private void addOverview(SectionSpec componentReport, long totalSize, int[] retained, Histogram histogram,
                    Ticks listener) throws Exception
    {
        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_Overview);
        overview.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());

        addOverviewNumbers(retained, histogram, overview, totalSize);
        listener.tick();
        addOverviewPie(overview, totalSize);
        listener.tick();
        addTopConsumer(overview, retained, listener);
        listener.tick();
        addRetainedSet(overview, histogram);
        listener.tick();

        componentReport.add(overview);
    }

    private void addOverviewNumbers(int[] retained, Histogram histogram, SectionSpec overview, long totalSize)
    {
        int noOfClasses = histogram.getClassHistogramRecords().size();
        int noOfObjects = retained.length;
        int noOfClassLoaders = histogram.getClassLoaderHistogramRecords().size();

        StringBuilder buf = new StringBuilder();
        buf.append(Messages.ComponentReportQuery_Size + " <strong>") //$NON-NLS-1$
                        .append(Units.Storage.of(totalSize).format(totalSize)).append("</strong> "); //$NON-NLS-1$
        buf.append(Messages.ComponentReportQuery_Classes + " <strong>") //$NON-NLS-1$
                        .append(Units.Plain.of(noOfClasses).format(noOfClasses)).append("</strong> "); //$NON-NLS-1$
        buf.append(Messages.ComponentReportQuery_Objects + " <strong>") //$NON-NLS-1$
                        .append(Units.Plain.of(noOfObjects).format(noOfObjects)).append("</strong> "); //$NON-NLS-1$
        buf.append(Messages.ComponentReportQuery_ClassLoader + " <strong>") //$NON-NLS-1$
                        .append(Units.Plain.of(noOfClassLoaders).format(noOfClassLoaders)).append("</strong>"); //$NON-NLS-1$
        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Details, new TextResult(buf.toString(), true));
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addOverviewPie(SectionSpec overview, long totalSize)
    {
        PieFactory pie = new PieFactory(snapshot);
        pie.addSlice(-1, objects.getLabel(), totalSize, totalSize);
        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Distribution, pie.build());
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addTopConsumer(SectionSpec componentReport, int[] retained, IProgressListener listener)
                    throws Exception
    {
        IResult result = SnapshotQuery.lookup("top_consumers_html", snapshot) //$NON-NLS-1$
                        .setArgument("objects", retained) //$NON-NLS-1$
                        .execute(listener);

        QuerySpec topConsumers = new QuerySpec(Messages.ComponentReportQuery_TopConsumers);
        topConsumers.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        topConsumers.set(Params.Html.COLLAPSED, Boolean.FALSE.toString());
        topConsumers.setResult(result);
        if (oqlretained != null)
        {
            String cmd = "top_consumers_html " + oqlretained + ";"; //$NON-NLS-1$ //$NON-NLS-2$
            topConsumers.setCommand(cmd);
        }
        componentReport.add(topConsumers);
    }

    private void addRetainedSet(SectionSpec componentReport, Histogram histogram)
    {
        QuerySpec retainedSet = new QuerySpec(Messages.ComponentReportQuery_RetainedSet);
        retainedSet.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        retainedSet.setResult(histogram);
        try
        {
            String objectLabel = objects.getLabel();
            if (!objectLabel.startsWith("[")) //$NON-NLS-1$
            {
                String command = "customized_retained_set"; //$NON-NLS-1$
                command += " " + objectLabel; //$NON-NLS-1$
                command += " -x java.lang.ref.Finalizer:referent"; //$NON-NLS-1$
                command += " java.lang.ref.PhantomReference:referent"; //$NON-NLS-1$
                command += " java.lang.ref.WeakReference:referent"; //$NON-NLS-1$
                command += " java.lang.ref.SoftReference:referent"; //$NON-NLS-1$
                SnapshotQuery.parse(command, snapshot);
                // It parses, so presume the command was okay
                retainedSet.setCommand(command);
            }
        }
        catch (SnapshotException e)
        {
            // Objects cannot be expressed simply for a command
        }
        componentReport.add(retainedSet);
    }

    // //////////////////////////////////////////////////////////////
    // duplicate strings
    // //////////////////////////////////////////////////////////////

    private void addDuplicateStrings(SectionSpec componentReport, Histogram histogram, Ticks listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (listener.isCanceled())
                break;
            if (!"char[]".equals(record.getLabel())) //$NON-NLS-1$
                continue;

            int[] objectIds = record.getObjectIds();
            if (objectIds.length > 100000)
            {
                int[] copy = new int[100000];
                System.arraycopy(objectIds, 0, copy, 0, copy.length);
                objectIds = copy;
            }

            RefinedResultBuilder builder = SnapshotQuery.lookup("group_by_value", snapshot) //$NON-NLS-1$
                            .setArgument("objects", objectIds) //$NON-NLS-1$
                            .refine(listener);

            builder.setFilter(1, ">=10"); //$NON-NLS-1$
            builder.setInlineRetainedSizeCalculation(true);
            RefinedTable table = (RefinedTable) builder.build();
            TotalsRow totals = new TotalsRow();
            table.calculateTotals(table.getRows(), totals, listener);

            StringBuilder comment = new StringBuilder();
            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_FoundOccurrences, table.getRowCount(),
                            HTMLUtils.escapeText(totals.getLabel(2))));
            comment.append("<p>").append(Messages.ComponentReportQuery_TopElementsInclude).append("</p><ul>"); //$NON-NLS-1$ //$NON-NLS-2$

            for (int rowId = 0; rowId < table.getRowCount() && rowId < 5; rowId++)
            {
                Object row = table.getRow(rowId);
                String value = table.getFormattedColumnValue(row, 0);
                if (value.length() > 50)
                    value = value.substring(0, 50) + "..."; //$NON-NLS-1$

                String size = table.getFormattedColumnValue(row, 3);

                comment.append("<li>").append(table.getFormattedColumnValue(row, 1)); //$NON-NLS-1$
                comment.append(" &times; <strong>").append(HTMLUtils.escapeText(value)).append("</strong> "); //$NON-NLS-1$ //$NON-NLS-2$
                comment.append(MessageUtil.format(Messages.ComponentReportQuery_Label_Bytes, size)).append("</li>"); //$NON-NLS-1$
            }
            comment.append("</ul>"); //$NON-NLS-1$

            // build result
            SectionSpec duplicateStrings = new SectionSpec(Messages.ComponentReportQuery_DuplicateStrings);
            componentReport.add(duplicateStrings);

            QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                            true));
            spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
            duplicateStrings.add(spec);

            spec = new QuerySpec(Messages.ComponentReportQuery_Histogram);
            spec.setResult(table);
            IObject o = snapshot.getObject(record.getClassId());
            if (o instanceof IClass)
                addCommand(spec, "group_by_value", objectIds, (IClass)o); //$NON-NLS-1$
            duplicateStrings.add(spec);

            listener.tick();
            break;
        }
    }

    // //////////////////////////////////////////////////////////////
    // collection usage
    // //////////////////////////////////////////////////////////////

    private void addCommand(QuerySpec spec, String command, int suspects[], IClass clazz)
    {
        if (suspects.length > 0 && suspects.length <= 30)
        {
            try
            {
                StringBuilder sb = new StringBuilder(command);
                for (int i : suspects)
                {
                    sb.append(" 0x").append(Long.toHexString(snapshot.mapIdToAddress(i))); //$NON-NLS-1$
                }
                spec.setCommand(sb.toString());
            }
            catch (SnapshotException e)
            {} // Ignore if problem
        }
        else if (oqlretained != null)
        {
            String oql = "select * from objects (" + oqlretained + ") m where m.@clazz.@objectAddress = " + clazz.getObjectAddress() + "L"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String cmd = command + " " + oql + ";"; //$NON-NLS-1$ //$NON-NLS-2$
            spec.setCommand(cmd);
        }
    }

    private void addEmptyCollections(SectionSpec componentReport, long totalSize, Histogram histogram, Ticks listener)
                    throws Exception
    {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_EmptyCollections);

        StringBuilder comment = new StringBuilder();
        SectionSpec collectionbySizeSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        collectionbySizeSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (listener.isCanceled())
                break;
            IClass clazz = (IClass) snapshot.getObject(record.getClassId());
            ICollectionExtractor extractor = CollectionExtractionUtils.findCollectionExtractor(clazz.getName());
            if (extractor != null && extractor.hasSize())
            {
                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collections_grouped_by_size", snapshot) //$NON-NLS-1$
                                .setArgument("objects", record.getObjectIds()) //$NON-NLS-1$
                                .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count && rowId < 10; rowId++)
                {
                    Object row = refinedTable.getRow(rowId);
                    int collectionSize = (Integer) refinedTable.getColumnValue(row, 0);

                    if (collectionSize == 0)
                    {
                        long size = Math.abs((Long) refinedTable.getColumnValue(row, 3));
                        if (size > threshold)
                        {
                            int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                            String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                            if (comment.length() == 0)
                                comment.append(Messages.ComponentReportQuery_DetectedEmptyCollections + "<ul>"); //$NON-NLS-1$

                            comment.append("<li>"); //$NON-NLS-1$
                            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                            numberOfObjects, HTMLUtils.escapeText(clazz.getName()), HTMLUtils.escapeText(retainedSize)));
                            comment.append("</li>"); //$NON-NLS-1$
                        }

                        break;
                    }
                }

                QuerySpec bySizeSpec = new QuerySpec(clazz.getName());
                bySizeSpec.setResult(refinedTable);
                addCommand(bySizeSpec, "collections_grouped_by_size", record.getObjectIds(), clazz); //$NON-NLS-1$
                collectionbySizeSpec.add(bySizeSpec);
            }
        }
        listener.tick();

        if (collectionbySizeSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoExcessiveEmptyCollectionsFound);
        else
            comment.append("</ul>"); //$NON-NLS-1$

        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(), true));
        spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(spec);

        overview.add(collectionbySizeSpec);

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // collection fill ratio
    // //////////////////////////////////////////////////////////////

    private void addCollectionFillRatios(SectionSpec componentReport, long totalSize, Histogram histogram,
                    Ticks listener) throws Exception
    {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_CollectionFillRatios);

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (listener.isCanceled())
                break;
            IClass clazz = (IClass) snapshot.getObject(record.getClassId());
            ICollectionExtractor extractor = CollectionExtractionUtils.findCollectionExtractor(clazz.getName());
            if (extractor != null && extractor.hasSize() && extractor.hasExtractableContents())
            {
                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collection_fill_ratio", snapshot) //$NON-NLS-1$
                                .setArgument("objects", record.getObjectIds()) //$NON-NLS-1$
                                .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count; rowId++)
                {
                    Object row = refinedTable.getRow(rowId);
                    double fillRatio = (Double) refinedTable.getColumnValue(row, 0);

                    if (fillRatio > 0d && fillRatio < 0.21d)
                    {
                        long size = Math.abs((Long) refinedTable.getColumnValue(row, 3));
                        if (size > threshold)
                        {
                            int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                            String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                            if (comment.length() == 0)
                                comment.append(Messages.ComponentReportQuery_Msg_DetectedCollectionFillRatios + "<ul>"); //$NON-NLS-1$

                            comment.append("<li>"); //$NON-NLS-1$
                            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                            numberOfObjects, HTMLUtils.escapeText(clazz.getName()), HTMLUtils.escapeText(retainedSize)));
                            comment.append("</li>"); //$NON-NLS-1$
                        }

                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                addCommand(spec, "collection_fill_ratio", record.getObjectIds(), clazz); //$NON-NLS-1$
                detailsSpec.add(spec);
            }
        }
        listener.tick();

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoLowFillRatiosFound);
        else
            comment.append("</ul>"); //$NON-NLS-1$

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                        true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // hash map collision ratios
    // //////////////////////////////////////////////////////////////

    private void addHashMapsCollisionRatios(SectionSpec componentReport, long totalSize, Histogram histogram,
                    Ticks listener) throws Exception
    {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_MapCollisionRatios);

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (listener.isCanceled())
                break;
            IClass clazz = (IClass) snapshot.getObject(record.getClassId());
            ICollectionExtractor extractor = CollectionExtractionUtils.findCollectionExtractor(clazz.getName());
            if (extractor != null && extractor instanceof IMapExtractor)
            {
                // run the query: collections by size
                int[] objectIds = record.getObjectIds();
                if (objectIds.length > 20000)
                {
                    int[] copy = new int[20000];
                    System.arraycopy(objectIds, 0, copy, 0, copy.length);
                    objectIds = copy;
                }

                RefinedResultBuilder builder = SnapshotQuery.lookup("map_collision_ratio", snapshot) //$NON-NLS-1$
                                .setArgument("objects", objectIds) //$NON-NLS-1$
                                .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count; rowId++)
                {
                    Object row = refinedTable.getRow(rowId);
                    double collisionRato = (Double) refinedTable.getColumnValue(row, 0);

                    if (collisionRato > 0.80d)
                    {
                        int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                        String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                        if (comment.length() == 0)
                            comment.append(Messages.ComponentReportQuery_Msg_DetectedCollisionRatios + "<ul>"); //$NON-NLS-1$

                        comment.append("<li>"); //$NON-NLS-1$
                        comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                        numberOfObjects, HTMLUtils.escapeText(clazz.getName()), HTMLUtils.escapeText(retainedSize)));
                        comment.append("</li>"); //$NON-NLS-1$
                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                addCommand(spec, "map_collision_ratio", record.getObjectIds(), clazz); //$NON-NLS-1$
                detailsSpec.add(spec);
            }
        }
        listener.tick();

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoCollisionRatiosFound);
        else
            comment.append("</ul>"); //$NON-NLS-1$

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                        true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // weak/soft reference statistics
    // //////////////////////////////////////////////////////////////
    private static abstract class ReferenceMessages
    {
        public String ReferenceStatistics;
        public String Msg_NoReferencesFound;
        public String NoAliveReferences;
        public String HistogramOfReferences;
        public String ReferenceStatQuery_Label_Referenced;
        public String ReferenceStatQuery_Label_Retained;
        public String ReferenceStatQuery_Label_StronglyRetainedReferents;
        public String Msg_ReferencesFound;
        public String Msg_ReferencesRetained;
        public String Msg_ReferencesStronglyRetained;
    }

    private static class SoftReferenceMessages extends ReferenceMessages
    {
        {
            ReferenceStatistics = Messages.ComponentReportQuery_SoftReferenceStatistics;
            Msg_NoReferencesFound = Messages.ComponentReportQuery_Msg_NoSoftReferencesFound;
            NoAliveReferences = Messages.ComponentReportQuery_Msg_NoAliveSoftReferences;
            HistogramOfReferences = Messages.ComponentReportQuery_HistogramOfSoftReferences;
            ReferenceStatQuery_Label_Referenced = Messages.SoftReferenceStatQuery_Label_Referenced;
            ReferenceStatQuery_Label_Retained = Messages.SoftReferenceStatQuery_Label_Retained;
            ReferenceStatQuery_Label_StronglyRetainedReferents = Messages.SoftReferenceStatQuery_Label_StronglyRetainedReferents;
            Msg_ReferencesFound = Messages.ComponentReportQuery_Msg_SoftReferencesFound;
            Msg_ReferencesRetained = Messages.ComponentReportQuery_Msg_SoftReferencesRetained;
            Msg_ReferencesStronglyRetained = Messages.ComponentReportQuery_Msg_SoftReferencesStronglyRetained;
        }
    }

    private static class WeakReferenceMessages extends ReferenceMessages
    {
        {
            ReferenceStatistics = Messages.ComponentReportQuery_WeakReferenceStatistics;
            Msg_NoReferencesFound = Messages.ComponentReportQuery_Msg_NoWeakReferencesFound;
            NoAliveReferences = Messages.ComponentReportQuery_Msg_NoAliveWeakReferences;
            HistogramOfReferences = Messages.ComponentReportQuery_HistogramOfWeakReferences;
            ReferenceStatQuery_Label_Referenced = Messages.WeakReferenceStatQuery_Label_Referenced;
            ReferenceStatQuery_Label_Retained = Messages.WeakReferenceStatQuery_Label_Retained;
            ReferenceStatQuery_Label_StronglyRetainedReferents = Messages.WeakReferenceStatQuery_Label_StronglyRetainedReferents;
            Msg_ReferencesFound = Messages.ComponentReportQuery_Msg_WeakReferencesFound;
            Msg_ReferencesRetained = Messages.ComponentReportQuery_Msg_WeakReferencesRetained;
            Msg_ReferencesStronglyRetained = Messages.ComponentReportQuery_Msg_WeakReferencesStronglyRetained;
        }
    }

    private void addReferenceStatistic(SectionSpec componentReport, Histogram histogram, Ticks ticks, String className,
                    ReferenceMessages messages) throws SnapshotException
    {
        Collection<IClass> classes = snapshot.getClassesByName(className, true);
        if (classes == null || classes.isEmpty())
        {
            addEmptyResult(componentReport, messages.ReferenceStatistics, messages.Msg_NoReferencesFound);
            return;
        }

        SetInt softRefClassIds = new SetInt(classes.size());
        for (IClass c : classes)
            softRefClassIds.add(c.getObjectId());

        ArrayList<ClassHistogramRecord> softRefs = new ArrayList<ClassHistogramRecord>();
        long numObjects = 0, heapSize = 0;
        ArrayInt instanceSet = new ArrayInt();
        SetInt referentSet = new SetInt();

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (ticks.isCanceled())
                break;
            if (softRefClassIds.contains(record.getClassId()))
            {
                softRefs.add(record);
                numObjects += record.getNumberOfObjects();
                heapSize += record.getUsedHeapSize();

                instanceSet.addAll(record.getObjectIds());

                for (int objectId : record.getObjectIds())
                {
                    IInstance obj = (IInstance) snapshot.getObject(objectId);

                    ObjectReference ref = ReferenceQuery.getReferent(obj);
                    if (ref != null)
                        referentSet.add(ref.getObjectId());
                    if (ticks.isCanceled())
                        break;
                }
            }
        }
        ticks.tick();

        if (instanceSet.isEmpty())
        {
            addEmptyResult(componentReport, messages.ReferenceStatistics, messages.NoAliveReferences);
            return;
        }

        Histogram softRefHistogram = new Histogram(messages.HistogramOfReferences, softRefs, null, numObjects,
                        heapSize, 0);

        CompositeResult referents = ReferenceQuery.execute(instanceSet, referentSet, snapshot,
                        messages.ReferenceStatQuery_Label_Referenced, messages.ReferenceStatQuery_Label_Retained,
                        messages.ReferenceStatQuery_Label_StronglyRetainedReferents, "referent", ticks); //$NON-NLS-1$

        StringBuilder comment = new StringBuilder();
        comment.append(MessageUtil.format(messages.Msg_ReferencesFound, instanceSet.size(), referentSet.size()))
                        .append(HTML_BREAK);

        Histogram onlySoftlyReachable = (Histogram) referents.getResultEntries().get(1).getResult();
        numObjects = 0;
        heapSize = 0;
        for (ClassHistogramRecord r : onlySoftlyReachable.getClassHistogramRecords())
        {
            numObjects += r.getNumberOfObjects();
            heapSize += r.getUsedHeapSize();
        }
        comment.append(MessageUtil.format(messages.Msg_ReferencesRetained, //
                        numObjects, //
                        Units.Storage.of(heapSize).format(heapSize))).append(HTML_BREAK);

        Histogram stronglyReachableReferents = (Histogram) referents.getResultEntries().get(2).getResult();
        numObjects = 0;
        heapSize = 0;
        for (ClassHistogramRecord r : stronglyReachableReferents.getClassHistogramRecords())
        {
            numObjects += r.getNumberOfObjects();
            heapSize += r.getUsedHeapSize();
        }
        if (numObjects >= 1)
        {
            comment.append("<strong>").append(MessageUtil.format(Messages.ComponentReportQuery_PossibleMemoryLeak)).append("</strong> "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        comment.append(MessageUtil.format(messages.Msg_ReferencesStronglyRetained, numObjects, //
                        Units.Storage.of(heapSize).format(heapSize)));

        SectionSpec overview = new SectionSpec(messages.ReferenceStatistics);
        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                        true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        QuerySpec child = new QuerySpec(softRefHistogram.getLabel(), softRefHistogram);
        child.set(Params.Rendering.DERIVED_DATA_COLUMN, "_default_=" + RetainedSizeDerivedData.APPROXIMATE.getCode()); //$NON-NLS-1$
        overview.add(child);

        for (CompositeResult.Entry entry : referents.getResultEntries())
        {
            child = new QuerySpec(entry.getName(), entry.getResult());
            overview.add(child);
        }

        /*
         * We have some candidates.
         * Find the path from the suspect to the roots excluding weak referents and see
         * if it goes through a weak reference which refers to this suspect.
         * If so, it is a candidate problem.
         * Record it, and the path and the path by class.
         */
        if (numObjects >= 1)
        {
            // Number of paths to show per referent type
            int maxsuspectspertype = 1000;
            /*
             * maximum paths to examine
             * It is possible that the shortest path doesn't go through the actual reference
             * but through others.
             */
            int maxpaths = 10;

            /*
             * Examine per class of the referred-to objects - as they might have different uses.
             */
            for (ClassHistogramRecord cr : stronglyReachableReferents.getClassHistogramRecords())
            {
                // Add all the suspect referents
                SetInt referents1 = new SetInt();
                for (int r : cr.getObjectIds())
                {
                    referents1.add(r);
                }
                // Find the references
                ArrayInt ai = new ArrayInt();
                for (IteratorInt i2 = instanceSet.iterator(); i2.hasNext(); )
                {
                    int objectId = i2.next();
                    IInstance obj = (IInstance) snapshot.getObject(objectId);

                    ObjectReference ref = ReferenceQuery.getReferent(obj);
                    if (ref != null)
                    {
                        if (referents1.contains(ref.getObjectId()))
                            ai.add(obj.getObjectId());
                    }
                    if (ticks.isCanceled())
                        break;
                }
                IResult result = SnapshotQuery.lookup("reference_leak", snapshot) //$NON-NLS-1$
                                .setArgument("objects", ai.toArray()) //$NON-NLS-1$
                                .setArgument("maxpaths", maxpaths) //$NON-NLS-1$
                                .setArgument("maxobjs", maxsuspectspertype) //$NON-NLS-1$
                                .setArgument("factor", 0.6) //$NON-NLS-1$
                                .execute(ticks);
                if (result instanceof CompositeResult)
                {
                    CompositeResult cr1 = (CompositeResult)result;
                    List<CompositeResult.Entry>entries = cr1.getResultEntries();
                    if (!(entries.size() == 1 && entries.get(0).getResult() instanceof TextResult))
                    {
                        commentSpec.set(Params.Html.IS_IMPORTANT, Boolean.TRUE.toString());
                        QuerySpec child1 = new QuerySpec(MessageUtil.format(Messages.ComponentReportQuery_ExampleLeakDetails, cr.getLabel()), cr1);
                        child1.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
                        overview.add(child1);
                    }
                    else
                    {
                        IResult tr = entries.get(0).getResult();
                        QuerySpec child1 = new QuerySpec(MessageUtil.format(Messages.ComponentReportQuery_ReferentResult, cr.getLabel()), tr);
                        child1.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
                        overview.add(child1);
                    }
                }
                else
                {
                    QuerySpec child1 = new QuerySpec(MessageUtil.format(Messages.ComponentReportQuery_ReferentResult, cr.getLabel()), result);
                    child1.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
                    overview.add(child1);
                }
            }
        }

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // finalizer statistics
    // //////////////////////////////////////////////////////////////

    private void addFinalizerStatistic(SectionSpec componentReport, int[] retained, Ticks ticks)
                    throws SnapshotException
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.Finalizer", true); //$NON-NLS-1$
        if (classes == null || classes.isEmpty())
        {
            addEmptyResult(componentReport, Messages.ComponentReportQuery_FinalizerStatistics,
                            Messages.ComponentReportQuery_Msg_NoFinalizerObjects);
            return;
        }

        // Currently SetInt uses a lot of memory - 32+8 bits @ 75% capacity = 53 bits per item
        boolean useBits = retained.length > Math.max(100000, snapshot.getSnapshotInfo().getNumberOfObjects() / 53);
        SetInt retainedSet = null;
        BitField retainedIds = null;
        if (useBits)
        {
            retainedIds = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        }
        else
        {
            // SetInt will resize when it is 75% full, so allocate it big enough now
            retainedSet = new SetInt(Math.max(((retained.length + 2) / 3) * 4, retained.length));
        }
        for (int ii = 0; ii < retained.length; ii++)
        {
            if (useBits)
                retainedIds.set(retained[ii]);
            else
                retainedSet.add(retained[ii]);
        }

        // Avoid duplicates from the two approaches by using a set
        SetInt finalizers = new SetInt();

        for (IClass c : classes)
        {
            for (int objectId : c.getObjectIds())
            {
                if (ticks.isCanceled())
                    break;
                IInstance obj = (IInstance) snapshot.getObject(objectId);

                ObjectReference ref = ReferenceQuery.getReferent(obj);
                if (ref != null)
                {
                    int referentId = ref.getObjectId();
                    if (useBits ? retainedIds.get(referentId) : retainedSet.contains(referentId))
                    {
                        finalizers.add(referentId);
                    }
                }
            }
        }

        // Add other objects marked as not yet finalized
        for (int root : snapshot.getGCRoots())
        {
            GCRootInfo ifo[] = snapshot.getGCRootInfo(root);
            for (GCRootInfo rootInfo : ifo)
            {
                if (rootInfo.getType() == GCRootInfo.Type.UNFINALIZED
                                || rootInfo.getType() == GCRootInfo.Type.FINALIZABLE)
                {
                    int referentId = rootInfo.getObjectId();
                    if (useBits ? retainedIds.get(referentId) : retainedSet.contains(referentId))
                    {
                        finalizers.add(referentId);
                    }
                    break;
                }
            }
        }

        if (finalizers.isEmpty())
        {
            addEmptyResult(componentReport, Messages.ComponentReportQuery_FinalizerStatistics,
                            Messages.ComponentReportQuery_Msg_NoFinalizerFound);
            return;
        }

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_FinalizerStatistics);
        StringBuilder comment = new StringBuilder();
        comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_TotalFinalizerMethods, finalizers.size()));

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                        true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        Histogram histogram = snapshot.getHistogram(finalizers.toArray(), ticks);
        if (ticks.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram.setLabel(Messages.ComponentReportQuery_HistogramFinalizeMethod);
        overview.add(new QuerySpec(histogram.getLabel(), histogram));

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // internal utility methods
    // //////////////////////////////////////////////////////////////

    private void addEmptyResult(SectionSpec report, String sectionLabel, String message)
    {
        SectionSpec section = new SectionSpec(sectionLabel);
        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(message, true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        section.add(commentSpec);
        report.add(section);
    }
}
