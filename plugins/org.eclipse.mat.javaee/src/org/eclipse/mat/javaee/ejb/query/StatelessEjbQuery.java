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
package org.eclipse.mat.javaee.ejb.query;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.GraftedResultTree;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.SimpleObjectQuery;
import org.eclipse.mat.javaee.ejb.api.StatelessEjbExtractor;
import org.eclipse.mat.javaee.impl.EjbExtractors;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;

@CommandName("javaee_ejb_stateless")
public class StatelessEjbQuery extends SimpleObjectQuery<StatelessEjbQuery.StatelessEJbOverviewNode> {
    private static final int[] GRAFTED_COLUMN_MAPPING = {0, 1, -1, -1, -1, -1, -1, 2};

    final static class StatelessEJbOverviewNode implements IOverviewNode {
        private final IObject request;
        // TODO: do we need to cache this?
        private final StatelessEjbExtractor extractor;

        public StatelessEJbOverviewNode(IObject request) {
            this.request = request;
            this.extractor = EjbExtractors.getStatelessEjbExtractor(request);
        }

        public long getRetainedHeap() {
            return request.getRetainedHeapSize();
        }

        public int getId() {
            return request.getObjectId();
        }

        private StatelessEjbExtractor getExtractor() {
            return extractor;
        }

        public String getComponentName() {
            return getExtractor().getComponentName(request);
        }

        public String getModuleName() {
            return getExtractor().getModuleName(request);
        }

        public String getApplicationName() {
            return getExtractor().getApplicationName(request);
        }

        public String getDistinctName() {
            return getExtractor().getDistinctName(request);
        }

        public IClass getComponentClass() {
            return getExtractor().getComponentClass(request);
        }

        public Integer getInstanceCount() {
            return getExtractor().getInstanceCount(request);
        }

        public Map<IObject, IObject> getInstances() {
            return getExtractor().getInstances(request);
        }
    }

    private static class StatelessEJbInstanceNode {
        private final IObject key;
        private final IObject instance;

        public StatelessEJbInstanceNode(IObject key, IObject instance) {
            this.key = key;
            this.instance = instance;
        }

        public IObject getKey() {
            return key;
        }

        public IObject getInstance() {
            return instance;
        }

        public long getRetainedHeap() {
            return instance.getRetainedHeapSize();
        }
    }


    public StatelessEjbQuery() {
        super(StatelessEJbOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return EjbExtractors.findStatelessEjbs(snapshot);
    }

    protected StatelessEJbOverviewNode createOverviewNode(IObject obj) {
        return new StatelessEJbOverviewNode(obj);
    }

    protected boolean overviewHasChildren(StatelessEJbOverviewNode row) {
        return row.getInstanceCount() > 0;
    }

    protected Column[] getColumns() {
        return new Column[] {
            new Column("Component", String.class).decorator(GraftedResultTree.GRAFT_DECORATOR),
            new Column("Module", String.class),
            new Column("Application", String.class),
            new Column("Distinct Name", String.class),
            new Column("Class", String.class),
            new Column("Instances", Integer.class),
            new Column("Size", Long.class),
            //TODO: add a column for inUse or pool max size?
        };
    }

    @Override
    protected Object getOverviewColumnValue(StatelessEJbOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getComponentName();
        case 1:
            return row.getModuleName();
        case 2:
            return row.getApplicationName();
        case 3:
            return row.getDistinctName();
        case 4:
            return row.getComponentClass().getName();
        case 5:
            return row.getInstanceCount();
        case 6:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected List<StatelessEJbInstanceNode> getOverviewChildren(StatelessEJbOverviewNode row) {
        Map<IObject, IObject> instances = row.getInstances();
        List<StatelessEJbInstanceNode> results = new ArrayList<StatelessEJbInstanceNode>(instances.size());
        for (Map.Entry<IObject,IObject> i: instances.entrySet()) {
            results.add(new StatelessEJbInstanceNode(i.getKey(), i.getValue()));
        }
        return results;
    }


    @Override
    protected Object getColumnValue(Object row, int columnIndex) {
        if (row instanceof StatelessEJbInstanceNode) {
            StatelessEJbInstanceNode node = (StatelessEJbInstanceNode)row;
            switch (columnIndex) {
            case 0:
                return node.getKey().getDisplayName();
            case 1:
                return null;
            case 2:
                return null;
            case 3:
                return null;
            case 4:
                return node.getInstance();
            case 5:
                return null;
            case 6:
                return node.getRetainedHeap();
            default:
                JavaEEPlugin.error("Unexpected column index " + columnIndex);
                return null;
            }
        } else {
            return super.getColumnValue(row, columnIndex);
        }
    }


    @Override
    protected boolean hasChildren(Object row) {
        if (row instanceof StatelessEJbInstanceNode) {
            StatelessEJbInstanceNode node = (StatelessEJbInstanceNode) row;
            return !node.getInstance().getOutboundReferences().isEmpty();
        } else {
            return super.hasChildren(row);
        }
    }

    @Override
    protected List<?> getChildren(Object row) {
        if (row instanceof StatelessEJbInstanceNode) {
            StatelessEJbInstanceNode node = (StatelessEJbInstanceNode) row;
            // Create an outbound tree and graft it onto the main tree
            IObject instance = node.getInstance();
            return GraftedResultTree.graftOutbound(instance.getSnapshot(), instance.getObjectId(), GRAFTED_COLUMN_MAPPING);
        } else {
            return super.getChildren(row);
        }
    }

    @Override
    protected IContextObject getContext(Object row) {
        if (row instanceof StatelessEJbInstanceNode) {
            final StatelessEJbInstanceNode node = (StatelessEJbInstanceNode) row;
            return new IContextObject() {
                public int getObjectId() {
                    return node.getInstance().getObjectId();
                }
            };
        } else {
            return super.getContext(row);
        }
    }

    @Override
    public URL getIcon(Object row) {
        if (row instanceof StatelessEJbInstanceNode) {
            StatelessEJbInstanceNode node = (StatelessEJbInstanceNode) row;
            return Icons.outbound(snapshot, node.getInstance().getObjectId());
        } else {
            return super.getIcon(row);
        }
    }
}
