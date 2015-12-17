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
package org.eclipse.mat.javaee.impl.servlet.undertow;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.ServletConfigExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class UndertowServletConfigExtractor implements ServletConfigExtractor {
    public String getName(IObject config) {
        try {
            IObject info = (IObject)config.resolveValue("servletInfo");
            if (info == null) {
                JavaEEPlugin.warning("Missing 'servletInfo' field from class " + config.getClazz().getName());
                return null;
            }

            return ((IObject)info.resolveValue("name")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

	public String getApplication(IObject config) {
        try {
            IObject contextPath = (IObject)config.resolveValue("servletContext.deploymentInfo.contextPath");
            return contextPath.getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
	}

}
