/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - improvements, bug 364505
 *******************************************************************************/
package org.eclipse.mat.snapshot.query;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.Units;

/**
 * Helper class to create pie chart results for heap objects.
 * <p>
 * Usage:
 * 
 * <pre>
 * public class PieQuery implements IQuery
 * {
 *     &#064;Argument
 *     public ISnapshot snapshot;
 * 
 *     public IResult execute(IProgressListener listener) throws Exception
 *     {
 *         PieFactory f = new PieFactory(snapshot);
 * 
 *         int[] topDominators = snapshot.getImmediateDominatedIds(-1);
 * 
 *         for (int ii = 0; ii &lt; 5 &amp;&amp; ii &lt; topDominators.length; ii++)
 *             f.addSlice(topDominators[ii]);
 * 
 *         return f.build();
 *     }
 * 
 * }
 * </pre>
 * 
 * @since 0.8
 */
public final class PieFactory
{
    private ISnapshot snapshot;
    private long totalHeap;
    private long retainedHeapBySlices;
    private List<SliceImpl> slices = new ArrayList<SliceImpl>();

    /**
     * Create a pie factory for the given snapshot. The size of the pie is the
     * total heap size
     * {@link org.eclipse.mat.snapshot.SnapshotInfo#getUsedHeapSize()}.
     * 
     * @param snapshot
     */
    public PieFactory(ISnapshot snapshot)
    {
        this(snapshot, snapshot.getSnapshotInfo().getUsedHeapSize());
    }

    /**
     * Create a pie factory for the given snapshot.
     * 
     * @param snapshot
     *            snapshot containing the objects
     * @param pieSize
     *            total size of the pie
     */
    public PieFactory(ISnapshot snapshot, long pieSize)
    {
        this.snapshot = snapshot;
        this.totalHeap = pieSize;
    }

    /**
     * Create a pie factory for objects. Objects must be added either via
     * {@link #addSlice(IObject)} or {@link #addSlice(int, String, long, long)}
     * methods.
     * 
     * @param pieSize
     *            total size of the pie
     */
    public PieFactory(long pieSize)
    {
        this(null, pieSize);
    }

    /**
     * Create and add a new slice for the given object. The size of the slice is
     * determined by the retained size.
     * <p>
     * To use this method, one needs to pass a {@link ISnapshot} to the
     * constructor.
     * 
     * @param objectId
     *            object id
     * @return a new slice
     */
    public Slice addSlice(int objectId) throws SnapshotException
    {
        return addSlice(objectId, null);
    }

    /**
     * Create and add a new slice for the given object. The size of the slice is
     * determined by the retained size.
     * <p>
     * To use this method, one needs to pass a {@link ISnapshot} to the
     * constructor.
     * 
     * @param objectId
     *            object id
     * @param color
     *            Explicit color of the slice
     * @return a new slice
     * @since 1.2
     */
    public Slice addSlice(int objectId, Color color) throws SnapshotException
    {
        if (snapshot == null)
            throw new NullPointerException(Messages.PieFactory_ErrorMsg_NoSnapshotAvailable);

        IObject obj = snapshot.getObject(objectId);
        return addSlice(obj, color);
    }

    /**
     * Create and add a new slice for the given object. The size of the slice is
     * determined by the retained size.
     * 
     * @param object
     * @return a new slice
     */
    public Slice addSlice(IObject object)
    {
        return addSlice(object, null);
    }
    
    /**
     * Create and add a new slice for the given object. The size of the slice is
     * determined by the retained size.
     * 
     * @param object
     * @param color
     *        Explicit color of the slice
     * @return a new slice
     * @since 1.2
     */
    public Slice addSlice(IObject object, Color color)
    {
        return addSlice(object.getObjectId(), object.getDisplayName(), object.getUsedHeapSize(), object
                        .getRetainedHeapSize(), color);
    }

    /**
     *Create and add a new slice for the given object.
     * 
     * @param objectId
     *            object id
     * @param label
     *            (optionally) a label describing the object (for display)
     * @param usedHeapSize
     *            (optionally) the used heap size (for display)
     * @param retainedHeapSize
     *            the retained size which determines the size of the slice
     * @return a new slice
     */
    public Slice addSlice(int objectId, String label, long usedHeapSize, long retainedHeapSize)
    {
        return addSlice(objectId, label, usedHeapSize, retainedHeapSize, null);
    }
    
    /**
     *Create and add a new slice for the given object.
     * 
     * @param objectId
     *            object id
     * @param label
     *            (optionally) a label describing the object (for display)
     * @param usedHeapSize
     *            (optionally) the used heap size (for display)
     * @param retainedHeapSize
     *            the retained size which determines the size of the slice
     * @param color
     *            the color to use for the slice
     * @return a new slice
     * @since 1.2
     */
    public Slice addSlice(int objectId, String label, long usedHeapSize, long retainedHeapSize, Color color)
    {
        SliceImpl slice = new SliceImpl(objectId);
        slice.label = label != null ? label : ""; //$NON-NLS-1$
        slice.shallowSize = usedHeapSize;
        slice.retainedSize = retainedHeapSize;
        slice.color = color;
        slices.add(slice);

        retainedHeapBySlices += slice.retainedSize;

        return slice;
    }

    /**
     * Create and return the pie result object.
     * 
     * @return the pie result object
     */
    public IResultPie build()
    {
        if (retainedHeapBySlices < totalHeap)
        {
            SliceImpl rest = new SliceImpl(-1);
            rest.retainedSize = totalHeap - retainedHeapBySlices;
            slices.add(rest);
        }

        return new PieImpl(slices);
    }

    private static class PieImpl implements IResultPie, Serializable
    {
        private static final long serialVersionUID = 1L;

        private List<SliceImpl> slices;

        private PieImpl(List<SliceImpl> slices)
        {
            this.slices = slices;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public List<? extends Slice> getSlices()
        {
            return slices;
        }
    }

    private final static class SliceImpl implements IResultPie.ColoredSlice, Serializable
    {
        private static final long serialVersionUID = 1L;
        private static final String HTML_BREAK = "<br>"; //$NON-NLS-1$

        int objectId;

        String label;
        long shallowSize;
        long retainedSize;
        Color color;

        private SliceImpl(int objectId)
        {
            this.objectId = objectId;
        }

        public String getDescription()
        {
            StringBuilder buf = new StringBuilder();

            buf.append("<p>").append("<b>").append(HTMLUtils.escapeText(getLabel())).append("</b></p>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            if (label != null)
            {
                buf.append(HTML_BREAK).append("<p>").append(Messages.PieFactory_Label_ShallowSize).append(" <b>"); //$NON-NLS-1$ //$NON-NLS-2$
                buf.append(Units.Storage.of(shallowSize).format(shallowSize));
                buf.append("</b>     ").append(Messages.PieFactory_Label_RetainedSize).append(" <b>"); //$NON-NLS-1$ //$NON-NLS-2$
                buf.append(Units.Storage.of(retainedSize).format(retainedSize));
                buf.append("</b></p>"); //$NON-NLS-1$
            }

            return buf.toString();
        }

        public String getLabel()
        {
            return label != null ? label : Messages.PieFactory_Label_Remainder;
        }

        public double getValue()
        {
            return retainedSize;
        }

        public IContextObject getContext()
        {
            if (objectId >= 0)
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return objectId;
                    }
                };
            }
            else
            {
                return null;
            }
        }
        
        public Color getColor()
        {
            return color;
        }
    }

}
