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
package org.eclipse.mat.eclipse;

import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

public class EclipseNameResolver
{
    @Subject("org.eclipse.core.runtime.adaptor.EclipseClassLoader")
    public static class EclipseClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IPrimitiveArray charArray = (IPrimitiveArray) obj.resolveValue("hostdata.symbolicName.value");
            if (charArray == null)
                return null;
            return charArray.valueString(1024);
        }

    }

	@Subject("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader")
    public static class EclipseDefaultClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IPrimitiveArray charArray = (IPrimitiveArray) obj.resolveValue("manager.data.symbolicName.value");
            if (charArray == null)
                return null;
            return charArray.valueString(1024);
        }

    }
	
	@Subject("org.eclipse.equinox.launcher.Main$StartupClassLoader")
	public static class StartupClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            return "Equinox Startup Class Loader";
        }

    }
}
