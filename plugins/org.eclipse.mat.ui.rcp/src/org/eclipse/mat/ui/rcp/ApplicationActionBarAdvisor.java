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
package org.eclipse.mat.ui.rcp;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.mat.ui.rcp.actions.AddHistoryToMenuAction;
import org.eclipse.mat.ui.rcp.actions.OpenHelp;
import org.eclipse.mat.ui.rcp.actions.OpenPreferenceAction;
import org.eclipse.mat.ui.rcp.actions.ShowViewMenu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;

public class ApplicationActionBarAdvisor extends ActionBarAdvisor
{

    private IWorkbenchAction exitAction;
    private IWorkbenchAction copyAction;
    private IWorkbenchAction helpAction;
    private IWorkbenchAction welcomeAction;
    
    private Action preferencesAction;
    private IContributionItem openViewAction;
    private IContributionItem historyAction;

    public ApplicationActionBarAdvisor(IActionBarConfigurer configurer)
    {
        super(configurer);
    }

    protected void makeActions(final IWorkbenchWindow window)
    {
        exitAction = ActionFactory.QUIT.create(window);
        register(exitAction);
        copyAction = ActionFactory.COPY.create(window);
        register(copyAction);
        helpAction = new OpenHelp();// ActionFactory.HELP_CONTENTS.create(window);
        register(helpAction);
        welcomeAction = ActionFactory.INTRO.create(window);
        register(welcomeAction);

        preferencesAction = new OpenPreferenceAction();
        openViewAction = new ShowViewMenu(window);
        historyAction = new AddHistoryToMenuAction(window);
    }

    protected void fillMenuBar(IMenuManager menuBar)
    {
        MenuManager fileMenu = new MenuManager("&File", IWorkbenchActionConstants.M_FILE);
        fileMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        fileMenu.add(historyAction);
        fileMenu.add(new GroupMarker(IWorkbenchActionConstants.MRU));
        fileMenu.add(exitAction);
        menuBar.add(fileMenu);

        MenuManager editMenu = new MenuManager("&Edit", IWorkbenchActionConstants.M_EDIT);
        editMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        editMenu.add(copyAction);
        menuBar.add(editMenu);

        MenuManager windowMenu = new MenuManager("&View", IWorkbenchActionConstants.M_WINDOW);
        windowMenu.add(openViewAction);
        windowMenu.add(preferencesAction);
        menuBar.add(windowMenu);

        MenuManager helpMenu = new MenuManager("&Help", IWorkbenchActionConstants.M_HELP);
        helpMenu.add(new Separator(IWorkbenchActionConstants.GROUP_HELP));
        helpMenu.add(welcomeAction);
        helpMenu.add(helpAction);
        menuBar.add(helpMenu);

        menuBar.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

}
