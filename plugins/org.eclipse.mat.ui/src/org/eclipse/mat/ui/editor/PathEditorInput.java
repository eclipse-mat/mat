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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.model.IWorkbenchAdapter;

public class PathEditorInput implements IPathEditorInput
{
    private class WorkbenchAdapter implements IWorkbenchAdapter
    {
        /*
         * @see
         * org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
         */
        public Object[] getChildren(Object o)
        {
            return null;
        }

        /*
         * @see
         * org.eclipse.ui.model.IWorkbenchAdapter#getImageDescriptor(java.lang
         * .Object)
         */
        public ImageDescriptor getImageDescriptor(Object object)
        {
            return null;
        }

        /*
         * @see
         * org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
         */
        public String getLabel(Object o)
        {
            return ((PathEditorInput) o).getName();
        }

        /*
         * @see
         * org.eclipse.ui.model.IWorkbenchAdapter#getParent(java.lang.Object)
         */
        public Object getParent(Object o)
        {
            return null;
        }
    }

    private WorkbenchAdapter fWorkbenchAdapter = new WorkbenchAdapter();

    private IPath path;

    public PathEditorInput(IPath path)
    {
        this.path = path;
    }

    public IPath getPath()
    {
        return this.path;
    }

    public boolean exists()
    {
        return false;
    }

    public ImageDescriptor getImageDescriptor()
    {
        return null;
    }

    public String getName()
    {
        return path.lastSegment();
    }

    public IPersistableElement getPersistable()
    {
        return null;
    }

    public String getToolTipText()
    {
        return path.toOSString();
    }

    @SuppressWarnings("unchecked")
    public Object getAdapter(Class adapter)
    {
        // if (ILocationProvider.class.equals(adapter))
        // return this;
        if (IWorkbenchAdapter.class.equals(adapter))
            return fWorkbenchAdapter;
        return Platform.getAdapterManager().getAdapter(this, adapter);
    }

    @Override
    public boolean equals(Object obj)
    {
        return ((obj instanceof IPathEditorInput) && ((IPathEditorInput) obj).getPath().toOSString().equals(
                        this.path.toOSString()));
    }

    @Override
    public int hashCode()
    {
        return this.path.hashCode();
    }

}
