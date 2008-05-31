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
package org.eclipse.mat.impl.test;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.impl.registry.AbstractRegistry;
import org.eclipse.mat.impl.registry.ExtensionRegistry;


public class OutputterRegistry extends AbstractRegistry<IOutputter>
{
    public static OutputterRegistry instance()
    {
        return (OutputterRegistry) ExtensionRegistry.instance().get(IOutputter.class);
    }

    Map<String, IOutputter> outputters = new HashMap<String, IOutputter>();

    public OutputterRegistry(Class<IOutputter> type)
    {
        super(type);
    }

    public IOutputter get(String type)
    {
        return outputters.get(type);
    }

    @Override
    protected boolean doAdd(IOutputter instance) throws Exception
    {
        String targets[] = extractSubjects(instance);
        if (targets == null)
            return false;

        for (String target : targets)
            if (!outputters.containsKey(target))
                outputters.put(target, instance);

        return true;
    }

    @Override
    protected void doRemove(IOutputter instance)
    {
        String[] targets = extractSubjects(instance);
        if (targets != null)
            for (String target : targets)
                outputters.remove(target);
    }

    @Override
    protected void doRefresh()
    {}
}
