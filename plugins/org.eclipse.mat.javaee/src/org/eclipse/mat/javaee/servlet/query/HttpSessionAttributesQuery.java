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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.javaee.BaseObjectQuery;
import org.eclipse.mat.javaee.GraftedResultTree;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.SessionAttributeData;
import org.eclipse.mat.javaee.servlet.api.SessionExtractor;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;

@CommandName("javaee_http_session_attributes")
public class HttpSessionAttributesQuery extends BaseObjectQuery<HttpSessionAttributesQuery.SessionAttributeOverviewNode> {
    private static final int[] GRAFTED_COLUMN_MAPPING = {0, 1, 2};


    private static class SessionAttributeNode {
        public Map<String, IObject> sessionValues = new HashMap<String, IObject>();
        @SuppressWarnings("unused") public long retainedHeap = 0;
    };

    final static class SessionAttributeOverviewNode implements IOverviewNode {
        private final IObject key; // there may be multiple identical strings. pick one
        private SessionAttributeNode data = null;

        public SessionAttributeOverviewNode(IObject key, SessionAttributeNode data) {
            this.key = key;
            this.data = data;
        }

        public int getId() {
            return key.getObjectId();
        }

        public long getRetainedHeap() {
            long heap = 0;
            for (Map.Entry<String, IObject> e: data.sessionValues.entrySet()) {
                heap += e.getValue().getRetainedHeapSize();
            }
            return heap;
        }

        public String getName() {
            return key.getClassSpecificName();
        }

        public List<SessionAttributeValueNode> getSessionValues() {
            List<SessionAttributeValueNode> values = new ArrayList<SessionAttributeValueNode>();
            for (Map.Entry<String, IObject> e: data.sessionValues.entrySet()) {
                values.add(new SessionAttributeValueNode(e.getKey(), e.getValue()));
            }
            return values;
        }
    }

    private static class SessionAttributeValueNode {
        private final String sessionId;
        private final IObject value;

        public SessionAttributeValueNode(String sessionId, IObject value) {
            this.sessionId = sessionId;
            this.value = value;
        }

        public String getSessionId() {
            return sessionId;
        }

        public IObject getValue() {
            return value;
        }

    };



    public HttpSessionAttributesQuery() {
        super(SessionAttributeOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return ServletExtractors.findHttpSessions(snapshot);
    }

    protected List<SessionAttributeOverviewNode> buildResults(ArrayInt source) throws SnapshotException {
        Map<String, SessionAttributeOverviewNode> attributes = new HashMap<String, SessionAttributeOverviewNode>();

        IteratorInt it = source.iterator();
        while (it.hasNext()) {
            int id = it.next();
            IObject obj = snapshot.getObject(id);
            SessionExtractor extractor = ServletExtractors.getSessionExtractor(obj);
            if (extractor != null) {
                final String sessionId = extractor.getSessionId(obj);
                for (SessionAttributeData data: extractor.buildSessionAttributes(obj)) {
                    IObject key = data.getKey();
                    // use the class specific name of the object as the map key, to de-duplicate strings
                    SessionAttributeOverviewNode overviewNode = attributes.get(key.getClassSpecificName());
                    if (overviewNode == null) {
                        overviewNode = new SessionAttributeOverviewNode(key, new SessionAttributeNode());
                        // just use the first instance of the key string as the object, since we must have one
                        attributes.put(key.getClassSpecificName(), overviewNode);
                    }
                    SessionAttributeNode node = overviewNode.data;
                    node.sessionValues.put(sessionId, data.getValue());
                    node.retainedHeap += data.getValue().getRetainedHeapSize();
                }
            }
        }

        return new ArrayList<SessionAttributeOverviewNode>(attributes.values());
    }

    protected boolean overviewHasChildren(SessionAttributeOverviewNode row) {
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
    protected Object getOverviewColumnValue(SessionAttributeOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getName();
        case 1:
            // number of sessions with the attribute
            return row.data.sessionValues.size();
        case 2:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected Object getColumnValue(Object row, int columnIndex) {
        if (row instanceof SessionAttributeValueNode) {
            SessionAttributeValueNode node = (SessionAttributeValueNode)row;
            switch (columnIndex) {
            case 0:
                return node.getSessionId();
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
    protected List<?> getOverviewChildren(SessionAttributeOverviewNode row) {
        return row.getSessionValues();
    }

    @Override
    protected boolean hasChildren(Object row) {
        if (row instanceof SessionAttributeValueNode) {
            SessionAttributeValueNode node = (SessionAttributeValueNode) row;
            return !node.getValue().getOutboundReferences().isEmpty();
        } else {
            return super.hasChildren(row);
        }
    }

    @Override
    protected List<?> getChildren(Object row) {
        if (row instanceof SessionAttributeValueNode) {
            SessionAttributeValueNode node = (SessionAttributeValueNode) row;
            // Create an outbound tree and graft it onto the main tree
            return GraftedResultTree.graftOutbound(node.getValue().getSnapshot(), node.getValue().getObjectId(), GRAFTED_COLUMN_MAPPING);
        } else {
            return super.getChildren(row);
        }
    }

    @Override
    protected IContextObject getContext(Object row) {
        if (row instanceof SessionAttributeValueNode) {
            final SessionAttributeValueNode node = (SessionAttributeValueNode) row;
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
        if (row instanceof SessionAttributeValueNode) {
            SessionAttributeValueNode node = (SessionAttributeValueNode) row;
            return Icons.outbound(snapshot, node.getValue().getObjectId());
        } else {
            return super.getIcon(row);
        }
    }
}
