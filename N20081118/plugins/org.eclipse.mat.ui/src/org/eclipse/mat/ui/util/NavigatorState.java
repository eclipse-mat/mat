/*******************************************************************************
 * Copyright (c) 2008 SAP AG. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.mat.ui.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.ui.util.PaneState.PaneType;

public class NavigatorState
{
    public interface IStateChangeListener
    {
        void onStateChanged(PaneState state);
    }

    private List<PaneState> rootEntries = new ArrayList<PaneState>(1);
    private List<IStateChangeListener> listeners = new ArrayList<IStateChangeListener>();

    public NavigatorState()
    {
        this.listeners = new ArrayList<IStateChangeListener>();
    }

    public void addChangeStateListener(IStateChangeListener listener)
    {
        this.listeners.add(listener);
    }

    public void removeChangeStateListener(IStateChangeListener listener)
    {
        this.listeners.remove(listener);
    }

    private void notifyListeners(PaneState state)
    {
        for (IStateChangeListener listener : this.listeners)
            listener.onStateChanged(state);
    }

    public List<PaneState> getElements()
    {
        return rootEntries;
    }

    public void removeEntry(PaneState paneState)
    {
        PaneState parent = paneState.getParentPaneState();
        if (parent == null)
            rootEntries.remove(paneState);
        else
            parent.removeChild(paneState);
        notifyListeners(parent);
    }

    public void paneAdded(PaneState state)
    {
        if (state == null)
            return;
        
        state.setActive(true);
        
        if (state.getParentPaneState() == null && !rootEntries.contains(state))
        {
            rootEntries.add(state);
            notifyListeners(null);
        }
        else if (state.getParentPaneState() != null && !state.getParentPaneState().getChildren().contains(state))
        {
            state.getParentPaneState().addChild(state);
            notifyListeners(state.getParentPaneState());
        }
        else
        {
            notifyListeners(state);
        }
    }

    public void paneRemoved(PaneState state)
    {
        if (state == null)
            return;

        state.setActive(false);
        notifyListeners(state);
        
        if (state.getType() == PaneType.COMPOSITE_PARENT)
        {
            for (PaneState child : state.getChildren())
            {
                if (child.isActive())
                    paneRemoved(child);
            }
        }
    }

}
