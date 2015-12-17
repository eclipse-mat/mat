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

import java.net.URL;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.GraftedResultTree;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.SimpleObjectQuery;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.SessionAttributeData;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;

@CommandName("javaee_http_sessions")
public class HttpSessionQuery extends SimpleObjectQuery<HttpSessionQuery.SessionOverviewNode> {
    private static final int[] GRAFTED_COLUMN_MAPPING = {0, 1, 2};


    final static class SessionOverviewNode implements IOverviewNode {
        private final IObject object;
        private List<SessionAttributeData> attributes = null;

        public SessionOverviewNode(IObject object) {
            this.object = object;
        }

        public int getId() {
            return object.getObjectId();
        }

        public long getRetainedHeap() {
            return object.getRetainedHeapSize();
        }

        public synchronized List<SessionAttributeData> getAttributes() {
            if (attributes == null) {
                attributes = ServletExtractors.getSessionExtractor(object).buildSessionAttributes(object);
            }
            return attributes;
        }

        public String getName() {
            return ServletExtractors.getSessionExtractor(object).getSessionId(object);
        }

        public Integer getAttributeCount() {
            List<SessionAttributeData> attibutes = getAttributes();
            return (attibutes != null) ? attributes.size() : null;
        }
    }


    public HttpSessionQuery() {
        super(SessionOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return ServletExtractors.findHttpSessions(snapshot);
    }

    protected SessionOverviewNode createOverviewNode(IObject obj) {
        return new SessionOverviewNode(obj);
    }

    protected boolean overviewHasChildren(SessionOverviewNode row) {
        return true;
    }

    protected Column[] getColumns() {
        return new Column[] {
            new Column("Name", String.class).decorator(GraftedResultTree.GRAFT_DECORATOR),
            new Column("Count/Value", Object.class),
            new Column("Size", Long.class),
        };
    }

    @Override
    protected Object getOverviewColumnValue(SessionOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getName();
        case 1:
            return row.getAttributeCount();
        case 2:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected Object getColumnValue(Object row, int columnIndex) {
        if (row instanceof SessionAttributeData) {
            SessionAttributeData node = (SessionAttributeData)row;
            switch (columnIndex) {
            case 0:
                return node.getKey().getClassSpecificName();
            case 1:
                return node.getValue().getDisplayName();
            case 2:
                return node.getValue().getRetainedHeapSize();
            default:
                JavaEEPlugin.error("Unexpected column index " + columnIndex);
                return null;
            }
        } else {
            return super.getColumnValue(row, columnIndex);
        }
    }

    @Override
    protected List<?> getOverviewChildren(SessionOverviewNode row) {
        return row.getAttributes();
    }

    @Override
    protected boolean hasChildren(Object row) {
        if (row instanceof SessionAttributeData) {
            SessionAttributeData node = (SessionAttributeData) row;
            return !node.getValue().getOutboundReferences().isEmpty();
        } else {
            return super.hasChildren(row);
        }
    }

    @Override
    protected List<?> getChildren(Object row) {
        if (row instanceof SessionAttributeData) {
            SessionAttributeData node = (SessionAttributeData) row;
            // Create an outbound tree and graft it onto the main tree
            return GraftedResultTree.graftOutbound(node.getValue().getSnapshot(), node.getValue().getObjectId(), GRAFTED_COLUMN_MAPPING);
        } else {
            return super.getChildren(row);
        }
    }

    @Override
    protected IContextObject getContext(Object row) {
        if (row instanceof SessionAttributeData) {
            final SessionAttributeData node = (SessionAttributeData) row;
            return new IContextObject() {
                public int getObjectId() {
                    return node.getValue().getObjectId();
                }
            };
        } else {
            return super.getContext(row);
        }
    }

    @Override
    public URL getIcon(Object row) {
        if (row instanceof SessionAttributeData) {
            SessionAttributeData node = (SessionAttributeData) row;
            return Icons.outbound(snapshot, node.getValue().getObjectId());
        } else {
            return super.getIcon(row);
        }
    }
}
