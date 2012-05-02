/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup.Element;

public abstract class QueryBrowserProvider
{
    protected QueryBrowserPopup.Element[] sortedElements;

    public abstract String getName();

    public abstract QueryBrowserPopup.Element[] getElements();

    public QueryBrowserPopup.Element[] getElementsSorted()
    {
        if (sortedElements == null)
        {
            sortedElements = getElements();
            Arrays.sort(sortedElements, new Comparator<Element>()
            {
                public int compare(Element o1, Element o2)
                {
                    return o1.getLabel().compareTo(o2.getLabel());
                }
            });
        }
        return sortedElements;
    }

    public static boolean unsuitableSubjects(QueryDescriptor query, IQueryContext queryContext)
    {
        final String cls[];
        boolean skip;
        cls = extractSubjects(query);
        if (cls != null)
        {
            ISnapshot snapshot =  (ISnapshot)queryContext.get(ISnapshot.class, null);
            int count = 0;
            for (String cn : cls)
            {
                try
                {
                    Collection<IClass> ss = snapshot.getClassesByName(cn, false);
                    if (ss == null || ss.isEmpty())
                        continue;
                    count += ss.size();
                    break;
                }
                catch (SnapshotException e)
                {}
            }
            skip = (count == 0);
        }
        else
        {
            skip = false;
        }
        return skip;
    }

    private static String[] extractSubjects(QueryDescriptor query)
    {
        final String[] cls;
        Subjects subjects = query.getCommandType().getAnnotation(Subjects.class);
        if (subjects != null) 
        {
            cls = subjects.value();
        }
        else
        {
            Subject s = query.getCommandType().getAnnotation(Subject.class);
            if (s != null)
            {
                cls = new String[] { s.value() };
            }
            else
            {
                cls = null;
            }
        }
        return cls;
    }

}
