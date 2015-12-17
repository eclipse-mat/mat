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
package org.eclipse.mat.javaee;

import java.lang.reflect.Array;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;

/* O is the "overview" node type*/
public abstract class BaseObjectQuery<O extends IOverviewNode> implements IQuery, IDecorator, IIconProvider {
    private final Class<O> oClass;

    @Argument public ISnapshot snapshot;

    @Argument(isMandatory = false, flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    public BaseObjectQuery(Class<O> oClass) {
        this.oClass = oClass;
    }

    public IResult execute(IProgressListener listener) throws Exception {
    	ArrayInt source = ((objects != null) ? new ArrayInt(objects.getIds(listener)) : findObjects(snapshot));
        O[] result = buildResults(source).toArray((O[]) Array.newInstance(oClass, 0));

        Arrays.sort(result, new Comparator<O>() {
            public int compare(O o1, O o2) {
                return o1.getRetainedHeap() > o2.getRetainedHeap() ? -1 : o1.getRetainedHeap() == o2.getRetainedHeap() ? 0 : 1;
            }
        });

        return new ResultList(result);
    }

    protected abstract List<O> buildResults(ArrayInt source) throws SnapshotException;
    protected abstract ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException;
    protected abstract boolean overviewHasChildren(O row);
    protected abstract Column[]  getColumns();

    protected Object getOverviewColumnValue(O row, int columnIndex) {
        JavaEEPlugin.error("Unexpected column index " + columnIndex);
        return null;
    }

    protected Object getColumnValue(Object row, int columnIndex) {
        if (row.getClass().isAssignableFrom(oClass)) {
            return getOverviewColumnValue((O)row, columnIndex);
        } else if (row instanceof GraftedResultTree.Proxy) {
            return ((GraftedResultTree.Proxy)row).getColumnValue(columnIndex);
        } else {
            JavaEEPlugin.error("getColumnValue(): Unexpected row class " + row.getClass().getName());
            return null;
        }
    }

    protected IContextObject getContext(Object row) {
        if (row.getClass().isAssignableFrom(oClass)) {
            final O node = (O) row;
            return new IContextObject() {
                public int getObjectId() {
                    return node.getId();
                }
            };
        } else if (row instanceof GraftedResultTree.Proxy) {
            GraftedResultTree.Proxy proxy = (GraftedResultTree.Proxy) row;
            return proxy.getContext();/*FIXME bad performance?!?
            return null;*/
        } else {
            JavaEEPlugin.error("getContext(): Unexpected row class " + row.getClass().getName());
            return null;
        }
    }


    protected boolean hasChildren(Object row) {
        if (row.getClass().isAssignableFrom(oClass)) {
            return overviewHasChildren((O)row);
        } else if (row instanceof GraftedResultTree.Proxy) {
            return ((GraftedResultTree.Proxy)row).hasChildren();
        } else {
            JavaEEPlugin.error("hasChildren(): Unexpected row class " + row.getClass().getName());
            return false;
        }
    }

    protected List<?> getChildren(Object row) {
        if (row.getClass().isAssignableFrom(oClass)) {
            return getOverviewChildren((O)row);
        } else if (row instanceof GraftedResultTree.Proxy) {
            GraftedResultTree.Proxy proxy = (GraftedResultTree.Proxy) row;
            return proxy.getChildren();
        } else {
            JavaEEPlugin.error("getChildren(): Unexpected row class " + row.getClass().getName());
            return null;
        }
    }

    protected List<?> getOverviewChildren(O row) {
        return Collections.emptyList();
    }

    public URL getIcon(Object row) {
        if (row.getClass().isAssignableFrom(oClass)) {
            return getOverviewIcon((O)row);
        } else if (row instanceof GraftedResultTree.Proxy) {
            GraftedResultTree.Proxy proxy = (GraftedResultTree.Proxy) row;
            return proxy.getIcon();
        } else {
            JavaEEPlugin.error("getIcon(): Unexpected row class " + row.getClass().getName());
            return null;
        }
    }

    protected URL getOverviewIcon(O row) {
        return Icons.forObject(snapshot, row.getId());
    }

    public String prefix(Object row) {
        if (row instanceof GraftedResultTree.Proxy) {
            GraftedResultTree.Proxy proxy = (GraftedResultTree.Proxy) row;
            return proxy.prefix();
        } else {
            return null;
        }
    }

    public String suffix(Object row) {
        if (row instanceof GraftedResultTree.Proxy) {
            GraftedResultTree.Proxy proxy = (GraftedResultTree.Proxy) row;
            return proxy.suffix();
        } else {
            return null;
        }
    }


    private class ResultList implements IResultTree, IIconProvider, IDecorator {
        private final O[] results;

        public ResultList(O[] sessions) {
            this.results = sessions;
        }

        public Object getColumnValue(Object row, int columnIndex) {
            return BaseObjectQuery.this.getColumnValue(row, columnIndex);
        }

        public Column[] getColumns() {
            return BaseObjectQuery.this.getColumns();
        }

        public IContextObject getContext(Object row) {
            return BaseObjectQuery.this.getContext(row);
        }

        public ResultMetaData getResultMetaData() {
            return new ResultMetaData.Builder()
                .build();
        }

        public List<O> getElements() {
            return Arrays.asList(results);
        }

        public boolean hasChildren(Object row) {
            return BaseObjectQuery.this.hasChildren(row);
        }

        public List<?> getChildren(Object row) {
            return BaseObjectQuery.this.getChildren(row);
        }

        public URL getIcon(Object row) {
            return BaseObjectQuery.this.getIcon(row);
        }

        public String prefix(Object row) {
            return BaseObjectQuery.this.prefix(row);
        }

        public String suffix(Object row) {
            return BaseObjectQuery.this.suffix(row);
        }
    }
}
