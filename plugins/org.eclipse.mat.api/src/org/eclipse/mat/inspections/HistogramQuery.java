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

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@Name("Show As Histogram")
@CommandName("histogram")
@Category("Java Basics")
@Icon("/META-INF/icons/show_histogram.gif")
@Help("Create a histogram from an arbitrary set of objects.\n\n"
                + "If you need the retained set as an histogram, use the retained_set query.")
public class HistogramQuery implements IQuery
{
    public enum Grouping
    {
        BY_CLASS("Group by class", Icons.CLASS), //
        BY_CLASSLOADER("Group by class loader", Icons.CLASSLOADER_INSTANCE), //
        BY_PACKAGE("Group by package", Icons.PACKAGE);

        String label;
        URL icon;

        private Grouping(String label, URL icon)
        {
            this.label = label;
            this.icon = icon;
        }

        public URL getIcon()
        {
            return icon;
        }

        public String toString()
        {
            return label;
        }
    }

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none", isMandatory = false)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    @Help("Deprecated. Use groupBy instead.")
    @Deprecated
    public boolean byClassLoader = false;

    @Argument(isMandatory = false)
    public Grouping groupBy = Grouping.BY_CLASS;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Histogram histogram = objects == null ? snapshot.getHistogram(listener) //
                        : snapshot.getHistogram(objects.getIds(listener), listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        if (objects != null)
            histogram.setLabel(MessageUtil.format("Histogram of {0}", objects.getLabel()));

        if (byClassLoader)
            groupBy = Grouping.BY_CLASSLOADER;

        switch (groupBy)
        {
            case BY_CLASS:
                return histogram;
            case BY_CLASSLOADER:
                return histogram.groupByClassLoader();
            case BY_PACKAGE:
                return histogram.groupByPackage();
            default:
                throw new RuntimeException(MessageUtil.format("Illegal groupBy argument: {0}", groupBy));
        }
    }

}
