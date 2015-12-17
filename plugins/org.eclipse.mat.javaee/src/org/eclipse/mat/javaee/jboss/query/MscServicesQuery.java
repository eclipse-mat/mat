/*******************************************************************************
 * Copyright (c) 2015 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.jboss.query;

import java.net.URL;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.SimpleObjectQuery;
import org.eclipse.mat.javaee.impl.JBossExtractors;
import org.eclipse.mat.javaee.jboss.api.JBossMscExtractor;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;

@CommandName("jboss_msc_services")
public class MscServicesQuery extends SimpleObjectQuery<MscServicesQuery.MscServiceOverviewNode> {
    private static final Column[] COLUMNS = {
        new Column("Service", String.class),
        new Column("Mode", String.class),
        new Column("Status", String.class),
        new Column("Size", Long.class),
    };

    final static class MscServiceOverviewNode implements IOverviewNode {
        private final IObject registration;
        private final JBossMscExtractor extractor;

        public MscServiceOverviewNode(IObject registration) {
            this.registration = registration;
            this.extractor = JBossExtractors.getJBossMscExtractor(registration);
        }

        public long getRetainedHeap() {
            return registration.getRetainedHeapSize();
        }

        public int getId() {
            return registration.getObjectId();
        }

        private JBossMscExtractor getExtractor() {
            return extractor;
        }

        public String getServiceName() {
            return getExtractor().getServiceName(registration);
        }

        public String getMode() {
            return getExtractor().getMode(registration);
        }

        public String getState() {
            return getExtractor().getState(registration);
        }
    }


    public MscServicesQuery() {
        super(MscServiceOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return JBossExtractors.findMscServices(snapshot);
    }

    protected MscServiceOverviewNode createOverviewNode(IObject obj) {
        return new MscServiceOverviewNode(obj);
    }

    protected boolean overviewHasChildren(MscServiceOverviewNode row) {
        return false;
    }

    protected Column[] getColumns() {
        return COLUMNS;
    }

    @Override
    protected Object getOverviewColumnValue(MscServiceOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getServiceName();
        case 1:
            return row.getMode();
        case 2:
            return row.getState();
        case 3:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected IContextObject getContext(Object row) {
        if (row instanceof MscServiceOverviewNode) {
            final MscServiceOverviewNode node = (MscServiceOverviewNode) row;
            return new IContextObject() {
                public int getObjectId() {
                    return node.getId();
                }
            };
        } else {
            return super.getContext(row);
        }
    }

    @Override
    public URL getIcon(Object row) {
        if (row instanceof MscServiceOverviewNode) {
            MscServiceOverviewNode node = (MscServiceOverviewNode) row;
            return Icons.outbound(snapshot, node.getId());
        } else {
            return super.getIcon(row);
        }
    }
}
