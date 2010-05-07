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

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.ui.IEditorPart;

public class MultiPaneEditorSelectionProvider implements ISelectionProvider
{

    private ListenerList listeners = new ListenerList();

    private MultiPaneEditor heapEditor;

    public MultiPaneEditorSelectionProvider(MultiPaneEditor multiPageEditor)
    {
        this.heapEditor = multiPageEditor;
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
    }

    public void fireSelectionChanged(final SelectionChangedEvent event)
    {
        Object[] listeners = this.listeners.getListeners();
        for (int i = 0; i < listeners.length; ++i)
        {
            final ISelectionChangedListener l = (ISelectionChangedListener) listeners[i];
            SafeRunner.run(new SafeRunnable()
            {
                public void run()
                {
                    l.selectionChanged(event);
                }
            });
        }
    }

    public MultiPaneEditor getHeapEditor()
    {
        return heapEditor;
    }

    public ISelection getSelection()
    {
        IEditorPart activeEditor = heapEditor.getActiveEditor();
        if (activeEditor != null)
        {
            ISelectionProvider selectionProvider = activeEditor.getSite().getSelectionProvider();
            if (selectionProvider != null)
                return selectionProvider.getSelection();
        }
        return null;
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
    }

    public void setSelection(ISelection selection)
    {
        IEditorPart activeEditor = heapEditor.getActiveEditor();
        if (activeEditor != null)
        {
            ISelectionProvider selectionProvider = activeEditor.getSite().getSelectionProvider();
            if (selectionProvider != null)
                selectionProvider.setSelection(selection);
        }
    }

}
