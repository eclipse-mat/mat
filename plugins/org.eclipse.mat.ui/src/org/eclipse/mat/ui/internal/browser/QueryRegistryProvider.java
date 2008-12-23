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
package org.eclipse.mat.ui.internal.browser;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;

public class QueryRegistryProvider extends QueryBrowserProvider
{
    IQueryContext context;
    CategoryDescriptor category;

    public QueryRegistryProvider(IQueryContext context, CategoryDescriptor c)
    {
        this.context = context;
        this.category = c;
    }

    @Override
    public QueryBrowserPopup.Element[] getElements()
    {
        List<QueryDescriptor> queries = category.getQueries();
        List<QueryBrowserPopup.Element> answer = new ArrayList<QueryBrowserPopup.Element>(queries.size());

        for (QueryDescriptor query : queries)
        {
            if (query.accept(context))
                answer.add(new CQQElement(query));
        }

        return answer.toArray(new QueryBrowserPopup.Element[0]);
    }

    @Override
    public String getName()
    {
        return category.getFullName();
    }

    class CQQElement implements QueryBrowserPopup.Element
    {
        String label;
        QueryDescriptor query;

        public CQQElement(QueryDescriptor query)
        {
            this.query = query;
        }

        public void execute(MultiPaneEditor editor) throws SnapshotException
        {
            QueryExecution.executeQuery(editor, query);
        }

        public ImageDescriptor getImageDescriptor()
        {
            return MemoryAnalyserPlugin.getDefault().getImageDescriptor(query);
        }

        public String getLabel()
        {
            if (label == null)
            {
                StringBuilder b = new StringBuilder(128).append(query.getName());

                String shortDescription = query.getShortDescription();
                if (shortDescription != null)
                    b.append("  -  ").append(shortDescription);//$NON-NLS-1$

                label = b.toString();
            }

            return label;
        }

        public String getUsage()
        {
            return query.getUsage(context);
        }

        public QueryDescriptor getQuery()
        {
            return query;
        }
    }
}
