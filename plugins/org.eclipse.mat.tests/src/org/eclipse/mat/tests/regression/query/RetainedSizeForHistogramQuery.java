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
package org.eclipse.mat.tests.regression.query;

import java.util.Collection;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@Name("Retained Size for Histogram")
@CommandName("retained_size_histogram")
@Category(Category.HIDDEN)
public class RetainedSizeForHistogramQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public boolean byClassLoader = false;

    @Argument(isMandatory = false)
    public boolean approx = false;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Histogram histogram = (Histogram) SnapshotQuery.lookup("histogram", snapshot) //
                        .execute(listener);
        int counter = 0;
        if (byClassLoader)
        {
            Collection<ClassLoaderHistogramRecord> classloaderRecords = histogram.getClassLoaderHistogramRecords();
            for (ClassLoaderHistogramRecord classLoaderHistogramRecord : classloaderRecords)
            {
                classLoaderHistogramRecord.calculateRetainedSize(snapshot, false, approx, listener);
                if (!approx)
                {
                    counter++;
                    if (counter == 10)// calculate precise retained sizes only
                                      // for first 10 records
                        break;
                }
            }
        }
        else
        {
            Collection<ClassHistogramRecord> classRecords = histogram.getClassHistogramRecords();
            for (ClassHistogramRecord classHistogramRecord : classRecords)
            {
                classHistogramRecord.calculateRetainedSize(snapshot, false, approx, listener);
                if (!approx)
                {
                    counter++;
                    if (counter == 10)// calculate precise retained sizes only
                                      // for first 10 records
                        break;
                }
            }
        }
        return histogram;
    }

}
