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

import org.eclipse.mat.impl.query.SimpleServiceLoader;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;


public abstract class AbstractRegistry<S>
{
    private final Map<SimpleServiceLoader<S>, List<S>> loader2service = new HashMap<SimpleServiceLoader<S>, List<S>>();
    private final Class<S> type;

    protected AbstractRegistry(Class<S> type)
    {
        this.type = type;
    }

    protected abstract void doRefresh();

    protected abstract boolean doAdd(S instance) throws Exception;

    protected abstract void doRemove(S instance);

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    protected String[] extractSubjects(S instance)
    {
        Subjects subjects = instance.getClass().getAnnotation(Subjects.class);
        if (subjects != null)
            return subjects.value();
        
        Subject subject = instance.getClass().getAnnotation(Subject.class);
        return subject != null ? new String[] { subject.value()} : null;
    }
    
    // //////////////////////////////////////////////////////////////
    // default register / unregistering
    // //////////////////////////////////////////////////////////////

    protected final synchronized void registerDefaultServices(List<Exception> problems) throws IOException
    {
        SimpleServiceLoader<S> loader = SimpleServiceLoader.load(getClass().getClassLoader(), type, problems);
        internalRegister(loader, problems, false);
    }

    protected final synchronized List<Exception> register(File fileOrDirectory)
    {
        List<Exception> problems = new ArrayList<Exception>();
        internalRegister(fileOrDirectory, problems);
        return problems.isEmpty() ? null : problems;
    }

    protected final synchronized void unregister(File fileOrDirectory)
    {
        for (SimpleServiceLoader<S> loader : loader2service.keySet())
        {
            if (fileOrDirectory.equals(loader.getFileOrDirectory()))
            {
                for (S descriptor : loader2service.get(loader))
                    doRemove(descriptor);

                loader2service.remove(loader);
                doRefresh();
                return;
            }
        }
    }

    protected final synchronized void unregisterAll()
    {
        for (List<S> set : loader2service.values())
        {
            for (S descriptor : set)
            {
                doRemove(descriptor);
            }
        }

        loader2service.clear();
        doRefresh();
    }

    protected final synchronized List<Exception> refresh()
    {
        if (loader2service.isEmpty())
            return null;

        List<File> sources = new ArrayList<File>();

        for (Map.Entry<SimpleServiceLoader<S>, List<S>> entry : loader2service.entrySet())
        {
            File source = entry.getKey().getFileOrDirectory();
            if (source == null)
                continue;
            
            sources.add(source);
            for (S descriptor : entry.getValue())
                doRemove(descriptor);
        }

        loader2service.clear();
        doRefresh();

        List<Exception> problems = new ArrayList<Exception>();
        for (File file : sources)
            internalRegister(file, problems);

        return problems.isEmpty() ? null : problems;
    }

    private final void internalRegister(File fileOrDirectory, List<Exception> problems)
    {
        SimpleServiceLoader<S> loader;

        try
        {
            loader = SimpleServiceLoader.load(fileOrDirectory, type, problems);
        }
        catch (IOException e)
        {
            problems.add(e);
            return;
        }

        internalRegister(loader, problems, true);
    }

    private final void internalRegister(SimpleServiceLoader<S> loader, List<Exception> problems, boolean isDynamic)
    {
        List<S> loadedQueries = new ArrayList<S>();
        for (S query : loader)
        {
            try
            {
                if (doAdd(query))
                    loadedQueries.add(query);
            }
            catch (Exception e)
            {
                problems.add(e);
            }
        }
        
        if (isDynamic)
            loader2service.put(loader, loadedQueries);
    }

}
