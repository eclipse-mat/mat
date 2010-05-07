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
package org.eclipse.mat.ui.internal.query.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.internal.query.arguments.LinkEditor.Mode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;

public abstract class ArgumentEditor extends Composite
{
    protected IQueryContext context;
    protected ArgumentDescriptor descriptor;
    private List<IEditorListener> listeners = Collections.synchronizedList(new ArrayList<IEditorListener>());
    protected TableItem item;

    public interface IEditorListener
    {
        void onValueChanged(Object value, ArgumentDescriptor descriptor, TableItem item, ArgumentEditor editor);

        void onError(ArgumentEditor editor, String message);

        void onFocus(String message);

        void onModeChange(Mode mode, ArgumentDescriptor descriptor);

    }

    public ArgumentEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item)
    {
        super(parent, SWT.NONE);
        this.context = context;
        this.descriptor = descriptor;
        this.item = item;
    }

    public void addListener(IEditorListener listener)
    {
        this.listeners.add(listener);
    }

    public void removeListener(IEditorListener listener)
    {
        this.listeners.remove(listener);
    }

    void fireValueChangedEvent(Object value, ArgumentEditor editor)
    {
        synchronized (listeners)
        {
            for (IEditorListener listener : listeners)
                listener.onValueChanged(value, descriptor, item, editor);
        }
    }

    void fireErrorEvent(String message, ArgumentEditor editor)
    {
        synchronized (listeners)
        {
            for (IEditorListener listener : listeners)
                listener.onError(editor, message);
        }
    }

    void fireFocusEvent(String message)
    {
        synchronized (listeners)
        {
            for (IEditorListener listener : listeners)
                listener.onFocus(message);
        }
    }

    void fireModeChangeEvent(Mode mode)
    {
        synchronized (listeners)
        {
            for (IEditorListener listener : listeners)
                listener.onModeChange(mode, descriptor);
        }
    }

    public ArgumentDescriptor getDescriptor()
    {
        return descriptor;
    }

    public abstract void setValue(Object value) throws SnapshotException;

    public abstract Object getValue();

}
