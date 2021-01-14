/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - MAT Calcite
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
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

    List<ISelectionChangedListener> selectionListeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    protected void createContainer(Composite parent)
    {
        container = new PageBook(parent, SWT.NONE);
    }

    protected void createResultPane(AbstractEditorPane pane, Object arguments)
    {
        disposeEmbeddedPane();

        try
        {
            embeddedPane = pane;

            embeddedPane.parentPane = this;
            setPane(embeddedPane, getEditorInput());
            embeddedPane.initWithArgument(arguments);

            MultiPaneEditor multiPaneEditor = ((MultiPaneEditorSite) this.getEditorSite()).getMultiPageEditor();
            multiPaneEditor.updateToolbar();

            if (embeddedPane instanceof ISelectionProvider)
            {
                ISelectionProvider embeddedProvider = ((ISelectionProvider) embeddedPane);
                embeddedProvider.addSelectionChangedListener(this);
                selectionChanged(new SelectionChangedEvent(embeddedProvider, embeddedProvider.getSelection()));
            }

            multiPaneEditor.getNavigatorState().paneAdded(embeddedPane.getPaneState());
        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

    private void disposeEmbeddedPane()
    {
        if (embeddedPane != null)
        {
            MultiPaneEditor multiPaneEditor = ((MultiPaneEditorSite) this.getEditorSite()).getMultiPageEditor();
            multiPaneEditor.getNavigatorState().paneRemoved(embeddedPane.getPaneState());

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
    }

    public void closePage(PaneState state)
    {
        if (embeddedPane != null && embeddedPane.getPaneState() == state)
            disposeEmbeddedPane();
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
        selectionListeners.add(listener);
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
        selectionListeners.remove(listener);
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
        List<ISelectionChangedListener> l = new ArrayList<ISelectionChangedListener>(selectionListeners);
        for (ISelectionChangedListener listener : l)
        {
            listener.selectionChanged(event);
        }
    }

    public <T> T getAdapter(Class<T> adapter)
    {
        if (embeddedPane != null)
            return embeddedPane.getAdapter(adapter);
        return super.getAdapter(adapter);
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
