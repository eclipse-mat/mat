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
package org.eclipse.mat.snapshot.registry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.snapshot.extension.ITroubleTicketResolver;
import org.eclipse.mat.util.RegistryReader;

public class TroubleTicketResolverRegistry extends RegistryReader<ITroubleTicketResolver>
{
    private static final TroubleTicketResolverRegistry instance = new TroubleTicketResolverRegistry();

    public static final TroubleTicketResolverRegistry instance()
    {
        return instance;
    }

    private TroubleTicketResolverRegistry()
    {
        init(MATPlugin.getDefault().getExtensionTracker(), MATPlugin.PLUGIN_ID + ".ticketResolver"); //$NON-NLS-1$
    }

    @Override
    protected ITroubleTicketResolver createDelegate(IConfigurationElement configElement) throws CoreException
    {
        return (ITroubleTicketResolver) configElement.createExecutableExtension("impl"); //$NON-NLS-1$
    }

    @Override
    protected void removeDelegate(ITroubleTicketResolver delegate)
    {}

}
