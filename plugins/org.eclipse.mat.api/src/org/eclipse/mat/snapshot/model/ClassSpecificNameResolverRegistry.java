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
package org.eclipse.mat.snapshot.model;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.ApiPlugin;
import org.eclipse.mat.impl.registry.RegistryReader;
import org.eclipse.mat.snapshot.SnapshotException;

/**
 * Registry for name resolvers which resolve the names for objects of specific
 * classes (found in an snapshot), e.g. String (where the char[] is evaluated)
 * or a specific class loader (where the appropriate field holding its name and
 * thereby deployment unit is evaluated).
 */
public class ClassSpecificNameResolverRegistry extends RegistryReader<IClassSpecificNameResolver>
{
    // //////////////////////////////////////////////////////////////
    // Singleton
    // //////////////////////////////////////////////////////////////

    private static ClassSpecificNameResolverRegistry instance = new ClassSpecificNameResolverRegistry();

    public static ClassSpecificNameResolverRegistry instance()
    {
        return instance;
    }

    // //////////////////////////////////////////////////////////////
    // registry methods
    // //////////////////////////////////////////////////////////////

    private Map<String, IClassSpecificNameResolver> resolvers;

    private ClassSpecificNameResolverRegistry()
    {
        resolvers = new HashMap<String, IClassSpecificNameResolver>();
        init(ApiPlugin.getDefault().getExtensionTracker(), ApiPlugin.PLUGIN_ID + ".nameResolver");
    }

    @Override
    public IClassSpecificNameResolver createDelegate(IConfigurationElement configElement)
    {
        try
        {
            IClassSpecificNameResolver resolver = (IClassSpecificNameResolver) configElement
                            .createExecutableExtension("impl");

            String[] subjects = extractSubjects(resolver);
            if (subjects != null && subjects.length > 0)
            {
                for (int ii = 0; ii < subjects.length; ii++)
                    resolvers.put(subjects[ii], resolver);
            }
            else
            {
                Logger.getLogger(getClass().getName()).log(
                                Level.WARNING,
                                MessageFormat.format("Resolver without subjects: ''{0}''", resolver.getClass()
                                                .getName()));
            }

            return resolver;
        }
        catch (CoreException e)
        {
            Logger.getLogger(getClass().getName()).log(
                            Level.SEVERE,
                            MessageFormat.format("Error while creating name resolver ''{0}''", configElement
                                            .getAttribute("impl")), e);
            return null;

        }
    }

    @Override
    protected void removeDelegate(IClassSpecificNameResolver delegate)
    {
        for (Iterator<IClassSpecificNameResolver> iter = resolvers.values().iterator(); iter.hasNext();)
        {
            IClassSpecificNameResolver r = iter.next();
            if (r == delegate)
                iter.remove();
        }
    }

    /**
     * Register class specific name resolver.
     * 
     * @param className
     *            class name for which the class specific name resolver should
     *            be used
     * @param resolver
     *            class specific name resolver
     * @deprecated Use default extension mechanism: just implement interface and
     *             register location via UI
     */
    @Deprecated
    public static void registerResolver(String className, IClassSpecificNameResolver resolver)
    {
        instance().resolvers.put(className, resolver);
    }

    /**
     * Resolve name of the given snapshot object or return null if it can't be
     * resolved.
     * 
     * @param object
     *            snapshot object for which the name should be resolved
     * @return name of the given snapshot object or null if it can't be resolved
     */
    public static String resolve(IObject object)
    {
        if (object == null)
            throw new NullPointerException("No object given to resolve class specific name for.");

        return instance().doResolve(object);
    }

    private String doResolve(IObject object)
    {
        try
        {
            IClass clazz = object.getClazz();
            while (clazz != null)
            {
                IClassSpecificNameResolver resolver = resolvers.get(clazz.getName());
                if (resolver != null) { return resolver.resolve(object); }
                clazz = clazz.getSuperClass();
            }
            return null;
        }
        catch (RuntimeException e)
        {
            Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(Level.SEVERE,
                            MessageFormat.format("Error resolving name of {0}", object.getTechnicalName()), e);
            return null;
        }
        catch (SnapshotException e)
        {
            Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(Level.SEVERE,
                            MessageFormat.format("Error resolving name of {0}", object.getTechnicalName()), e);
            return null;
        }
    }

}
