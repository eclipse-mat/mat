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
package org.eclipse.mat.javaee.impl.servlet.catalina;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.ServletConfigExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class CatalinaWrapperFacadeServletConfigExtractor implements ServletConfigExtractor {
    public String getName(IObject config) {
        try {
            IObject wrapper = (IObject)config.resolveValue("wrapper");
            if (wrapper != null)
                return ServletExtractors.getServletConfigExtractor(wrapper).getName(wrapper);

            IObject subconfig = (IObject)config.resolveValue("config");
            if (subconfig != null)
                return ServletExtractors.getServletConfigExtractor(subconfig).getName(subconfig);

            JavaEEPlugin.warning("Unhandled Catalina wrapper facade for name for " + config.getDisplayName());
            return null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

	public String getApplication(IObject config) {
        try {
        	IObject standardContext = (IObject)config.resolveValue("context.context.context");
        	if (standardContext != null) {
        		return ((IObject)standardContext.resolveValue("name")).getClassSpecificName();
        	}

            IObject subconfig = (IObject)config.resolveValue("config");
            if (subconfig != null)
                return ServletExtractors.getServletConfigExtractor(subconfig).getName(subconfig);

            JavaEEPlugin.warning("Unhandled Catalina wrapper facade for app for " + config.getDisplayName());
            return null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
	}
}
