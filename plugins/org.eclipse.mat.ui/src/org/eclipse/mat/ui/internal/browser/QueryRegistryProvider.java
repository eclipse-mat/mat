/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - bug fixes
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.IPolicy;
import org.eclipse.mat.ui.util.PaneState;

public class QueryRegistryProvider extends QueryBrowserProvider
{
    IQueryContext context;
    CategoryDescriptor category;
    IPolicy policy;

    public QueryRegistryProvider(IQueryContext context, CategoryDescriptor c, IPolicy policy)
    {
        this.context = context;
        this.category = c;
        this.policy = policy;
    }

    @Override
    public QueryBrowserPopup.Element[] getElements()
    {
        List<QueryDescriptor> queries = category.getQueries();
        List<QueryBrowserPopup.Element> answer = new ArrayList<QueryBrowserPopup.Element>(queries.size());

        for (QueryDescriptor query : queries)
        {
            if (query.accept(context) && policy.accept(query) && !unsuitableSubjects(query, context))
                answer.add(new CQQElement(query));
        }

        return answer.toArray(new QueryBrowserPopup.Element[0]);
    }

    @Override
    public String getName()
    {
        String name = category.getFullName();
        if (name == null) name = Messages.QueryRegistryProvider_Uncategorized;
        return name;
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
            if (policy != null)
            {
                ArgumentSet set = query.createNewArgumentSet(editor.getQueryContext());
                ISnapshot snapshot = (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null);
                policy.fillInObjectArguments(snapshot, query, set);
                AbstractEditorPane active = editor.getActiveEditor();
                PaneState ps = active != null ? active.getPaneState() : null;
                QueryExecution.execute(editor, ps, null, set, !query.isShallow(),
                                false);
            }
            else
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
