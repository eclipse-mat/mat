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

import java.text.NumberFormat;
import java.util.Collection;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.collections.CollectionUtil;
import org.eclipse.mat.inspections.util.PieFactory;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.Units;

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
        SectionSpec componentReport = new SectionSpec("Component Report " + objects.getLabel());

        Ticks ticks = new Ticks(listener, "Component Report " + objects.getLabel(), 30);

        int retained[] = snapshot.getRetainedSet(objects.getIds(ticks), ticks);
        ticks.tick();

        Histogram histogram = snapshot.getHistogram(retained, ticks);
        ticks.tick();

        long totalSize = snapshot.getHeapSize(retained);
        ticks.tick();

        addOverview(componentReport, totalSize, retained, histogram, ticks);

        addDuplicateStrings(componentReport, histogram, ticks);

        addEmptyCollections(componentReport, totalSize, histogram, ticks);

        addCollectionFillRatios(componentReport, totalSize, histogram, ticks);

        addHashMapsCollisionRatios(componentReport, totalSize, histogram, ticks);

        ticks.delegate.done();

        return componentReport;
    }

    class Ticks implements IProgressListener
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

        SectionSpec overview = new SectionSpec("Collection Usage");

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

    private void addHashMapsCollisionRatios(SectionSpec componentReport, long totalSize, Histogram histogram,
                    Ticks listener) throws Exception
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

}
