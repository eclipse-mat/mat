/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - refactor to use SubjectRegistry
 *******************************************************************************/
package org.eclipse.mat.snapshot.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

/**
 * Registry for name resolvers which resolve the names for objects of specific
 * classes (found in an snapshot), e.g. String (where the char[] is evaluated)
 * or a specific class loader (where the appropriate field holding its name and
 * thereby deployment unit is evaluated).
 */
public final class ClassSpecificNameResolverRegistry
{
    // //////////////////////////////////////////////////////////////
    // Singleton
    // //////////////////////////////////////////////////////////////

    /** inner class because RegistryReader API not public */
    private static class RegistryImpl extends SubjectRegistry<IClassSpecificNameResolver>
    {
        // For registerResolver()
        private Map<String, IClassSpecificNameResolver> resolvers;

        public RegistryImpl()
        {
            // For registerResolver()
            resolvers = new HashMap<String, IClassSpecificNameResolver>();
            init(MATPlugin.getDefault().getExtensionTracker(), MATPlugin.PLUGIN_ID + ".nameResolver"); //$NON-NLS-1$
        }

        @Override
        protected IClassSpecificNameResolver doCreateDelegate(IConfigurationElement configElement) throws CoreException
        {
            return (IClassSpecificNameResolver) configElement.createExecutableExtension("impl"); //$NON-NLS-1$
        }

        private String doResolve(IObject object)
        {
            try
            {
                IClass clazz = object.getClazz();
                while (clazz != null)
                {
                    // For registerResolver()
                    IClassSpecificNameResolver resolver = resolvers.get(clazz.getName());
                    if (resolver != null) { return resolver.resolve(object); }

                    resolver = lookup(clazz.getName());
                    if (resolver != null) { return resolver.resolve(object); }
                    clazz = clazz.getSuperClass();
                }
                return null;
            }
            catch (RuntimeException e)
            {
                Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(
                                Level.SEVERE,
                                MessageUtil.format(Messages.ClassSpecificNameResolverRegistry_ErrorMsg_DuringResolving,
                                                object.getTechnicalName()), e);
                return null;
            }
            catch (SnapshotException e)
            {
                Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(
                                Level.SEVERE,
                                MessageUtil.format(Messages.ClassSpecificNameResolverRegistry_ErrorMsg_DuringResolving,
                                                object.getTechnicalName()), e);
                return null;
            }
        }

    }

    private static ClassSpecificNameResolverRegistry instance = new ClassSpecificNameResolverRegistry();

    public static ClassSpecificNameResolverRegistry instance()
    {
        return instance;
    }

    // //////////////////////////////////////////////////////////////
    // registry methods
    // //////////////////////////////////////////////////////////////

    private RegistryImpl registry;

    private ClassSpecificNameResolverRegistry()
    {
        registry = new RegistryImpl();
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
        instance().registry.resolvers.put(className, resolver);
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
            throw new NullPointerException(Messages.ClassSpecificNameResolverRegistry_Error_MissingObject);

        return instance().registry.doResolve(object);
    }

}
