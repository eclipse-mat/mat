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
package org.eclipse.mat.javaee.ejb.api;

import java.util.Map;

import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public interface StatefulEjbExtractor {
    String getComponentName(IObject request);
    String getModuleName(IObject request);
    String getApplicationName(IObject request);
    String getDistinctName(IObject request);
    IClass getComponentClass(IObject request);
    Integer getInstanceCount(IObject request);
    Map<IObject, IObject> getInstances(IObject request);
}
