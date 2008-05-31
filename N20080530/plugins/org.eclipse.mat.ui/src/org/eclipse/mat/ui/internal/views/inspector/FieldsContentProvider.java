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
package org.eclipse.mat.ui.internal.views.inspector;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;

public class FieldsContentProvider implements IStructuredContentProvider, IDoubleClickListener
{
    private Viewer viewer;
    private int limit;
    private LazyFields<?> fields;

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
    {
        this.fields = (LazyFields<?>) newInput;
        this.limit = 25;
        this.viewer = viewer;

        if (this.viewer instanceof StructuredViewer)
            ((StructuredViewer) this.viewer).addDoubleClickListener(this);
    }

    public Object[] getElements(Object inputElement)
    {
        boolean isAll = fields.getSize() <= limit;

        Object[] result = new Object[(isAll ? 0 : 1) + Math.min(fields.getSize(), limit)];

        List<?> elements = fields.getElements(limit);
        for (int index = 0; index < result.length && index < elements.size(); index++)
            result[index] = elements.get(index);

        if (!isAll)
            result[result.length - 1] = new MoreNode(limit, fields.getSize());

        return result;
    }

    public void dispose()
    {}

    public void doubleClick(DoubleClickEvent event)
    {
        IStructuredSelection selection = (IStructuredSelection) event.getSelection();
        if (selection.isEmpty())
            return;

        if (!(selection.getFirstElement() instanceof MoreNode))
            return;

        limit += 25;
        viewer.refresh();
    }

    /* package */static class MoreNode
    {
        int size;
        int limit;

        /* package */MoreNode(int limit, int size)
        {
            this.size = size;
            this.limit = limit;
        }

        public String toString()
        {
            return MessageFormat.format("{0} out of {1} displayed", limit, size);
        }
    }

}
