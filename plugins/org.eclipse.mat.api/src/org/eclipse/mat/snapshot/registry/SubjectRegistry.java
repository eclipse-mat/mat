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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;

public abstract class SubjectRegistry<D> extends RegistryReader<D>
{
    private Map<String, D> resolvers = new HashMap<String, D>();

    @Override
    public final D createDelegate(IConfigurationElement configElement) throws CoreException
    {
        D resolver = doCreateDelegate(configElement);

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
                            MessageUtil.format(Messages.SubjectRegistry_ErrorMsg_MissingSubjectAnnotation, resolver
                                            .getClass().getName()));
        }

        return resolver;
    }

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    protected String[] extractSubjects(D instance)
    {
        Subjects subjects = instance.getClass().getAnnotation(Subjects.class);
        if (subjects != null)
            return subjects.value();

        Subject subject = instance.getClass().getAnnotation(Subject.class);
        return subject != null ? new String[] { subject.value() } : null;
    }

    @Override
    protected final void removeDelegate(D delegate)
    {
        for (Iterator<D> iter = resolvers.values().iterator(); iter.hasNext();)
        {
            D d = iter.next();
            if (d == delegate)
                iter.remove();
        }
    }

    protected abstract D doCreateDelegate(IConfigurationElement configElement) throws CoreException;

    public final D lookup(String key)
    {
        return resolvers.get(key);
    }

    public final Collection<String> getKeys()
    {
        return resolvers.keySet();
    }
}
