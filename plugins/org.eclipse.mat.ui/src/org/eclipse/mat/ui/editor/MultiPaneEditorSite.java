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
package org.eclipse.mat.ui.editor;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;

public class MultiPaneEditorSite implements IEditorSite
{

    private IEditorPart editor;

    private MultiPaneEditor multiPageEditor;

    private ISelectionProvider selectionProvider = null;

    private ISelectionChangedListener selectionChangedListener = null;

    public MultiPaneEditorSite(MultiPaneEditor multiPageEditor, IEditorPart editor)
    {
        if (multiPageEditor == null)
            throw new NullPointerException();
        if (editor == null)
            throw new NullPointerException();

        this.multiPageEditor = multiPageEditor;
        this.editor = editor;
    }

    public IEditorActionBarContributor getActionBarContributor()
    {
        return multiPageEditor.getEditorSite().getActionBarContributor();
    }

    public IActionBars getActionBars()
    {
        return multiPageEditor.getEditorSite().getActionBars();
    }

    public IEditorPart getEditor()
    {
        return editor;
    }

    public String getId()
    {
        return ""; //$NON-NLS-1$
    }

    public MultiPaneEditor getMultiPageEditor()
    {
        return multiPageEditor;
    }

    public IWorkbenchPage getPage()
    {
        return getMultiPageEditor().getSite().getPage();
    }

    public String getPluginId()
    {
        return ""; //$NON-NLS-1$
    }

    public String getRegisteredName()
    {
        return ""; //$NON-NLS-1$
    }

    private ISelectionChangedListener getSelectionChangedListener()
    {
        if (selectionChangedListener == null)
        {
            selectionChangedListener = new ISelectionChangedListener()
            {
                public void selectionChanged(SelectionChangedEvent event)
                {
                    MultiPaneEditorSite.this.handleSelectionChanged(event);
                }
            };
        }
        return selectionChangedListener;
    }

    public ISelectionProvider getSelectionProvider()
    {
        return selectionProvider;
    }

    public Shell getShell()
    {
        return getMultiPageEditor().getSite().getShell();
    }

    public IWorkbenchWindow getWorkbenchWindow()
    {
        return getMultiPageEditor().getSite().getWorkbenchWindow();
    }

    protected void handleSelectionChanged(SelectionChangedEvent event)
    {
        ISelectionProvider parentProvider = getMultiPageEditor().getSite().getSelectionProvider();
        if (parentProvider instanceof MultiPaneEditorSelectionProvider)
        {
            SelectionChangedEvent newEvent = new SelectionChangedEvent(parentProvider, event.getSelection());
            ((MultiPaneEditorSelectionProvider) parentProvider).fireSelectionChanged(newEvent);
        }
    }

    public void registerContextMenu(String menuID, MenuManager menuMgr, ISelectionProvider selectionProvider)
    {
        getMultiPageEditor().getSite().registerContextMenu(menuID, menuMgr, selectionProvider);
    }

    public void registerContextMenu(MenuManager menuManager, ISelectionProvider selectionProvider)
    {
        getMultiPageEditor().getSite().registerContextMenu(menuManager, selectionProvider);
    }

    public final void registerContextMenu(String menuId, MenuManager menuMgr, ISelectionProvider selectionProvider,
                    boolean includeEditorInput)
    {}

    public final void registerContextMenu(MenuManager menuManager, ISelectionProvider selectionProvider,
                    boolean includeEditorInput)
    {}

    public void setSelectionProvider(ISelectionProvider provider)
    {
        ISelectionProvider oldSelectionProvider = selectionProvider;
        selectionProvider = provider;
        if (oldSelectionProvider != null)
        {
            oldSelectionProvider.removeSelectionChangedListener(getSelectionChangedListener());
        }
        if (selectionProvider != null)
        {
            selectionProvider.addSelectionChangedListener(getSelectionChangedListener());
        }
    }

    @SuppressWarnings("unchecked")
    public Object getAdapter(Class adapter)
    {
        return null;
    }

    public IWorkbenchPart getPart()
    {
        return editor;
    }

    /*
     * new methods available in eclipse 3.2
     */
    @SuppressWarnings("unchecked")
    public Object getService(Class api)
    {
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean hasService(Class api)
    {
        return false;
    }

    @SuppressWarnings("deprecation")
    public org.eclipse.ui.IKeyBindingService getKeyBindingService()
    {
        return null;
    }

}
