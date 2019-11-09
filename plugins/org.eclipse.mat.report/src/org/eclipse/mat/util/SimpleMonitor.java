/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - nested SimpleMonitor
 *******************************************************************************/
package org.eclipse.mat.util;

/**
 * A way of generating several progress monitors.
 */
public class SimpleMonitor
{
    String task;
    IProgressListener delegate;
    int currentMonitor;
    int[] percentages;

    public SimpleMonitor(String task, IProgressListener monitor, int[] percentages)
    {
        this.task = task;
        this.delegate = monitor;
        this.percentages = percentages;
    }

    public IProgressListener nextMonitor()
    {

        // Subcall to simple monitor
        if (delegate instanceof Listener)
        {
            /*
             *  Scale by remaining.
             *  E.g. first monitor has [100,50,100]
             *  total of 250
             *  After second monitor has been used (10), 40 remaining
             *  Now second SimpleMonitor [100,100,100,100] created.
             *  All the percentages need to be scaled by 1/10
             */
            Listener l = (Listener)delegate;
            int togo = l.majorUnits - l.unitsReported;
            int todo = 0;
            for (int i = currentMonitor; i < percentages.length; ++i)
            {
                todo += percentages[i];
            }
            if (currentMonitor == 0)
                delegate.beginTask(task, togo);
            return new Listener((int)((long)percentages[currentMonitor++] * togo / todo));
        }
        if (currentMonitor == 0)
        {
            int total = 0;
            for (int ii : percentages)
                total += ii;
            delegate.beginTask(task, total);
        }

        return new Listener(percentages[currentMonitor++]);
    }

    public class Listener implements IProgressListener
    {
        long counter;

        int majorUnits;
        int unitsReported;
        long workDone;
        long workPerUnit;

        boolean isSmaller;

        public Listener(int majorUnits)
        {
            this.majorUnits = majorUnits;
        }

        public void beginTask(String name, int totalWork)
        {
            if (name != null)
                delegate.subTask(name);

            if (totalWork == 0)
                return;

            if (workDone > 0)
            {
                // Already had a beginTask, so use up the remaining
                if (unitsReported < majorUnits)
                {
                    majorUnits -= unitsReported;
                }
                else
                {
                    majorUnits = 0;
                }
                workDone = 0;
            }

            isSmaller = totalWork < majorUnits || majorUnits == 0;
            workPerUnit = isSmaller ? majorUnits / totalWork : totalWork / majorUnits;
            unitsReported = 0;
        }

        public void subTask(String name)
        {
            delegate.subTask(name);
        }

        public void done()
        {
            if (majorUnits - unitsReported > 0)
                delegate.worked(majorUnits - unitsReported);
        }

        public boolean isCanceled()
        {
            return delegate.isCanceled();
        }

        public boolean isProbablyCanceled()
        {
            return counter++ % 5000 == 0 ? isCanceled() : false;
        }

        public void totalWorkDone(long work)
        {
            if (workDone >= work)
                return;

            if (workPerUnit == 0)
                return;

            workDone = work;
            int unitsWorked = isSmaller ? (int) (work * workPerUnit) : (int) (work / workPerUnit);
            // Avoid exceeding work
            unitsWorked = Math.min(unitsWorked, majorUnits);
            int unitsToReport = unitsWorked - unitsReported;

            if (unitsToReport > 0)
            {
                delegate.worked(unitsToReport);
                unitsReported += unitsToReport;
            }
        }

        public void worked(int work)
        {
            totalWorkDone(workDone + work);
        }

        public void setCanceled(boolean value)
        {
            delegate.setCanceled(value);
        }

        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {
            delegate.sendUserMessage(severity, message, exception);
        }

        public long getWorkDone()
        {
            return workDone;
        }

    }

}
