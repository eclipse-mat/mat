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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.collect.ArrayUtils;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.ObjectListResult;

public class GraftedResultTree {
    private final IResultTree subtree;
    private final int[] columnMapping;

    private GraftedResultTree(IResultTree tree, final int[] columnMapping) {
        if (ArrayUtils.max(columnMapping) > tree.getColumns().length) {
            throw new IllegalArgumentException("Mapped to non-existant columns");
        }

        this.subtree = tree;
        this.columnMapping = columnMapping;
    }

    public static List<?> graft(IResultTree child, final int[] columnMapping) {
        GraftedResultTree tree = new GraftedResultTree(child, columnMapping);
        List<? extends Object> children = child.getElements();
        List<Proxy> results = new ArrayList<Proxy>(children.size());
        for (Object o: children) {
            results.add(tree.proxy(o));
        }
        return results;
    }
    
    public static List<?> graftOutbound(ISnapshot snapshot, int objectId, final int[] columnMapping) {
        IResultTree subtree = new ObjectListResult.Outbound(snapshot, new int[] {objectId});
        GraftedResultTree tree = new GraftedResultTree(subtree, columnMapping);
        Object o = subtree.getElements().get(0);
        Proxy p = tree.proxy(o);
        return p.getChildren();
    }

    

    private Proxy proxy(Object o) {
        return new Proxy(o);
    }

    public class Proxy {
        private final Object o;

        public Proxy(Object o) {
            this.o = o;
        }

        public List<?> getChildren() {
            List<?> children = subtree.getChildren(o);
            List<Proxy> results = new ArrayList<Proxy>(children.size());
            for (Object c: children) {
                results.add(new Proxy(c));
            }
            return results;
        }

        public boolean hasChildren() {
            return subtree.hasChildren(o);
        }

        public IContextObject getContext() {
            return subtree.getContext(o);
        }

        public Object getColumnValue(int columnIndex) {
            int col = columnMapping[columnIndex];
            if (col >= 0)
                return subtree.getColumnValue(o, col);
            else
                return null;
        }

        public URL getIcon() {
            if (subtree instanceof IIconProvider) {
                return ((IIconProvider)subtree).getIcon(o);
            } else {
                return null;
            }
        }

        public String prefix() {
            if (subtree instanceof IDecorator) {
                return ((IDecorator)subtree).prefix(o);
            } else {
                return null;
            }
        }

        public String suffix() {
            if (subtree instanceof IDecorator) {
                return ((IDecorator)subtree).suffix(o);
            } else {
                return null;
            }
        }
    }

    public static final IDecorator GRAFT_DECORATOR = new IDecorator() {
        public String prefix(Object row) {
           if (row instanceof Proxy) {
               Proxy pRow = (Proxy) row;
               return pRow.prefix();
           } else {
               return null;
           }
        }

        public String suffix(Object row) {
            if (row instanceof Proxy) {
                Proxy pRow = (Proxy) row;
                return pRow.suffix();
            } else {
                return null;
            }
         }
    };
}
