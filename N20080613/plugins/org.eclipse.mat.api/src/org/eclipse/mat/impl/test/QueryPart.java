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
package org.eclipse.mat.impl.test;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.impl.query.CommandLine;
import org.eclipse.mat.impl.result.RefinedStructuredResult;
import org.eclipse.mat.impl.result.RefinedTable;
import org.eclipse.mat.impl.result.RefinedTree;
import org.eclipse.mat.impl.test.ResultRenderer.RenderingInfo;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.HistogramResult;
import org.eclipse.mat.query.results.ObjectListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.test.ITestResult;
import org.eclipse.mat.test.Params;
import org.eclipse.mat.test.QuerySpec;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.test.Spec;
import org.eclipse.mat.test.ITestResult.Status;
import org.eclipse.mat.util.IProgressListener;

public class QueryPart extends AbstractPart
{
    public QueryPart(SectionPart parent, QuerySpec spec)
    {
        super(parent, spec);
    }

    @Override
    public QuerySpec spec()
    {
        return (QuerySpec) super.spec();
    }

    public String getCommand()
    {
        // expand variables in command
        return params().expand(spec().getCommand());
    }

    @Override
    public Status execute(ISnapshot snapshot, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        String sectionName = parent != null ? parent.spec().getName() : "none";
        listener.subTask(MessageFormat.format("Processing test ''{0}'' of section ''{1}''", spec().getName(),
                        sectionName));

        IResult result = spec().getResult();

        if (result == null)
        {
            if (getCommand() == null)
            {
                String msg = MessageFormat.format("No command specified for test ''{0}'' of section ''{1}''", //
                                spec().getName(), sectionName);
                Logger.getLogger(getClass().getName()).log(Level.WARNING, msg);
            }
            else
            {
                try
                {
                    result = CommandLine.execute(snapshot, getCommand(), listener);
                }
                catch (Exception e)
                {
                    Logger.getLogger(getClass().getName()).log(
                                    Level.WARNING,
                                    MessageFormat.format("Ignoring result of ''{0}'' due to {1}", spec().getName(), e
                                                    .getMessage()), e);
                    return status;
                }
            }
        }

        // create a spec (to be merged) for the composite result
        if (result instanceof CompositeResult)
            result = buildFor((CompositeResult) result);

        // merge specs into the current part hierarchy (and remove yourself)
        if (result instanceof Spec)
        {
            Spec replacement = (Spec) result;

            // overwrite all parameters explicitly given
            replacement.putAll(spec().getParams());
            replacement.setName(spec().getName());

            AbstractPart part = AbstractPart.build(getParent(), replacement);
            getParent().replace(this, part);
            return part.execute(snapshot, renderer, listener);
        }

        // set status if test discloses one
        if (result instanceof ITestResult)
            status = ((ITestResult) result).getStatus();

        // convert any non-standard (i.e. non table oder trees) into the
        // standard ones
        if (result instanceof ObjectListResult)
        {
            result = ((ObjectListResult) result).asTree(snapshot);
        }
        else if (result instanceof HistogramResult)
        {
            result = ((HistogramResult) result).asTable();
        }

        // read and process parameters for tree and table and pass 'em on to the
        // renderer
        if (result instanceof IResultTable)
        {
            RefinedTable.Builder builder = new RefinedTable.Builder(snapshot, (IResultTable) result);
            readParamsAndProcess(renderer, result, builder);
        }
        else if (result instanceof IResultTree)
        {
            RefinedTree.Builder builder = new RefinedTree.Builder(snapshot, (IResultTree) result);
            readParamsAndProcess(renderer, result, builder);
        }
        else if (result instanceof IResultPie && Platform.getBundle("org.eclipse.mat.chart.ui") == null)
        {
            // do not render the result
        }
        else
        {
            renderer.process(this, result, null);
        }

        return status;
    }

    // //////////////////////////////////////////////////////////////
    // build specs for an existing composite result
    // //////////////////////////////////////////////////////////////

    private Spec buildFor(CompositeResult result)
    {
        String name = result.getName() != null ? result.getName() : spec().getName();
        SectionSpec spec = new SectionSpec(name);
        spec.setStatus(result.getStatus());

        String pattern = params().shallow().get(Params.Rendering.PATTERN);
        boolean isOverviewDetailsPattern = Params.Rendering.PATTERN_OVERVIEW_DETAILS.equals(pattern);

        int index = 1;
        for (CompositeResult.Entry entry : result.getResultEntries())
        {
            QuerySpec q = new QuerySpec(entry.getName() != null ? entry.getName() : spec().getName() + " " + index);
            q.setResult(entry.getResult());

            if (index == 1 && isOverviewDetailsPattern)
            {
                q.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
                q.set(Params.Html.IS_IMPORTANT, params().get(Params.Html.IS_IMPORTANT));
            }

            spec.add(q);

            index++;
        }

        return spec;
    }

    // //////////////////////////////////////////////////////////////
    // read parameters and inject into refined result builder
    // //////////////////////////////////////////////////////////////

    private void readParamsAndProcess(ResultRenderer renderer, IResult result, RefinedStructuredResult.Builder builder)
                    throws IOException
    {
        builder.setInlineRetainedSizeCalculation(true);

        addRetainedSizeColumns(builder, result);
        addSortOrder(builder);
        addFilter(builder);

        RenderingInfo rInfo = new RenderingInfo(builder.getColumns().size());

        addHiding(builder, rInfo);
        addHardLimit(rInfo);
        addShowTotals(rInfo);

        renderer.process(this, builder.build(), rInfo);
    }

    private void addShowTotals(RenderingInfo rInfo)
    {
        rInfo.showTotals = params().getBoolean(Params.Html.SHOW_TOTALS, true);

    }

    private void addHiding(RefinedStructuredResult.Builder builder, RenderingInfo rInfo)
    {
        String[] hidden = params().getStringArray(Params.Rendering.HIDE_COLUMN);
        if (hidden == null || hidden.length == 0)
            return;

        for (String column : hidden)
        {
            int columnIndex = builder.getColumnIndexByName(column);
            if (columnIndex < 0)
            {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                MessageFormat.format("Column not found: {0}", column));
            }
            else
            {
                rInfo.visibleColumns[columnIndex] = false;
            }
        }
    }

    private void addHardLimit(RenderingInfo info)
    {
        info.limit = params().getInt(Params.Rendering.LIMIT, 25);
    }

    /** format: filter1=criteria,filter2=criteria */
    private void addFilter(RefinedStructuredResult.Builder builder)
    {
        String[] filters = params().getStringArray(Params.Rendering.FILTER);
        if (filters == null || filters.length == 0)
            return;

        for (String filter : filters)
        {
            int p = filter.indexOf('=');
            if (p < 0)
            {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                MessageFormat.format("Missing ''='' sign in filter ''{0}''", filter));
            }
            else
            {
                int columnIndex = builder.getColumnIndexByName(filter.substring(0, p));
                if (columnIndex < 0)
                {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                    MessageFormat.format("Column not found: {0}", filter.substring(0, p)));
                }
                else
                {
                    try
                    {
                        builder.setFilter(columnIndex, filter.substring(p + 1));
                    }
                    catch (IllegalArgumentException e)
                    {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                        MessageFormat.format("Error in filter: {0}", filter.substring(p + 1)), e);
                    }
                }
            }
        }
    }

    /** format: contextprovider1,contextprovider2,_default_ */
    private void addRetainedSizeColumns(RefinedStructuredResult.Builder builder, IResult result)
    {
        String[] providers = params().getStringArray(Params.Rendering.CALCULATE_RETAINED_SIZE_FOR);
        if (providers == null || providers.length == 0)
            return;

        ResultMetaData metaData = result.getResultMetaData();

        boolean approximate = params().getBoolean(Params.Rendering.APPROXIMATE_RETAINED_SIZE, true);

        for (String provider : providers)
        {
            boolean added = false;

            if ("_default_".equals(provider))
            {
                builder.addDefaultRetainedSizeColumn(approximate);
                added = true;
            }
            else
            {
                if (metaData != null)
                {
                    for (ContextProvider p : metaData.getContextProviders())
                    {
                        if (provider.equals(p.getLabel()))
                        {
                            builder.addRetainedSizeColumn(p, approximate);
                            added = true;
                            break;
                        }
                    }
                }
            }

            if (!added)
            {
                String msg = "Error added retained size column for ''{0}'' - no context provider found.";
                Logger.getLogger(QueryPart.class.getName()).log(Level.WARNING, MessageFormat.format(msg, provider));
            }

        }
    }

    private void addSortOrder(RefinedStructuredResult.Builder builder)
    {
        String sortColumn = params().get(Params.Rendering.SORT_COLUMN);
        if (sortColumn != null)
        {
            int columnIndex = builder.getColumnIndexByName(sortColumn);
            if (columnIndex < 0)
            {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                MessageFormat.format("Column not found: {0}", sortColumn));
            }
            else
            {
                Column.SortDirection direction = null;

                String sortOrder = params.get(Params.Rendering.SORT_ORDER);
                if (sortOrder != null)
                    direction = Column.SortDirection.valueOf(sortOrder);

                builder.setSortOrder(columnIndex, direction);
            }
        }
    }
}
