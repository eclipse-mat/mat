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
package org.eclipse.mat.javaee.deployment.api;

import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;

public interface DeploymentExtractor {
    String getDeploymentName(IObject deployment);
    DeploymentType getDeploymentType(IObject deployment);
    IClassLoader getClassloader(IObject deployment);
}
