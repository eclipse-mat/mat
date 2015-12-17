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

public enum DeploymentType
{
    EAR,
    WAR,
    RAR,
    JAR_EJB,
    JAR_LIBRARY,
    APPLICATION_CLIENT, // for JBoss only?
    UKNOWN
}
