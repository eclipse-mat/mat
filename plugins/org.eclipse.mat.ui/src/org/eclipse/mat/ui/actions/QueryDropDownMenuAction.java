/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG & IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - default policy
 *******************************************************************************/
package org.eclipse.mat.ui.actions;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.actions.ExecuteQueryAction;
import org.eclipse.mat.ui.internal.browser.Policy;
import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup;
import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup.Element;
import org.eclipse.mat.ui.internal.browser.QueryBrowserProvider;
import org.eclipse.mat.ui.internal.browser.QueryHistoryProvider;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.IPolicy;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu.QueryAction;

public class QueryDropDownMenuAction extends EasyToolBarDropDown
{

    private MultiPaneEditor editor;

    private Action queryBrowser;
    
    private IPolicy policy;
    
    private static final IPolicy defaultPolicy = new Policy();
    
    private boolean showHistory;

    private QueryHistoryProvider qhp;

    public QueryDropDownMenuAction(MultiPaneEditor editor)
    {
        this(editor, defaultPolicy, true);
    }

    public QueryDropDownMenuAction(MultiPaneEditor editor, IPolicy policy, boolean showHistory)
    {
        super(Messages.QueryDropDownMenuAction_OpenQueryBrowser, MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY), editor);

        this.editor = editor;
        this.policy = policy;
        this.showHistory = showHistory;

        makeActions();
        if (showHistory)
        {
            qhp = new QueryHistoryProvider(editor.getQueryContext(), policy);
        }
    }

    private void makeActions()
    {
        queryBrowser = new Action(Messages.QueryDropDownMenuAction_SearchQueries)
        {
            @Override
            public void run()
            {
                new QueryBrowserPopup(editor, false, policy).open();
            }
        };
        queryBrowser.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY));
        queryBrowser.setToolTipText(Messages.QueryDropDownMenuAction_SeachQueriesByName);
        queryBrowser.setActionDefinitionId("org.eclipse.mat.ui.query.browser.QueryBrowser");//$NON-NLS-1$
    }

    @Override
    public void contribute(PopupMenu menu)
    {
        addCategorySubMenu(menu, QueryRegistry.instance().getRootCategory(), policy);

        menu.addSeparator();

        menu.add(queryBrowser);

        if (showHistory)
            addHistory(menu);
    }

    private void addCategorySubMenu(PopupMenu menu, CategoryDescriptor category, IPolicy policy)
    {
        for (Object item : category.getChildren())
        {
            if (item instanceof CategoryDescriptor)
            {
                CategoryDescriptor subCategory = (CategoryDescriptor) item;
                PopupMenu categoryItem = new PopupMenu(subCategory.getName());
                menu.add(categoryItem);
                addCategorySubMenu(categoryItem, subCategory, policy);
            }
            else if (item instanceof QueryDescriptor)
            {
                QueryDescriptor query = (QueryDescriptor) item;
                if (query.accept(editor.getQueryContext()) && policy.accept(query) && !QueryBrowserProvider.unsuitableSubjects(query, editor.getQueryContext()))
                {
                    if (defaultPolicy.equals(policy))
                        menu.add(new ExecuteQueryAction(editor, query));
                    else
                        menu.add(new QueryAction(editor, query, policy));
                }
            }
        }
    }

    private void addHistory(PopupMenu menu)
    {
        // Use the query history provider to take account of context, policy and subjects
        List<Element> history = Arrays.asList(qhp.getElements());
        if (!history.isEmpty())
        {
            menu.addSeparator();

            PopupMenu historyMenu = new PopupMenu(Messages.QueryDropDownMenuAction_History);
            historyMenu.setActionDefinitionId("org.eclipse.mat.ui.query.browser.QueryHistory");//$NON-NLS-1$
            menu.add(historyMenu);

            int count = 0;
            for (Element element : history)
            {
                String cmd = element.getLabel();
                historyMenu.add(new ExecuteQueryAction(editor, cmd));

                if (++count == 10)
                    break;
            }

            Action action = new Action(Messages.QueryDropDownMenuAction_All)
            {
                @Override
                public void run()
                {
                    new QueryBrowserPopup(editor, true).open();
                }
            };

            historyMenu.add(action);
        }
    }

}
