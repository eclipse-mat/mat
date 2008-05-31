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
package org.eclipse.mat.impl.result;

import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.VoidProgressListener;

public class RefinedHistogramTable extends RefinedTable
{

    @Override
    protected RetainedSizeCalculator calculatorFor(ContextProvider provider)
    {
        try
        {
            RetainedSizeCalculator.AllClasses calculator = new RetainedSizeCalculator.AllClasses();

            // fill in pre-calculated values
            Histogram histogram = (Histogram) subject;
            for (ClassHistogramRecord r : histogram.getClassHistogramRecords())
                r.calculateRetainedSize(snapshot, false, true, new VoidProgressListener());

            return calculator;
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }

    }
}
