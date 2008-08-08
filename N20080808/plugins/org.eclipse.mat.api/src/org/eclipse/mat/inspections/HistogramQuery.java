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

import java.text.MessageFormat;

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
import org.eclipse.mat.util.IProgressListener;


@Name("Show As Histogram")
@CommandName("histogram")
@Category("Java Basics")
@Icon("/META-INF/icons/show_histogram.gif")
@Help("Create a histogram from an arbitrary set of objects.\n\n"
                + "If you need the retained set as an histogram, use the retained_set query.")
public class HistogramQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none", isMandatory = false)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public boolean byClassLoader = false;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Histogram histogram = objects == null ? snapshot.getHistogram(listener) //
                        : snapshot.getHistogram(objects.getIds(listener), listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        if (objects != null)
            histogram.setLabel(MessageFormat.format("Histogram of {0}", new Object[] { objects.getLabel() }));

        return byClassLoader ? histogram.groupByClassLoader() : histogram;
    }

}
