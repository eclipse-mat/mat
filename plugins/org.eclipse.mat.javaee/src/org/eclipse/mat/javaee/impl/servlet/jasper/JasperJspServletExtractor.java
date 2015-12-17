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
package org.eclipse.mat.javaee.impl.servlet.jasper;

import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.impl.servlet.HttpServletExtractor;
import org.eclipse.mat.snapshot.model.IObject;


public class JasperJspServletExtractor extends HttpServletExtractor {
    private static final String JSP_CLASS_PREFIX = "org.apache.jsp.";

    @Override
    public String getName(IObject object) {
        return getServletClass(object);
    }

    @Override
    public String getServletClass(IObject object) {
        String className = super.getServletClass(object);
        if (!className.startsWith(JSP_CLASS_PREFIX)) {
            JavaEEPlugin.warning("Unhandled JSP class prefix: " + className);
            return className;
        }

        return "JSP: " + className.substring(JSP_CLASS_PREFIX.length()).replace('.', '/').replace('_', '.');
    }

}
