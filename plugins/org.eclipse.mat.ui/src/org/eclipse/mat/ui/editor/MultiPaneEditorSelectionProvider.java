/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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

    private ListenerList<ISelectionChangedListener> listeners = new ListenerList<ISelectionChangedListener>();

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
        for (ISelectionChangedListener listener : this.listeners)
        {
            final ISelectionChangedListener l = listener;
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
