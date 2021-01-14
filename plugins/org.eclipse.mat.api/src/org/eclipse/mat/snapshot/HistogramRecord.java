/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.io.Serializable;
import java.util.Comparator;

/**
 * This class holds all basic data for a histogram record. Other classes inherit
 * from it. It offers comparators to sort histogram records by their basic data.
 * This pattern should be implemented by the classes inheriting from it as well.
 */
public class HistogramRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    protected String label;
    protected long numberOfObjects;
    protected long usedHeapSize;
    protected long retainedHeapSize;

    public static final Comparator<HistogramRecord> COMPARATOR_FOR_LABEL = new Comparator<HistogramRecord>()
    {
        public int compare(HistogramRecord o1, HistogramRecord o2)
        {
            return o1.getLabel().compareToIgnoreCase(o2.getLabel());
        }
    };

    public static final Comparator<HistogramRecord> COMPARATOR_FOR_NUMBEROFOBJECTS = new Comparator<HistogramRecord>()
    {
        public int compare(HistogramRecord o1, HistogramRecord o2)
        {
            long diff = o1.getNumberOfObjects() - o2.getNumberOfObjects();
            return ((diff == 0) ? 0 : ((diff > 0) ? +1 : -1));
        }
    };

    public static final Comparator<HistogramRecord> COMPARATOR_FOR_USEDHEAPSIZE = new Comparator<HistogramRecord>()
    {
        public int compare(HistogramRecord o1, HistogramRecord o2)
        {
            long diff = o1.getUsedHeapSize() - o2.getUsedHeapSize();
            return ((diff == 0) ? 0 : ((diff > 0) ? +1 : -1));
        }
    };

    public static final Comparator<HistogramRecord> COMPARATOR_FOR_RETAINEDHEAPSIZE = new Comparator<HistogramRecord>()
    {
        public int compare(HistogramRecord o1, HistogramRecord o2)
        {
            long retained_o1 = o1.getRetainedHeapSize();
            long retained_o2 = o2.getRetainedHeapSize();
            if (retained_o1 < 0)
                retained_o1 = -retained_o1;
            if (retained_o2 < 0)
                retained_o2 = -retained_o2;
            long diff = retained_o1 - retained_o2;
            return ((diff == 0) ? 0 : ((diff > 0) ? +1 : -1));
        }
    };

    HistogramRecord()
    {
        super();
    }

    /**
     * Create histogram record just with a label identifying it.
     * 
     * @param label
     *            label identifying the histogram record
     */
    public HistogramRecord(String label)
    {
        this(label, 0, 0, 0);
    }

    /**
     * Create histogram record with a label identifying it and its basic data.
     * 
     * @param label
     *            label identifying the histogram record
     * @param numberOfObjects
     *            number of objects this histogram record stands for
     * @param usedHeapSize
     *            number of bytes in heap area this histogram record stands for
     */
    public HistogramRecord(String label, long numberOfObjects, long usedHeapSize, long retainedHeapSize)
    {
        this.label = label;
        this.numberOfObjects = numberOfObjects;
        this.usedHeapSize = usedHeapSize;
        this.retainedHeapSize = retainedHeapSize;
    }

    /**
     * Get label identifying the histogram record.
     * 
     * @return label identifying the histogram record
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * Set label identifying the histogram record.
     * 
     * @param label
     *            label identifying the histogram record
     */
    public void setLabel(String label)
    {
        this.label = label;
    }

    /**
     * Get number of objects this histogram record stands for.
     * 
     * @return number of objects this histogram record stands for
     */
    public long getNumberOfObjects()
    {
        return numberOfObjects;
    }

    /**
     * Set number of objects this histogram record stands for.
     * 
     * @param numberOfObjects
     *            number of objects this histogram record stands for
     */
    public void setNumberOfObjects(long numberOfObjects)
    {
        this.numberOfObjects = numberOfObjects;
    }

    /**
     * Increment number of objects this histogram record stands for by 1.
     */
    public void incNumberOfObjects()
    {
        this.numberOfObjects++;
    }

    /**
     * Increment number of objects this histogram record stands for.
     * 
     * @param inc
     *            number of objects by which the number of objects should be
     *            incremented
     */
    public void incNumberOfObjects(long inc)
    {
        this.numberOfObjects += inc;
    }

    /**
     * Get number of bytes in heap area this histogram record stands for.
     * 
     * @return number of bytes in heap area this histogram record stands for
     */
    public long getUsedHeapSize()
    {
        return usedHeapSize;
    }

    /**
     * Set number of bytes in heap area this histogram record stands for.
     * 
     * @param usedHeapSize
     *            number of bytes in heap area this histogram record stands for
     */
    public void setUsedHeapSize(long usedHeapSize)
    {
        this.usedHeapSize = usedHeapSize;
    }

    /**
     * Increment number of bytes in heap area this histogram record stands for.
     * 
     * @param inc
     *            number of bytes by which the number of bytes in heap area
     *            should be incremented
     */
    public void incUsedHeapSize(long inc)
    {
        this.usedHeapSize += inc;
    }

    /**
     * Get number of retained bytes in heap area this histogram record stands
     * for.
     * <p>
     * Retained bytes means how much memory would be garbage collected if the
     * references to the objects this histogram record stands for would be lost
     * and the objects garbage collected.
     * 
     * @return number of retained bytes in heap area this histogram record
     *         stands for
     */
    public long getRetainedHeapSize()
    {
        return retainedHeapSize;
    }

    /**
     * Set number of retained bytes in heap area this histogram record stands
     * for.
     * <p>
     * Retained bytes means how much memory would be garbage collected if the
     * references to the objects this histogram record stands for would be lost
     * and the objects garbage collected.
     * 
     * @param retainedHeapSize
     *            number of retained bytes in heap area this histogram record
     *            stands for
     */
    public void setRetainedHeapSize(long retainedHeapSize)
    {
        this.retainedHeapSize = retainedHeapSize;
    }

    public void incRetainedHeapSize(long inc)
    {
        this.retainedHeapSize += inc;
    }

    /**
     * Convenience method reversing the order of the given comparator. Be aware
     * that each time you call this method a new comparator is returned and that
     * this comparator delegates the call to the given comparator, but just
     * switches the parameters. You can get the same result with the given
     * comparator, by iterating from the last to the first element instead. From
     * performance perspective using e.g. {@link java.util.List#get(int)} is
     * anyhow faster compared to an {@link java.util.Iterator}.
     * 
     * @param comparator
     *            comparator for which a reversed comparator should be returned
     * @return comparator comparing in reverse order than the given comparator
     */
    public static Comparator<HistogramRecord> reverseComparator(final Comparator<HistogramRecord> comparator)
    {
        return new Comparator<HistogramRecord>()
        {
            public int compare(HistogramRecord o1, HistogramRecord o2)
            {
                return comparator.compare(o2, o1);
            }
        };
    }
}
