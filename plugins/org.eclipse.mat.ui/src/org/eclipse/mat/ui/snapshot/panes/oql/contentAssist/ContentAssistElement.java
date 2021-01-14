/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - add images and description
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import org.eclipse.swt.graphics.Image;

/**
 * Javabean to temporarily store classnames for the content assistant in an
 * ordered structure.
 * 
 * @author pyppo
 */
public class ContentAssistElement implements Comparable<ContentAssistElement>
{

    private String className;

    private Image image;
    
    private String displayName;

    /**
     * Uses String compare method.
     */
    public int compareTo(ContentAssistElement o)
    {
        int ret = className.compareTo(o.className);
        if (ret == 0) {
            if (displayName == null)
            {
                if (o.displayName != null)
                    ret = -1;
            }
            else
            {
                if (o.displayName == null)
                    ret = 1;
                else
                    ret = displayName.compareTo(o.displayName);
            }
        }
        return ret;
    }

    @Override
    public int hashCode()
    {
        return className.hashCode() + (displayName != null ? displayName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContentAssistElement other = (ContentAssistElement) obj;
        if (className == null)
        {
            if (other.className != null)
                return false;
        }
        else if (!className.equals(other.className))
            return false;
        if (displayName == null)
        {
            if (other.displayName != null)
                return false;
        }
        else if (!displayName.equals(other.displayName))
            return false;
        return true;
    }

    public String getClassName()
    {
        return className;
    }

    public String getDisplayString()
    {
        return displayName;
    }

    public Image getImage()
    {
        return image;
    }

    public ContentAssistElement(String className, Image image)
    {
        this(className, image, null);
    }
    
    public ContentAssistElement(String className, Image image, String display)
    {
        super();
        if (className == null)
            throw new IllegalArgumentException("Cannot be initialized without a class name");
        this.className = className;
        this.image = image;
        this.displayName = display;
    }

    public String toString()
    {
        return displayName != null ? className + " : " + displayName : className; //$NON-NLS-1$
    }

}
