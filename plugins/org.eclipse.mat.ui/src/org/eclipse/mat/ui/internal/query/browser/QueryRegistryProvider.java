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
package org.eclipse.mat.ui.internal.query.browser;

import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.impl.query.CategoryDescriptor;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.ImageHelper;


public class QueryRegistryProvider extends QueryBrowserProvider
{
    CategoryDescriptor category;

    public QueryRegistryProvider(CategoryDescriptor c)
    {
        this.category = c;
    }

    @Override
    public QueryBrowserPopup.Element[] getElements()
    {
        List<QueryDescriptor> queries = category.getQueries();
        QueryBrowserPopup.Element[] answer = new QueryBrowserPopup.Element[queries.size()];

        int index = 0;
        for (QueryDescriptor query : queries)
        {
            answer[index++] = new CQQElement(query);
        }

        return answer;
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

        public void execute(HeapEditor editor) throws SnapshotException
        {
            QueryExecution.execute(editor, query);
        }

        public ImageDescriptor getImageDescriptor()
        {
            return ImageHelper.getImageDescriptor(query);
        }

        public String getLabel()
        {
            if (label == null)
            {
                StringBuilder b = new StringBuilder(128).append(query.getName());

                String shortDescription = query.getShortDescription();
                if (shortDescription != null)
                    b.append("  -  ").append(shortDescription);

                label = b.toString();
            }

            return label;
        }

        public String getUsage()
        {
            return query.getUsage();
        }

        public QueryDescriptor getQuery()
        {
            return query;
        }
    }
}
