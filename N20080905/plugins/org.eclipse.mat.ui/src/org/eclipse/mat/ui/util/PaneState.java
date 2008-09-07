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

import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.graphics.Image;

public class PaneState
{
    public enum PaneType
    {
        QUERY, EDITOR, COMPOSITE_PARENT, COMPOSITE_CHILD
    }

    private List<PaneState> children = new ArrayList<PaneState>(2);
    private String identifier;
    private boolean reproducable;
    private PaneState originator;
    private Image image;
    private boolean active;
    private PaneType type;

    public PaneState(PaneType type, PaneState originator, String identifier, boolean reproducable)
    {
        this.identifier = identifier;
        this.reproducable = reproducable;
        this.originator = originator;
        this.type = type;
        setActive(true);
    }

    public PaneState getParentPaneState()
    {
        return originator;
    }

    public String getIdentifier()
    {
        return identifier;
    }

    public boolean isReproducable()
    {
        return reproducable;
    }

    public List<PaneState> getChildren()
    {
        return children;
    }

    protected void addChild(PaneState child)
    {
        children.add(child);
    }

    protected void removeChild(PaneState paneState)
    {
        children.remove(paneState);
    }

    public Image getImage()
    {
        return image != null ? image : MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.QUERY);
    }

    public void setImage(Image image)
    {
        this.image = image;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    public PaneType getType()
    {
        return type;
    }

    public boolean hasChildren()
    {
        return !children.isEmpty();
    }

    public boolean hasActiveChildren()
    {
        if (children.isEmpty())
            return false;

        for (PaneState child : children)
        {
            if (child.active)
                return true;
        }

        return false;
    }
}
