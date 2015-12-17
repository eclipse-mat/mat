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
package org.eclipse.mat.javaee.impl;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.deployment.api.DeploymentExtractor;
import org.eclipse.mat.javaee.impl.deployment.JBoss5DeploymentExtractor;
import org.eclipse.mat.javaee.impl.deployment.JBoss7DeploymentExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class DeploymentExtractors extends Extractors {
    // this is a bit weird since we want to look up deployments from the deployer,
    // not all instances of the class which also gets used for other things

    public static ArrayInt findDeployments(ISnapshot snapshot) throws SnapshotException {
        ArrayInt classBasedDeployments = findObjects(snapshot, DEPLOYMENT_EXTRACTORS);
        ArrayInt jboss5Deployments = JBoss5DeploymentExtractor.findDeployments(snapshot);

        ArrayInt results = new ArrayInt(classBasedDeployments.size() + jboss5Deployments.size());
        results.addAll(classBasedDeployments);
        results.addAll(jboss5Deployments);
        return results;
    }

    public static DeploymentExtractor getDeploymentExtractor(IObject object) {
        boolean jboss5;
        try {
            IClass objClass = object.getClazz();
            jboss5 =  objClass.doesExtend("org.jboss.deployers.vfs.plugins.structure.AbstractVFSDeploymentContext");
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Exception detecting JBoss 5 deployments", e);
            jboss5 = false;
        }

        if (jboss5) {
            return JBOSS_5_DEPLOYMENT_EXTRACTOR;
        } else {
            return getExtractor(object, DEPLOYMENT_EXTRACTORS);
        }
    }


    private static final DeploymentExtractor JBOSS_5_DEPLOYMENT_EXTRACTOR = new JBoss5DeploymentExtractor();
    private static final Map<String, DeploymentExtractor> DEPLOYMENT_EXTRACTORS;
    static {
        DEPLOYMENT_EXTRACTORS = new HashMap<String, DeploymentExtractor>();
        DEPLOYMENT_EXTRACTORS.put("org.jboss.as.server.deployment.DeploymentUnitImpl", new JBoss7DeploymentExtractor());
    }
}
