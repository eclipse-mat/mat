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
package org.eclipse.mat.javaee.deployment.query;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.IOverviewNode;
import org.eclipse.mat.javaee.SimpleObjectQuery;
import org.eclipse.mat.javaee.deployment.api.DeploymentExtractor;
import org.eclipse.mat.javaee.deployment.api.DeploymentType;
import org.eclipse.mat.javaee.impl.DeploymentExtractors;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;

@CommandName("javaee_deployments")
public class JavaEEDeploymentQuery extends SimpleObjectQuery<JavaEEDeploymentQuery.DeploymentOverviewNode> {
    static class DeploymentNode {
        private final IObject deployment;
        // TODO: do we need to cache this?
        private final DeploymentExtractor extractor;

        public DeploymentNode(IObject deployment) {
            this(deployment, DeploymentExtractors.getDeploymentExtractor(deployment));
        }

        public DeploymentNode(IObject deployment, DeploymentExtractor deploymentExtractor) {
            this.deployment = deployment;
            this.extractor = deploymentExtractor;
        }

        protected IObject getDeployment() {
            return deployment;
        }

        protected DeploymentExtractor getExtractor() {
            return extractor;
        }

        public String getDeploymentName() {
            return getExtractor().getDeploymentName(deployment);
        }

        public DeploymentType getDeploymentType() {
            return getExtractor().getDeploymentType(deployment);
        }
    }

    static final class DeploymentOverviewNode extends DeploymentNode implements IOverviewNode {
        public DeploymentOverviewNode(IObject deployment) {
            super(deployment);
        }

        public long getRetainedHeap() {
            return getDeployment().getRetainedHeapSize();
        }

        public int getId() {
            return getDeployment().getObjectId();
        }

        public DeploymentClassloaderNode createClassloaderNode() {
            return new DeploymentClassloaderNode(getDeployment(), getExtractor());
        }
    }

    static final class DeploymentClassloaderNode extends DeploymentNode {
        private final IClassLoader classloader;

        public DeploymentClassloaderNode(IObject deployment, DeploymentExtractor deploymentExtractor) {
            super(deployment, deploymentExtractor);
            this.classloader = getExtractor().getClassloader(getDeployment());
        }

        public String getClassloaderName() {
            return (classloader != null) ? classloader.getDisplayName() : null;
        }

        public int getClassloaderId() {
            return (classloader != null) ? classloader.getObjectId() : 0;
        }
    }


    public JavaEEDeploymentQuery() {
        super(DeploymentOverviewNode.class);
    }

    protected ArrayInt findObjects(ISnapshot snapshot) throws SnapshotException {
        return DeploymentExtractors.findDeployments(snapshot);
    }

    protected DeploymentOverviewNode createOverviewNode(IObject obj) {
        return new DeploymentOverviewNode(obj);
    }

    protected boolean overviewHasChildren(DeploymentOverviewNode row) {
        return true;
    }

    protected Column[] getColumns() {
        return new Column[] {
            new Column("Deployment", String.class).decorator(this),
            new Column("Type", DeploymentType.class),
            new Column("Size", Long.class),
        };
    }

    @Override
    protected Object getOverviewColumnValue(DeploymentOverviewNode row, int columnIndex) {
        switch (columnIndex) {
        case 0:
            return row.getDeploymentName();
        case 1:
            return row.getDeploymentType();
        case 2:
            return row.getRetainedHeap();
        default:
            return super.getOverviewColumnValue(row, columnIndex);
        }
    }


    protected List<DeploymentNode> getOverviewChildren(DeploymentOverviewNode row) {
        List<DeploymentNode> children = new ArrayList<DeploymentNode>(1);
        children.add(row.createClassloaderNode());
        return children;
    }

    @Override
    protected boolean hasChildren(Object row) {
        if (row instanceof DeploymentClassloaderNode) {
            return false;
        } else {
            return super.hasChildren(row);
        }
    }

    @Override
    protected Object getColumnValue(Object row, int columnIndex) {
        if (row instanceof DeploymentClassloaderNode) {
            DeploymentClassloaderNode node = (DeploymentClassloaderNode) row;
            switch (columnIndex) {
                case 0:
                    return node.getClassloaderName();
                case 1:
                case 2:
                    return null;
                default:
                    return super.getColumnValue(row, columnIndex);
            }
        } else {
            return super.getColumnValue(row, columnIndex);
        }
    }

    @Override
    protected IContextObject getContext(Object row) {
        if (row instanceof DeploymentClassloaderNode) {
            final DeploymentClassloaderNode node = (DeploymentClassloaderNode) row;
            return new IContextObject() {
                public int getObjectId() {
                    return node.getClassloaderId();
                }
            };
        } else {
            return super.getContext(row);
        }
    }

    @Override
    public URL getIcon(Object row) {
        if (row instanceof DeploymentClassloaderNode) {
            return Icons.CLASSLOADER_INSTANCE;
        } else {
            return super.getIcon(row);
        }
    }

    @Override
    public String prefix(Object row) {
        if (row instanceof DeploymentClassloaderNode) {
            return "<classloader>";
        } else {
            return super.prefix(row);
        }
    }
}
