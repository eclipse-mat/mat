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

import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.ServletExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class GenericServletExtractor implements ServletExtractor {
    public String getName(IObject object) {
        JavaEEPlugin.warning("Do not know how to get name for " + object.getDisplayName());
        return "- Unknown -";
    }

	public String getApplication(IObject object) {
        JavaEEPlugin.warning("Do not know how to get application for " + object.getDisplayName());
        return "- Unknown -";
	}

    public String getServletClass(IObject object) {
        return object.getClazz().getName();
    }
}
