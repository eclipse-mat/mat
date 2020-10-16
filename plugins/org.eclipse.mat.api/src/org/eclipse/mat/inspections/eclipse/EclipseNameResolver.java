/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Chris Grindstaff
 *******************************************************************************/
package org.eclipse.mat.inspections.eclipse;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

public class EclipseNameResolver
{
    @Subject("org.eclipse.core.runtime.adaptor.EclipseClassLoader")
    public static class EclipseClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IObject s = (IObject) obj.resolveValue("hostdata.symbolicName"); //$NON-NLS-1$
            return s != null ? s.getClassSpecificName() : null;
        }

    }

    @Subject("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader")
    public static class EclipseDefaultClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IObject s = (IObject) obj.resolveValue("manager.data.symbolicName"); //$NON-NLS-1$
            return s != null ? s.getClassSpecificName() : null;
        }

    }

    @Subject("org.eclipse.osgi.internal.loader.EquinoxClassLoader")
    public static class EquinoxClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IObject s = (IObject) obj.resolveValue("generation.revision.symbolicName"); //$NON-NLS-1$
            return s != null ? s.getClassSpecificName() : null;
        }

    }

    @Subject("org.eclipse.equinox.launcher.Main$StartupClassLoader")
    public static class StartupClassLoaderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            return Messages.EclipseNameResolver_EquinoxStartupClassLoader;
        }

    }

    @Subject("org.eclipse.swt.graphics.RGB")
    public static class RGBResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            Integer red = (Integer) obj.resolveValue("red"); //$NON-NLS-1$
            Integer green = (Integer) obj.resolveValue("green"); //$NON-NLS-1$
            Integer blue = (Integer) obj.resolveValue("blue"); //$NON-NLS-1$

            if (red == null || green == null || blue == null)
                return null;

            return MessageUtil.format(Messages.EclipseNameResolver_RGB, red, green, blue);
        }
    }
    
    /*
     * Contributed in bug 273915
     */
    @Subjects( { "org.eclipse.swt.graphics.Point", //
                    "java.awt.Point" })
    public static class PointResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            Object x = obj.resolveValue("x"); //$NON-NLS-1$
            Object y = obj.resolveValue("y"); //$NON-NLS-1$
            return MessageUtil.format(Messages.EclipseNameResolver_Point, x, y);
        }
    }

    /*
     * Contributed in bug 273915
     */
    @Subjects( { "org.eclipse.swt.graphics.Rectangle", //
                    "java.awt.Rectangle" })
    public static class RectangleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            Object x = obj.resolveValue("x"); //$NON-NLS-1$
            Object y = obj.resolveValue("y"); //$NON-NLS-1$
            Object width = obj.resolveValue("width"); //$NON-NLS-1$
            Object height = obj.resolveValue("height"); //$NON-NLS-1$
            return MessageUtil.format(Messages.EclipseNameResolver_Rectangle, x, y, width, height);
        }
    }

    @Subject("org.osgi.framework.Version")
    public static class OSGiFrameworkVersion implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            Object s = obj.resolveValue("versionString"); //$NON-NLS-1$
            if (s instanceof IObject)
            {
                String v = ((IObject)s).getClassSpecificName();
                if (v != null)
                    return v;
            }
            Object major = obj.resolveValue("major"); //$NON-NLS-1$
            Object minor = obj.resolveValue("minor"); //$NON-NLS-1$
            Object micro = obj.resolveValue("micro"); //$NON-NLS-1$
            if (major instanceof Integer && minor instanceof Integer && micro instanceof Integer)
            {
                Object q = obj.resolveValue("qualifier"); //$NON-NLS-1$
                if (q instanceof IObject)
                {
                    String v = ((IObject)q).getClassSpecificName();
                    if (v != null && v.length() > 0)
                        return major + "." + minor + "." + micro + "." + v; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                return major + "." + minor + "." + micro; //$NON-NLS-1$ //$NON-NLS-2$
            }
            return null;
        }

    }
}