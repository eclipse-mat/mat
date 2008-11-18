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
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;

public final class RequestDetailResolverRegistry extends SubjectRegistry<IRequestDetailsResolver>
{
    private static final RequestDetailResolverRegistry INSTANCE = new RequestDetailResolverRegistry();

    public static final RequestDetailResolverRegistry instance()
    {
        return INSTANCE;
    }

    private RequestDetailResolverRegistry()
    {
        init(MATPlugin.getDefault().getExtensionTracker(), MATPlugin.PLUGIN_ID + ".requestResolver");
    }

    @Override
    protected IRequestDetailsResolver doCreateDelegate(IConfigurationElement configElement) throws CoreException
    {
        return (IRequestDetailsResolver) configElement.createExecutableExtension("impl");
    }

}
