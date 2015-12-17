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
package org.eclipse.mat.javaee.impl.servlet;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.ServletConfigExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class HttpServletExtractor extends GenericServletExtractor {
    @Override
    public String getName(IObject object) {
        try {
            IObject config = (IObject)object.resolveValue("config");
            if (config == null) {
                JavaEEPlugin.warning("Did not find servlet config for name for " + object.getDisplayName());
                return null;
            }
            ServletConfigExtractor extractor = ServletExtractors.getServletConfigExtractor(config);
            return (extractor != null) ? extractor.getName(config) : null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    @Override
	public String getApplication(IObject object) {
        try {
            IObject config = (IObject)object.resolveValue("config");
            if (config == null) {
                JavaEEPlugin.warning("Did not find servlet config for application for " + object.getDisplayName());
                return null;
            }
            ServletConfigExtractor extractor = ServletExtractors.getServletConfigExtractor(config);
            return (extractor != null) ? extractor.getApplication(config) : null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
	}
}
