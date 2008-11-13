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
package org.eclipse.mat.inspections.component;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.ReferenceQuery;
import org.eclipse.mat.inspections.collections.CollectionUtil;
import org.eclipse.mat.inspections.util.PieFactory;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Name;
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
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.Units;

@Name("Component Report")
@CommandName("component_report")
@Category("Leak Identification")
@Help("Analyze a component for possible memory waste and other inefficiencies.")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/component_report.html")
public class ComponentReportQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SectionSpec componentReport = new SectionSpec(MessageFormat.format("Component Report {0}", objects.getLabel()));

        Ticks ticks = new Ticks(listener, componentReport.getName(), 31);

        // calculate retained set
        int[] retained = calculateRetainedSize(ticks);

        ticks.tick();

        Histogram histogram = snapshot.getHistogram(retained, ticks);
        ticks.tick();

        long totalSize = snapshot.getHeapSize(retained);
        ticks.tick();

        addOverview(componentReport, totalSize, retained, histogram, ticks);

        SectionSpec possibleWaste = new SectionSpec("Possible Memory Waste");
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

        SectionSpec miscellaneous = new SectionSpec("Miscellaneous");
        componentReport.add(miscellaneous);

        try
        {
            addSoftReferenceStatistic(miscellaneous, histogram, ticks);
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
            addHashMapsCollisionRatios(miscellaneous, histogram, ticks);
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

    private int[] calculateRetainedSize(Ticks ticks) throws SnapshotException
    {
        int[] retained = null;

        List<ExcludedReferencesDescriptor> excludes = new ArrayList<ExcludedReferencesDescriptor>();

        addExcludes(excludes, "java.lang.ref.Finalizer", "referent");
        addExcludes(excludes, "java.lang.ref.WeakReference", "referent");
        addExcludes(excludes, "java.lang.ref.SoftReference", "referent");

        if (excludes.isEmpty())
        {
            retained = snapshot.getRetainedSet(objects.getIds(ticks), ticks);
        }
        else
        {
            retained = snapshot.getRetainedSet(objects.getIds(ticks), //
                            excludes.toArray(new ExcludedReferencesDescriptor[0]), //
                            ticks);
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
        SectionSpec overview = new SectionSpec("Overview");
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
        buf.append("Size: <strong>").append(Units.Storage.of(totalSize).format(totalSize)).append("</strong>");
        buf.append(" Classes: <strong>").append(Units.Plain.of(noOfClasses).format(noOfClasses)).append("</strong>");
        buf.append(" Objects: <strong>").append(Units.Plain.of(noOfObjects).format(noOfObjects)).append("</strong>");
        buf.append(" Class Loader: <strong>").append(Units.Plain.of(noOfClassLoaders).format(noOfClassLoaders)).append(
                        "</strong>");
        QuerySpec spec = new QuerySpec("Details", new TextResult(buf.toString(), true));
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addOverviewPie(SectionSpec overview, long totalSize)
    {
        PieFactory pie = new PieFactory(snapshot);
        pie.addSlice(-1, objects.getLabel(), totalSize, totalSize);
        QuerySpec spec = new QuerySpec("Distribution", pie.build());
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addTopConsumer(SectionSpec componentReport, int[] retained, IProgressListener listener)
                    throws Exception
    {
        IResult result = SnapshotQuery.lookup("top_consumers_html", snapshot) //
                        .set("objects", retained) //
                        .execute(listener);

        QuerySpec topConsumers = new QuerySpec("Top Consumers");
        topConsumers.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        topConsumers.set(Params.Html.COLLAPSED, Boolean.FALSE.toString());
        topConsumers.setResult(result);
        componentReport.add(topConsumers);
    }

    private void addRetainedSet(SectionSpec componentReport, Histogram histogram)
    {
        QuerySpec retainedSet = new QuerySpec("Retained Set");
        retainedSet.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        retainedSet.setResult(histogram);
        componentReport.add(retainedSet);
    }

    // //////////////////////////////////////////////////////////////
    // duplicate strings
    // //////////////////////////////////////////////////////////////

    private void addDuplicateStrings(SectionSpec componentReport, Histogram histogram, Ticks listener) throws Exception
    {
        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (!"char[]".equals(record.getLabel()))
                continue;

            int[] objectIds = record.getObjectIds();
            if (objectIds.length > 100000)
            {
                int[] copy = new int[100000];
                System.arraycopy(objectIds, 0, copy, 0, copy.length);
                objectIds = copy;
            }

            RefinedResultBuilder builder = SnapshotQuery.lookup("group_by_value", snapshot) //
                            .set("objects", objectIds) //
                            .refine(listener);

            builder.setFilter(1, ">=10");
            builder.setInlineRetainedSizeCalculation(true);
            RefinedTable table = (RefinedTable) builder.build();
            TotalsRow totals = new TotalsRow();
            table.calculateTotals(table.getRows(), totals, listener);

            StringBuilder comment = new StringBuilder();
            comment.append("Found ");
            comment.append(NumberFormat.getInstance().format(table.getRowCount()));
            comment.append(" occurrences of char[] with at least 10 instances having identical content.");
            comment.append(" Total size is ").append(totals.getLabel(2)).append(" bytes.");
            comment.append("<p>Top elements include:<ul>");

            for (int rowId = 0; rowId < table.getRowCount() && rowId < 5; rowId++)
            {
                Object row = table.getRow(rowId);
                String value = table.getFormattedColumnValue(row, 0);
                if (value.length() > 50)
                    value = value.substring(0, 50) + "...";

                String size = table.getFormattedColumnValue(row, 3);

                comment.append("<li>").append(table.getFormattedColumnValue(row, 1));
                comment.append(" x <strong>").append(value).append("</strong> (");
                comment.append(size).append(" bytes)  </li>");
            }
            comment.append("</ul>");

            // build result
            SectionSpec duplicateStrings = new SectionSpec("Duplicate Strings");
            componentReport.add(duplicateStrings);

            QuerySpec spec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
            spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
            duplicateStrings.add(spec);

            spec = new QuerySpec("Histogram");
            spec.setResult(table);
            duplicateStrings.add(spec);

            listener.tick();

            break;
        }
    }

    // //////////////////////////////////////////////////////////////
    // collection usage
    // //////////////////////////////////////////////////////////////

    private void addEmptyCollections(SectionSpec componentReport, long totalSize, Histogram histogram, Ticks listener)
                    throws Exception
    {
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec("Empty Collections");

        StringBuilder comment = new StringBuilder();
        SectionSpec collectionbySizeSpec = new SectionSpec("Details");
        collectionbySizeSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot))
        {
            if (!info.hasSize())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (metadata.containsKey(record.getClassId()))
            {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collections_grouped_by_size", snapshot) //
                                .set("objects", record.getObjectIds()) //
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
                                comment.append("Detected the following empty collections:<ul>");

                            comment.append("<li>").append(NumberFormat.getInstance().format(numberOfObjects)) //
                                            .append(" instances of <strong>").append(clazz.getName()) //
                                            .append("</strong> retain <strong>") //
                                            .append(retainedSize).append("</strong> bytes.</li>");
                        }

                        break;
                    }
                }

                QuerySpec bySizeSpec = new QuerySpec(clazz.getName());
                bySizeSpec.setResult(refinedTable);
                collectionbySizeSpec.add(bySizeSpec);

                listener.tick();
            }
        }

        if (collectionbySizeSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append("No excessive usage of empty collections found.");
        else
            comment.append("</ul>");

        QuerySpec spec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
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
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec("Collection Fill Ratios");

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec("Details");
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot))
        {
            if (!info.hasSize() || !info.hasBackingArray())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (metadata.containsKey(record.getClassId()))
            {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collection_fill_ratio", snapshot) //
                                .set("objects", record.getObjectIds()) //
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
                                comment.append("Detected the following collections with fill ratios below 20%:<ul>");

                            comment.append("<li>").append(NumberFormat.getInstance().format(numberOfObjects)) //
                                            .append(" instances of <strong>").append(clazz.getName()) //
                                            .append("</strong> retain <strong>") //
                                            .append(retainedSize).append("</strong> bytes.</li>");
                        }

                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                detailsSpec.add(spec);

                listener.tick();
            }
        }

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append("No serious amount of collections with low fill ratios found.");
        else
            comment.append("</ul>");

        QuerySpec commentSpec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // hash map collision ratios
    // //////////////////////////////////////////////////////////////

    private void addHashMapsCollisionRatios(SectionSpec componentReport, Histogram histogram, Ticks listener)
                    throws Exception
    {
        SectionSpec overview = new SectionSpec("Map Collision Ratios");

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec("Details");
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = CollectionUtil.getKnownMaps(snapshot);

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords())
        {
            if (metadata.containsKey(record.getClassId()))
            {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                int[] objectIds = record.getObjectIds();
                if (objectIds.length > 20000)
                {
                    int[] copy = new int[20000];
                    System.arraycopy(objectIds, 0, copy, 0, copy.length);
                    objectIds = copy;
                }

                RefinedResultBuilder builder = SnapshotQuery.lookup("map_collision_ratio", snapshot) //
                                .set("objects", objectIds) //
                                .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count; rowId++)
                {
                    Object row = refinedTable.getRow(rowId);
                    double collisionRato = (Double) refinedTable.getColumnValue(row, 0);

                    if (collisionRato > 0.79d)
                    {
                        int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                        String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                        if (comment.length() == 0)
                            comment.append("Detected the following maps with collision ratios above 80%:<ul>");

                        comment.append("<li>").append(NumberFormat.getInstance().format(numberOfObjects)) //
                                        .append(" instances of <strong>").append(clazz.getName()) //
                                        .append("</strong> retain <strong>") //
                                        .append(retainedSize).append("</strong> bytes.</li>");
                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                detailsSpec.add(spec);

                listener.tick();
            }
        }

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append("No maps found with collision ratios greater than 80%.");
        else
            comment.append("</ul>");

        QuerySpec commentSpec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // soft reference statistics
    // //////////////////////////////////////////////////////////////

    private void addSoftReferenceStatistic(SectionSpec componentReport, Histogram histogram, Ticks ticks)
                    throws SnapshotException
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.SoftReference", true);
        if (classes == null)
        {
            addEmptyResult(componentReport, "Soft Reference Statistics", "Heap dumps contains no soft references.");
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
            if (softRefClassIds.contains(record.getClassId()))
            {
                softRefs.add(record);
                numObjects += record.getNumberOfObjects();
                heapSize += record.getUsedHeapSize();

                instanceSet.addAll(record.getObjectIds());

                for (int objectId : record.getObjectIds())
                {
                    IInstance obj = (IInstance) snapshot.getObject(objectId);

                    ObjectReference ref = (ObjectReference) obj.getField("referent").getValue();
                    if (ref != null)
                        referentSet.add(ref.getObjectId());
                }
            }
        }

        if (instanceSet.isEmpty())
        {
            addEmptyResult(componentReport, "Soft Reference Statistics",
                            "Component does not keep Soft References alive.");
            return;
        }

        Histogram softRefHistogram = new Histogram("Histogram of Soft References", softRefs, null, numObjects,
                        heapSize, 0);

        CompositeResult referents = ReferenceQuery.execute("softly", instanceSet, referentSet, snapshot, ticks);

        StringBuilder comment = new StringBuilder();
        comment.append(MessageFormat.format("A total of {0} java.lang.ref.SoftReference "
                        + "object{0,choice,0#s|1#|2#s} have been found, "
                        + "which softly reference {1,choice,0#no objects|1#one object|2#{1,number} objects}.<br/>",
                        instanceSet.size(), referentSet.size()));

        Histogram onlySoftlyReachable = (Histogram) referents.getResultEntries().get(1).getResult();
        numObjects = 0;
        heapSize = 0;
        for (ClassHistogramRecord r : onlySoftlyReachable.getClassHistogramRecords())
        {
            numObjects += r.getNumberOfObjects();
            heapSize += r.getUsedHeapSize();
        }
        comment.append(MessageFormat.format("{0,choice,0#none object|1#one object|2#{0,number} objects} totaling {1} "
                        + "are retained (kept alive) only via soft references.", //
                        numObjects, //
                        Units.Storage.of(heapSize).format(heapSize)));

        SectionSpec overview = new SectionSpec("Soft Reference Statistics");
        QuerySpec commentSpec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        QuerySpec child = new QuerySpec(softRefHistogram.getLabel(), softRefHistogram);
        child.set(Params.Rendering.DERIVED_DATA_COLUMN, "_default_=" + RetainedSizeDerivedData.APPROXIMATE.getCode());
        overview.add(child);

        for (CompositeResult.Entry entry : referents.getResultEntries())
            overview.add(new QuerySpec(entry.getName(), entry.getResult()));

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // finalizer statistics
    // //////////////////////////////////////////////////////////////

    private void addFinalizerStatistic(SectionSpec componentReport, int[] retained, Ticks ticks)
                    throws SnapshotException
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.Finalizer", true);
        if (classes == null)
        {
            addEmptyResult(componentReport, "Finalizer Statistics",
                            "Heap dumps contains no java.lang.ref.Finalizer objects.");
            return;
        }

        SetInt retainedSet = new SetInt((retained.length / 100) * 110);
        for (int ii = 0; ii < retained.length; ii++)
            retainedSet.add(retained[ii]);

        ArrayInt finalizers = new ArrayInt();

        for (IClass c : classes)
        {
            for (int objectId : c.getObjectIds())
            {
                IInstance obj = (IInstance) snapshot.getObject(objectId);

                ObjectReference ref = (ObjectReference) obj.getField("referent").getValue();
                if (ref != null)
                {
                    int referentId = ref.getObjectId();
                    if (retainedSet.contains(referentId))
                    {
                        finalizers.add(referentId);
                    }
                }
            }
        }

        if (finalizers.isEmpty())
        {
            addEmptyResult(componentReport, "Finalizer Statistics",
                            "Component does not keep object with Finalizer methods alive.");
            return;
        }

        SectionSpec overview = new SectionSpec("Finalizer Statistics");
        StringBuilder comment = new StringBuilder();
        comment.append(MessageFormat.format("A total of {0} object{0,choice,0#s|1#|2#s} "
                        + "implement the finalize method.", finalizers.size()));

        QuerySpec commentSpec = new QuerySpec("Comment", new TextResult(comment.toString(), true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        Histogram histogram = snapshot.getHistogram(finalizers.toArray(), ticks);
        histogram.setLabel("Histogram of Objects with Finalize Method");
        overview.add(new QuerySpec(histogram.getLabel(), histogram));

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // internal utilitiy methods
    // //////////////////////////////////////////////////////////////

    private void addEmptyResult(SectionSpec report, String sectionLabel, String message)
    {
        SectionSpec section = new SectionSpec(sectionLabel);
        QuerySpec commentSpec = new QuerySpec("Comment", new TextResult(message, true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        section.add(commentSpec);
        report.add(section);
    }
}
