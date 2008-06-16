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
package org.eclipse.mat.inspections.query;

import java.text.MessageFormat;

import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;


@Name("Show Retained Set")
@Icon("/META-INF/icons/show_retained_set.gif")
@Help("Calculate the retained set of an arbitrary set of objects.\n\n"
                + "Optionally one can provide a list field names. If this parameter is specified, "
                + "instead of assuming that the whole instance is not available, just the references "
                + "with the specified name are considered non-existing.")
public class RetainedSetQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false, flag = "f")
    @Help("List of field names")
    public String[] fieldNames;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] retainedSet;

        if (fieldNames == null)
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), listener);
        else
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), fieldNames, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        
        Histogram histogram = snapshot.getHistogram(retainedSet, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(MessageFormat.format("Retained by ''{0}''", new Object[] { objects.getLabel() }));
        return histogram;
    }

}
