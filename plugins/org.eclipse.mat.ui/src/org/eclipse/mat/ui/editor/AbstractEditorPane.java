/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - MAT Calcite
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;

/**
 * This is used to display a result of a query etc.
 */
public abstract class AbstractEditorPane implements IEditorPart
{
    protected PaneConfiguration configuration;
    protected AbstractEditorPane parentPane;
    protected IEditorInput input;
    protected IEditorSite site;
    protected List<IPropertyListener> listeners = new ArrayList<IPropertyListener>();
    private Menu contextMenu;
    private PaneState paneState;

    void setConfiguration(PaneConfiguration conf)
    {
        this.configuration = conf;
    }

    PaneConfiguration getConfiguration()
    {
        return this.configuration;
    }

    public void init(IEditorSite site, IEditorInput input) throws PartInitException
    {
        this.site = site;
        this.input = input;
    }

    public IEditorInput getEditorInput()
    {
        return input;
    }

    public IEditorSite getEditorSite()
    {
        return site;
    }

    /**
     * @param argument
     */
    public void initWithArgument(Object argument)
    {}

    public void addPropertyListener(IPropertyListener listener)
    {
        listeners.add(listener);
    }

    public void removePropertyListener(IPropertyListener listener)
    {
        listeners.remove(listener);
    }

    protected void firePropertyChange(int propId)
    {
        for (IPropertyListener listener : listeners)
            listener.propertyChanged(this, propId);
    }

    public IWorkbenchPartSite getSite()
    {
        return site;
    }

    public Image getTitleImage()
    {
        return null;
    }

    public String getTitleToolTip()
    {
        return null;
    }

    public void setFocus()
    {}

    public <T> T getAdapter(Class<T> adapter)
    {
        return null;
    }

    public final void doSave(IProgressMonitor monitor)
    {}

    public final void doSaveAs()
    {}

    public final boolean isDirty()
    {
        return false;
    }

    public final boolean isSaveAsAllowed()
    {
        return false;
    }

    public final boolean isSaveOnCloseNeeded()
    {
        return false;
    }

    /**
     * @param manager
     */
    public void contributeToToolBar(IToolBarManager manager)
    {}

    public String getPaneId()
    {
        return configuration.getId();
    }

    public AbstractEditorPane getParentPane()
    {
        return parentPane;
    }

    public void dispose()
    {
        if (contextMenu != null && !contextMenu.isDisposed())
            contextMenu.dispose();

        Job.getJobManager().cancel(this);
    }

    protected void hookContextMenu(Control control)
    {
        if (contextMenu != null && !contextMenu.isDisposed())
            contextMenu.dispose();

        contextMenu = new Menu(control.getShell());
        contextMenu.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuShown(MenuEvent e)
            {
                MenuItem[] items = contextMenu.getItems();
                for (int ii = 0; ii < items.length; ii++)
                    items[ii].dispose();

                PopupMenu popup = new PopupMenu();
                editorContextMenuAboutToShow(popup);
                popup.addToMenu(getEditorSite().getActionBars().getStatusLineManager(), contextMenu);
            }

        });
        control.setMenu(contextMenu);

    }

    /**
     * @param menu
     */
    protected void editorContextMenuAboutToShow(PopupMenu menu)
    {}

    public PaneState getPaneState()
    {
        return paneState;
    }

    public void setPaneState(PaneState paneState)
    {
        this.paneState = paneState;
    }

    public MultiPaneEditor getEditor()
    {
        return ((MultiPaneEditorSite) this.getEditorSite()).getMultiPageEditor();
    }

    public IQueryContext getQueryContext()
    {
        return getEditor().getQueryContext();
    }
}
