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

import java.util.Locale;

import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

/**
 * Used to make it easier to track progress with an {@link IProgressListener}
 * when going through an {@link IHeapObjectArgument#iterator()}.
 * Measuring the size in advance could be expensive if the argument
 * is an OQL query, so we can approximate the work to be done as
 * each int[] is read.
<pre>{@code
HeapObjectsTracker hot = new HeapObjectsTracker(objects);
listener.beginTask("My long task", hot.totalWork());
for (Iterator <int[]> it = objects.iterator(); it.hasNext();)
{
    int objectIds[] = it.next();
    hot.beginBlock(objectIds, !it.hasNext());
    for (int id : objectIds)
    {
        listener.worked(hot.work());
    }
    listener.worked(hot.endBlock());
}
listener.done();
}</pre>
 */
class HeapObjectsTracker
{
    /** Actual total number of array entries + arrays */
    long actual;
    /** Total work for {@link IProgressListener#beginTask} */
    int totalWork;
    /** Work indicated to {@link IProgressListener#worked} for previous blocks */
    int workPrev;
    /** Current cumulative work indicated to {@link IProgressListener#worked} */
    int workDone;
    /** Which array has been processed (1-based) */
    int j;
    /** Total objects seen so far */
    long t;
    /** Current object index from the block */
    int k;
    /** Number of objects in the block (+1 for the block) */
    int n;
    /** Estimate of number of all remaining objects including in this block */
    long est;

    /**
     * Construct the tracker from a {@link IHeapObjectArgument} supplied to
     * a query.
     * @param objects
     */
    public HeapObjectsTracker(IHeapObjectArgument objects)
    {
        if (objects.getLabel().toUpperCase(Locale.ENGLISH).contains("SELECT ")) //$NON-NLS-1$
        {
            /* 
             * This could be expensive to evaluate twice, so use the
             * estimation for work done.
             */
            totalWork = 1000000;
            actual = -1;
        }
        else
        {
            long total = 0;
            for (int[] objectIds : objects)
            {
                // +1 for the block
                total += objectIds.length + 1;
            }
            actual = total;
            if (total < 1000000)
                totalWork = (int)total;
            else
                totalWork = 1000000;
        }
    }

    /**
     * Gets the total work for the progress listener
     * @return the amount of work for {@link IProgressListener#beginTask(String, int)}
     */
    public int totalWork()
    {
        return totalWork;
    }

    /**
     * Starts progress tracker for an array of ints from the iterator
     * {@link {@link IHeapObjectArgument#iterator()}
     * @param objectIds The object ids
     * @param last whether this is the last block or not
     */
    public void beginBlock(int[] objectIds, boolean last)
    {
        /* In case endBlock() not called */
        t += n;
        // Current iteration, +1 for the block
        n = objectIds.length + 1;
        ++j;
        k = 0;
        workPrev = workDone;
        if (actual >= 0)
        {
            est = actual - t;
        }
        else
        {
            if (last)
            {
                est = n;
            }
            else
            {
                est = (t + n) * (j + 2) / j;
            }
        }
    }

    /**
     * Processed one heap item, so see how much work that was.
     * @return the amount for {@link IProgressListener#worked}
     */
    public int work()
    {
        ++k;
        int work = (int)((long)k * (totalWork - workPrev) / est);
        int delta = work - workDone;
        workDone = work;
        return delta;
    }

    /**
     * Calculates the work done at the end of processing
     * an array of ints from the iterator
     * {@link {@link IHeapObjectArgument#iterator()}
     * @return the amount for {@link IProgressListener#worked}
     */
    public int endBlock()
    {
        int delta;
        if (k < n)
        {
            int work = (int)((long)n * (totalWork - workPrev) / est);
            delta = work - workDone;
            workDone = work;
        }
        else
        {
            delta = 0;
        }
        t += n;
        n = 0;
        return delta;
    }
}
