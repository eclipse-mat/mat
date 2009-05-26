/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.jetty;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;

public class JettyNameResolvers
{

    @Subject("org.mortbay.jetty.webapp.WebAppClassLoader")
    public static class WebAppClassLoaderResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject name = (IObject) object.resolveValue("_name"); //$NON-NLS-1$
            if (name != null)
                return name.getClassSpecificName();

            IObject contextPath = (IObject) object.resolveValue("_context._contextPath"); //$NON-NLS-1$
            return contextPath != null ? contextPath.getClassSpecificName() : null;
        }
    }

    @Subject("org.apache.jasper.servlet.JasperLoader")
    public static class JasperLoaderResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject parent = (IObject) object.resolveValue("parent"); //$NON-NLS-1$
            return parent != null ? MessageFormat.format(Messages.JettyNameResolvers_JSPofWebApp, parent
                            .getClassSpecificName()) : null;
        }
    }
}
