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

@CommandName("javaee_servlets")
public class ServletQuery extends SimpleObjectQuery<ServletQuery.ServletOverviewNode> {
    private static final int[] GRAFTED_COLUMN_MAPPING = {0, -1, -1, 2};


    final static class ServletOverviewNode implements IOverviewNode {
        private final IObject object;

        public ServletOverviewNode(IObject object) {
            this.object = object;
        }

        public int getId() {
            return object.getObjectId();
        }

        public long getRetainedHeap() {
            return object.getRetainedHeapSize();
        }

        public String getName() {
            return ServletExtractors.getServletExtractor(object).getName(object);
        }

        public String getServletClass() {
            return ServletExtractors.getServletExtractor(object).getServletClass(object);
        }

		public String getApplication() {
            return ServletExtractors.getServletExtractor(object).getApplication(object);
		}

        public IObject getObject() {
            return object;
        }
    }



    public ServletQuery() {
        super(ServletOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return ServletExtractors.findServlets(snapshot);
    }

    protected ServletOverviewNode createOverviewNode(IObject obj) {
        return new ServletOverviewNode(obj);
    }

    protected boolean overviewHasChildren(ServletOverviewNode row) {
        return true;
    }

    protected Column[] getColumns() {
        return new Column[] {
            new Column("Name", String.class).decorator(GraftedResultTree.GRAFT_DECORATOR),
            new Column("Application", String.class),
            new Column("Class", String.class),
            new Column("Size", Long.class),
        };
    }

    @Override
    protected Object getOverviewColumnValue(ServletOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getName();
        case 1:
            return row.getApplication();
        case 2:
            return row.getServletClass();
        case 3:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }

    @Override
    protected List<?> getOverviewChildren(ServletOverviewNode row) {
        return GraftedResultTree.graftOutbound(row.getObject().getSnapshot(), row.getId(), GRAFTED_COLUMN_MAPPING);
    }
}
