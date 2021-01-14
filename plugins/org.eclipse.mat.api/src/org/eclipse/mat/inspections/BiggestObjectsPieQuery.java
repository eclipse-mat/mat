/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.util.IProgressListener;

@CommandName("pie_biggest_objects")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/pie_chart.gif")
public class BiggestObjectsPieQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResultPie execute(IProgressListener listener) throws Exception
    {
        int[] objects = snapshot.getImmediateDominatedIds(-1);

        final long totalHeapSize = snapshot.getSnapshotInfo().getUsedHeapSize();

        int index = 0;
        int count = 0;
        long retainedHeapBySlices = 0;

        PieFactory pie = new PieFactory(snapshot);

        while (index < objects.length //
                        && (count < 3 //
                        || (retainedHeapBySlices < totalHeapSize / 4 && count < 10)))
        {
            Slice slice = pie.addSlice(objects[index++]);
            retainedHeapBySlices += slice.getValue();
            count++;

            if (slice.getValue() < totalHeapSize / 100.0)
                break;

            if (listener.isCanceled())
                break;
        }

        return pie.build();
    }
}
