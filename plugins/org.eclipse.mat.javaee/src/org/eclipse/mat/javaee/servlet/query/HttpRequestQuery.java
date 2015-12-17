/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.servlet.query;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.GraftedResultTree;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.SimpleObjectQuery;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

@CommandName("javaee_http_requests")
public class HttpRequestQuery extends SimpleObjectQuery<HttpRequestQuery.RequestOverviewNode> {
    private static final int[] GRAFTED_COLUMN_MAPPING = {0, 1, 2};


    final static class RequestOverviewNode implements IOverviewNode {
        private final IObject request;

        public RequestOverviewNode(IObject request) {
            this.request = request;
        }

        public long getRetainedHeap() {
            return request.getRetainedHeapSize();
        }

        public int getId() {
            return request.getObjectId();
        }

        public String getUri() {
            return ServletExtractors.getRequestExtractor(request).getRequestUri(request);
        }

        public boolean isInUse() {
            return ServletExtractors.getRequestExtractor(request).isInUse(request);
        }

        public IObject getObject() {
            return request;
        }
    }



    public HttpRequestQuery() {
        super(RequestOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return ServletExtractors.findHttpRequests(snapshot);
    }

    protected RequestOverviewNode createOverviewNode(IObject obj) {
        return new RequestOverviewNode(obj);
    }

    protected boolean overviewHasChildren(RequestOverviewNode row) {
        return true;
    }

    protected Column[] getColumns() {
        return new Column[] {
            new Column("Name", String.class).decorator(GraftedResultTree.GRAFT_DECORATOR),
            new Column("Shallow Heap", Long.class),
            new Column("Retained Heap", Long.class),
        };
    }

    @Override
    protected Object getOverviewColumnValue(RequestOverviewNode row, int columnIndex) {
        boolean inUse = row.isInUse();
        switch (columnIndex) {
        case 0:
            if (inUse)
                return row.getUri();
            else
                return "- Not in use -";
        case 1:
            return null;
        case 2:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected List<?> getOverviewChildren(RequestOverviewNode row) {
        return GraftedResultTree.graftOutbound(row.getObject().getSnapshot(), row.getId(), GRAFTED_COLUMN_MAPPING);
    }
}
