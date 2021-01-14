/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson/IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.SimpleMonitor;

@CommandName("delta_histogram")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/delta_histogram.gif")
public class DeltaHistogram extends HistogramQuery
{
    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot snapshot2;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int parts[] = new int[] {40,40,20};
        SimpleMonitor sm = new SimpleMonitor(Messages.DeltaHistogram_Progress, listener, parts);
        Histogram h1 = (Histogram) super.execute(sm.nextMonitor());
        snapshot = snapshot2;
        Histogram h2 = (Histogram) super.execute(sm.nextMonitor());
        sm.nextMonitor();
        Histogram h3 = h2.diffWithBaseline(h1);
        // Currently it seems a SECONDARY_SNAPSHOT is not disposed by the caller.
        SnapshotFactory.dispose(snapshot2);
        listener.done();
        return h3;
    }
}
