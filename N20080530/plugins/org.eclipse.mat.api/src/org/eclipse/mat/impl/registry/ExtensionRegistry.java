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
package org.eclipse.mat.impl.registry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.impl.test.IOutputter;
import org.eclipse.mat.impl.test.OutputterRegistry;


public class ExtensionRegistry
{
    private final Map<Class<?>, AbstractRegistry<?>> registries = new HashMap<Class<?>, AbstractRegistry<?>>();

    private static final ExtensionRegistry instance = new ExtensionRegistry();

    public static ExtensionRegistry instance()
    {
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <S> AbstractRegistry<S> get(Class<S> type)
    {
        return (AbstractRegistry<S>) registries.get(type);
    }

    private ExtensionRegistry()
    {
        registries.put(IOutputter.class, new OutputterRegistry(IOutputter.class));

        List<Exception> problems = new ArrayList<Exception>();
        for (AbstractRegistry<?> r : registries.values())
        {
            try
            {
                r.registerDefaultServices(problems);
            }
            catch (IOException e)
            {
                problems.add(e);
            }
        }

        for (Exception problem : problems)
            Logger.getLogger(ExtensionRegistry.class.getName()).log(Level.SEVERE, problem.getMessage(), problem);
    }

    public synchronized List<Exception> register(File fileOrDirectory)
    {
        List<Exception> problems = new ArrayList<Exception>();

        for (AbstractRegistry<?> registry : registries.values())
            registry.register(fileOrDirectory);

        return problems.isEmpty() ? null : problems;
    }

    public synchronized void unregister(File fileOrDirectory)
    {
        for (AbstractRegistry<?> registry : registries.values())
            registry.unregister(fileOrDirectory);
    }

    public synchronized void unregisterAll()
    {
        for (AbstractRegistry<?> registry : registries.values())
            registry.unregisterAll();
    }

    public synchronized List<Exception> refresh()
    {
        List<Exception> problems = new ArrayList<Exception>();
        for (AbstractRegistry<?> registry : registries.values())
        {
            List<Exception> p = registry.refresh();
            if (p != null)
                problems.addAll(p);
        }

        return problems.isEmpty() ? null : problems;
    }

}
