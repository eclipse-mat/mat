/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.Collections;
import java.util.List;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@CommandName("comparison_report")
@Icon("/META-INF/icons/compare.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/comparingdata.html")
public class ComparisonReport implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot baseline;

    @Argument(flag = Argument.UNFLAGGED)
    public String report = "org.eclipse.mat.api:suspects2"; //$NON-NLS-1$

    public IResult execute(IProgressListener listener) throws Exception
    {
        SnapshotQuery queryc = SnapshotQuery.parse("default_report "+report, snapshot); //$NON-NLS-1$
        List<String> params = Collections.singletonList("baseline="+baseline.getSnapshotInfo().getPath()); //$NON-NLS-1$
        queryc.setArgument("params", params); //$NON-NLS-1$
        IResult ret = queryc.execute(listener);
        return ret;
    }
}
