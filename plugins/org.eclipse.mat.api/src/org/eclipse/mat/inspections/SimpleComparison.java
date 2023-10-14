/*******************************************************************************
 * Copyright (c) 2020, 2023 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.SimpleMonitor;

/**
 * A simple comparison of the results of running a query on two
 * different snapshots.
 */
@Icon("/META-INF/icons/compare.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/comparingdata.html")
public class SimpleComparison implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot baseline;

    @Argument
    public String query;

    @Argument(isMandatory = false)
    public String options;

    @Argument(isMandatory = false)
    public String defaultoptions = "-mask \"" //$NON-NLS-1$
                    + complexPattern
                    + "\" -x java.util.HashMap$Node:key java.util.Hashtable$Entry:key java.util.WeakHashMap$Entry:referent java.util.concurrent.ConcurrentHashMap$Node:key"; //$NON-NLS-1$

    private static final String addressPattern = "\\s@ 0x[0-9a-f]+"; //$NON-NLS-1$
    private static final String arrayPattern = "^(\\[[0-9]+\\], ){0,100}\\[[0-9]+\\](,\\.\\.\\.)?$"; //$NON-NLS-1$
    private static final String arraySizePattern = "(?<=\\p{javaJavaIdentifierPart}\\[)\\d+(?=\\])"; //$NON-NLS-1$
    /** Regex pattern for masking. Compiles the pattern to check it */ 
    private static final String complexPattern = Pattern.compile(addressPattern + "|" + arrayPattern + "|" + arraySizePattern).pattern(); //$NON-NLS-1$ //$NON-NLS-2$

    public enum Retained {
        APPROXIMATE,
        PRECISE
    };

    @Argument(isMandatory = false)
    public Retained retained;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SimpleMonitor monitor = new SimpleMonitor(Messages.SimpleComparison_ProgressName, listener, new int[] {100,100,300});
        IStructuredResult baseTable = callQuery(monitor.nextMonitor(), baseline);
        IStructuredResult currTable = callQuery(monitor.nextMonitor(), snapshot);

        String queryId = "comparetablesquery"; //$NON-NLS-1$
        if (defaultoptions != null && defaultoptions.length() > 0)
            queryId += " " + defaultoptions; //$NON-NLS-1$
        if (options != null && options.length() > 0)
            queryId += " " + options; //$NON-NLS-1$

        SnapshotQuery queryc = SnapshotQuery.parse(queryId, snapshot);

        List<IStructuredResult> r = new ArrayList<IStructuredResult>();
        r.add(baseTable);
        r.add(currTable);
        queryc.setArgument("tables", r); //$NON-NLS-1$
        ArrayList<ISnapshot> snapshots = new ArrayList<ISnapshot>();
        snapshots.add(baseline);
        snapshots.add(snapshot);
        queryc.setArgument("snapshots", snapshots); //$NON-NLS-1$
        IResult ret = queryc.execute(monitor.nextMonitor());
        listener.done();
        return ret;
    }

    private IStructuredResult callQuery(IProgressListener listener, ISnapshot snapshot) throws Exception
    {
        if (retained != null)
        {
            RefinedResultBuilder rb1 = SnapshotQuery.parse(query, snapshot).refine(listener);
            rb1.setInlineRetainedSizeCalculation(true);
            rb1.addDefaultContextDerivedColumn(retained == Retained.PRECISE ? RetainedSizeDerivedData.PRECISE : RetainedSizeDerivedData.APPROXIMATE);
            return rb1.build();
        }
        else
        {
            return (IStructuredResult) SnapshotQuery.parse(query, snapshot)
                            .execute(listener);
        }
    }
}
