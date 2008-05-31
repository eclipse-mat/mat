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
package org.eclipse.mat.ui.rcp.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.WorkbenchActivityHelper;
import org.eclipse.ui.views.IViewDescriptor;
import org.eclipse.ui.views.IViewRegistry;

public class ShowViewMenu extends ContributionItem
{
    private IWorkbenchWindow window;
    protected boolean dirty = true;

    private Map<String, IAction> actions = new HashMap<String, IAction>(21);

    // Maps pages to a list of opened views
    private Map<IWorkbenchPage, ArrayList<String>> openedViews = new HashMap<IWorkbenchPage, ArrayList<String>>();

    private IMenuListener menuListener = new IMenuListener()
    {
        public void menuAboutToShow(IMenuManager manager)
        {
            manager.markDirty();
            dirty = true;
        }
    };

    public ShowViewMenu(IWorkbenchWindow window)
    {
        this.window = window;
    }

    public void fill(Menu menu, int index)
    {
        if (getParent() instanceof MenuManager)
        {
            ((MenuManager) getParent()).addMenuListener(menuListener);
        }

        if (!dirty) { return; }

        MenuManager manager = new MenuManager();
        fillMenu(manager);
        IContributionItem items[] = manager.getItems();
        if (items.length == 0)
        {
            MenuItem item = new MenuItem(menu, SWT.NONE, index++);
            item.setText("No applicable view");
            item.setEnabled(false);
        }
        else
        {
            for (int i = 0; i < items.length; i++)
            {
                items[i].fill(menu, index++);
            }
        }
        dirty = false;
    }

    private void fillMenu(IMenuManager innerMgr)
    {
        // Remove all.
        innerMgr.removeAll();

        // If no page disable all.
        IWorkbenchPage page = window.getActivePage();
        if (page == null) { return; }

        // If no active perspective disable all
        if (page.getPerspective() == null) { return; }

        // Get visible actions.
        List<String> viewIds = Arrays.asList(page.getShowViewShortcuts());

        // add all open views
        viewIds = addOpenedViews(page, viewIds);

        List<IAction> actions = new ArrayList<IAction>(viewIds.size());
        for (String id : viewIds)
        {
            if (id.equals("org.eclipse.ui.internal.introview"))
            {
                continue;
            }
            IAction action = getAction(id);
            if (action != null)
            {
                if (WorkbenchActivityHelper.filterItem(action))
                {
                    continue;
                }
                actions.add(action);
            }
        }

        for (IAction action : actions)
        {
            innerMgr.add(action);
        }
    }

    private List<String> addOpenedViews(IWorkbenchPage page, List<String> actions)
    {
        ArrayList<String> views = getParts(page);
        ArrayList<String> result = new ArrayList<String>(views.size() + actions.size());

        for (int i = 0; i < actions.size(); i++)
        {
            String element = actions.get(i);
            if (result.indexOf(element) < 0)
            {
                result.add(element);
            }
        }
        for (int i = 0; i < views.size(); i++)
        {
            String element = views.get(i);
            if (result.indexOf(element) < 0)
            {
                result.add(element);
            }
        }
        return result;
    }

    private ArrayList<String> getParts(IWorkbenchPage page)
    {
        ArrayList<String> parts = (ArrayList<String>) openedViews.get(page);
        if (parts == null)
        {
            parts = new ArrayList<String>();
            openedViews.put(page, parts);
        }
        return parts;
    }

    /**
     * Returns the action for the given view id, or null if not found.
     */
    private IAction getAction(String id)
    {
        // Keep a cache, rather than creating a new action each time,
        // so that image caching in ActionContributionItem works.
        IAction action = (IAction) actions.get(id);
        if (action == null)
        {
            IViewRegistry reg = PlatformUI.getWorkbench().getViewRegistry(); 
            IViewDescriptor desc = reg.find(id);
            if (desc != null)
            {
                action = new ShowViewAction(window, desc);
                action.setActionDefinitionId(id);
                actions.put(id, action);
            }
        }
        return action;
    }

    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Overridden to always return true and force dynamic menu building.
     */
    public boolean isDynamic()
    {
        return true;
    }

}
