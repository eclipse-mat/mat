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
package org.eclipse.mat.query.results;

import org.eclipse.mat.inspections.query.ObjectListQuery.InboundObjects;
import org.eclipse.mat.inspections.query.ObjectListQuery.OutboundObjects;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.ISnapshot;

/**
 * A list of objects. By default, the outbound references of the objects are
 * rendered.
 */
public final class ObjectListResult implements IResult
{
    private String label;
    private int[] objectIds;
    private boolean showOutbound = true;

    public ObjectListResult(String label, int[] objectIds)
    {
        this(label, objectIds, true);
    }

    public ObjectListResult(String label, int[] objectIds, boolean showOutbound)
    {
        this.label = label;
        this.objectIds = objectIds;
        this.showOutbound = showOutbound;
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public String getLabel()
    {
        return label;
    }

    public int[] getObjectIds()
    {
        return objectIds;
    }

    public boolean showOutbound()
    {
        return showOutbound;
    }

    public IResultTree asTree(ISnapshot snapshot)
    {
        return showOutbound ? new OutboundObjects(snapshot, objectIds) //
                        : new InboundObjects(snapshot, objectIds);
    }

}
