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
package org.eclipse.mat.inspections.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.Units;


public class PieFactory
{
    private ISnapshot snapshot;
    private long totalHeap;
    private long retainedHeapBySlices;
    private List<SliceImpl> slices = new ArrayList<SliceImpl>();

    public PieFactory(ISnapshot snapshot)
    {
        this(snapshot, snapshot.getSnapshotInfo().getUsedHeapSize());
    }

    public PieFactory(ISnapshot snapshot, long totalHeap)
    {
        this.snapshot = snapshot;
        this.totalHeap = totalHeap;
    }

    public Slice addSlice(int objectId) throws SnapshotException
    {
        IObject obj = snapshot.getObject(objectId);
        return addSlice(obj.getObjectId(), obj.getDisplayName(), obj.getUsedHeapSize(), obj.getRetainedHeapSize());
    }

    public Slice addSlice(int objectId, String label, long usedHeapSize, long retainedHeapSize)
    {
        SliceImpl slice = new SliceImpl(objectId);
        slice.label = label != null ? label : "";
        slice.shallowSize = usedHeapSize;
        slice.retainedSize = retainedHeapSize;
        slices.add(slice);

        retainedHeapBySlices += slice.retainedSize;

        return slice;
    }

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

    private final static class SliceImpl implements IResultPie.Slice, Serializable
    {
        private static final long serialVersionUID = 1L;

        int objectId;

        String label;
        long shallowSize;
        long retainedSize;

        private SliceImpl(int objectId)
        {
            this.objectId = objectId;
        }

        public String getDescription()
        {
            StringBuilder buf = new StringBuilder();

            buf.append("<p>").append("<b>").append(HTMLUtils.escapeText(getLabel())).append("</b></p>");

            if (label != null)
            {
                buf.append("<br/><p>Shallow Size: <b>");
                buf.append(Units.Storage.of(shallowSize).format(shallowSize));
                buf.append("</b>     Retained Size: <b>");
                buf.append(Units.Storage.of(retainedSize).format(retainedSize));
                buf.append("</b></p>");
            }

            return buf.toString();
        }

        public String getLabel()
        {
            return label != null ? label : "Remainder";
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
    }

}
