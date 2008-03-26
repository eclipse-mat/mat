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
package org.eclipse.mat.inspections.query.sidecar;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.ApiPlugin;
import org.eclipse.mat.impl.registry.RegistryReader;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshotAddon;
import org.osgi.framework.Bundle;

public class AddonResolverRegistry extends RegistryReader<AddonResolverRegistry.AddonRecord>
{

    public class AddonRecord
    {

        String name;
        IConfigurationElement configElement;

        public AddonRecord(IConfigurationElement configElement)
        {
            this.configElement = configElement;
            this.name = configElement.getAttribute("name");
        }

        public String getQueryIdentifier() throws InvalidRegistryObjectException, ClassNotFoundException,
                        InstantiationException, IllegalAccessException
        {
            Class<? extends IQuery> queryClass = getQuery().getClass();

            Name n = queryClass.getAnnotation(Name.class);
            String name = n != null ? n.value() : queryClass.getSimpleName();

            return name.toLowerCase().replace(' ', '_');

        }

        @SuppressWarnings("unchecked")
        public <A extends ISnapshotAddon> Class<A> getQueryInterface() throws InvalidRegistryObjectException, ClassNotFoundException 
        {
            Bundle bundle = Platform.getBundle(configElement.getContributor().getName());
            if (bundle == null)
                return null;

            return (Class<A>)bundle.loadClass(configElement.getAttribute("impl"));
           
        }

        public IQuery getQuery() throws InvalidRegistryObjectException, ClassNotFoundException,
                        InstantiationException, IllegalAccessException
        {
            Bundle bundle = Platform.getBundle(configElement.getContributor().getName());
            if (bundle == null)
                return null;

            return (IQuery) bundle.loadClass(configElement.getAttribute("query")).newInstance();
        }

        public String getName()
        {
            return name;
        }

    }

    private static final AddonResolverRegistry instance = new AddonResolverRegistry();

    public static final AddonResolverRegistry instance()
    {
        return instance;
    }

    @Override
    protected AddonRecord createDelegate(IConfigurationElement configElement) throws CoreException
    {
        return new AddonRecord(configElement);
    }

    public AddonResolverRegistry()
    {
        init(ApiPlugin.getDefault().getExtensionTracker(), ApiPlugin.PLUGIN_ID + ".addonResolver");
    }

    @Override
    protected void removeDelegate(AddonRecord delegate)
    {}

}
