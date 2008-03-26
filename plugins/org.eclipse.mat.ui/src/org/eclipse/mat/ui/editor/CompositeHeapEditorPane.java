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
package org.eclipse.mat.ui.editor;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.PageBook;


public abstract class CompositeHeapEditorPane extends AbstractEditorPane implements ISelectionProvider,
                ISelectionChangedListener
{
    private PageBook container;
    private AbstractEditorPane embeddedPane;

    List<ISelectionChangedListener> listeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    protected void createContainer(Composite parent)
    {
        container = new PageBook(parent, SWT.NONE);
    }

    protected void createResultPane(String id, Object arguments)
    {
        try
        {
            AbstractEditorPane pane = PaneConfiguration.createNewPane(id);
            if (pane == null)
                throw new PartInitException(MessageFormat.format("Editor pane {0} not found.", new Object[] { id }));

            createResultPane(pane, arguments);
        }
        catch (CoreException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

    protected void createResultPane(AbstractEditorPane pane, Object arguments)
    {
        if (embeddedPane != null)
        {
            if (embeddedPane instanceof ISelectionProvider)
            {
                ((ISelectionProvider) embeddedPane).removeSelectionChangedListener(this);
            }

            for (Control control : container.getChildren())
            {
                control.setVisible(false);
                control.dispose();
            }
            embeddedPane.dispose();
            embeddedPane = null;
        }

        try
        {
            MultiPaneEditor multiPaneEditor = ((MultiPaneEditorSite) this.getEditorSite()).getMultiPageEditor();

            embeddedPane = pane;

            embeddedPane.parentPane = this;
            setPane(embeddedPane, getEditorInput());
            embeddedPane.initWithArgument(arguments);

            multiPaneEditor.updateToolbar();

            if (embeddedPane instanceof ISelectionProvider)
            {
                ISelectionProvider embeddedProvider = ((ISelectionProvider) embeddedPane);
                embeddedProvider.addSelectionChangedListener(this);
                selectionChanged(new SelectionChangedEvent(embeddedProvider, embeddedProvider.getSelection()));
            }
        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

    protected AbstractEditorPane getEmbeddedPane()
    {
        return embeddedPane;
    }

    private void setPane(AbstractEditorPane editor, IEditorInput input) throws PartInitException
    {
        editor.init(site, input);
        Composite parent2 = new Composite(container, Window.getDefaultOrientation());
        parent2.setLayout(new FillLayout());
        editor.createPartControl(parent2);

        container.showPage(parent2);
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
    }

    public ISelection getSelection()
    {
        if (embeddedPane != null && embeddedPane instanceof ISelectionProvider)
        {
            return ((ISelectionProvider) embeddedPane).getSelection();
        }
        else
        {
            return StructuredSelection.EMPTY;
        }
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
    }

    public void setSelection(ISelection selection)
    {
        if (embeddedPane != null && embeddedPane instanceof ISelectionProvider)
        {
            ((ISelectionProvider) embeddedPane).setSelection(selection);
        }
    }

    public void selectionChanged(SelectionChangedEvent event)
    {
        List<ISelectionChangedListener> l = new ArrayList<ISelectionChangedListener>(listeners);
        for (ISelectionChangedListener listener : l)
        {
            listener.selectionChanged(event);
        }
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        if (embeddedPane != null)
        {
            manager.add(new Separator());
            embeddedPane.contributeToToolBar(manager);
        }
    }

}
