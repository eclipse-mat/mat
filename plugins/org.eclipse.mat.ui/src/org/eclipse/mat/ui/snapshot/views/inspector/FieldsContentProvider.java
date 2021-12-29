/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
package org.eclipse.mat.ui.snapshot.views.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.MessageBox;

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
            result[result.length - 1] = new MoreNode(limit, fields.getSize(), this::setLimit, viewer.getControl());

        return result;
    }

    private void setLimit(int newLimit)
    {
        limit = newLimit;
        viewer.refresh();
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
        Control control;
        IntConsumer limitUpdater;

        /* package */ MoreNode(int limit, int size, IntConsumer limitUpdater, Control control)
        {
            this.limit = limit;
            this.size = size;
            this.limitUpdater = limitUpdater;
            this.control = control;
        }

        public String toString()
        {
            return MessageUtil.format(Messages.FieldsContentProvider_Displayed, limit, size);
        }

        public Iterable<Action> getActions()
        {
            List<Action> actions = new ArrayList<>();
            if (size - limit >= 25)
            {
                actions.add(new Next25ExpandAction(limit, size, limitUpdater, control));
            }
            actions.add(new CustomExpandAction(limit, size, limitUpdater, control));
            actions.add(new ExpandAllAction(limit, size, limitUpdater, control));

            return actions;
        }
    }

    static abstract class ExpandAction extends Action
    {
        private long limit;
        private long size;
        private IntConsumer limitUpdater;
        private Control control;

        ExpandAction(String text, int limit, int size, IntConsumer limitUpdater, Control control)
        {
            super(text);
            this.limit = limit;
            this.size = size;
            this.limitUpdater = limitUpdater;
            this.control = control;
            
            setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
        }

        @Override
        public final void run()
        {
            long nextLimit = nextLimit();
            if (nextLimit < limit)
            { return; }
            
            if (nextLimit > Integer.MAX_VALUE)
            {
                nextLimit = Integer.MAX_VALUE;
            }

            long delta = nextLimit - limit;
            long toBeExpanded = Math.min(size - limit, delta);
            if (toBeExpanded > 5000)
            {
                MessageBox box = new MessageBox(control.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
                box.setMessage(MessageUtil.format(Messages.FieldsContentProvider_BlockingWarning, toBeExpanded));
                if (box.open() != SWT.OK)
                { return; }
            }

            limitUpdater.accept((int) nextLimit);
        }

        protected final long getCurrentLimit()
        {
            return limit;
        }

        protected final Control getControl()
        {
            return control;
        }

        /**
         * Provides next limit value (how much elements should be shown). If
         * returned value exceeds {@link Integer#MAX_VALUE}, then
         * {@link Integer#MAX_VALUE} will be used. Returning value which is less
         * than actual limit means that expanding action should be stopped.
         * 
         * @return next limit value
         */
        protected abstract long nextLimit();
    }

    static class Next25ExpandAction extends ExpandAction
    {
        Next25ExpandAction(int limit, int size, IntConsumer limitUpdater, Control control)
        {
            super(Messages.FieldsContentProvider_Next25, limit, size, limitUpdater, control);
        }

        @Override
        protected long nextLimit()
        {
            return getCurrentLimit() + 25;
        }
    }

    static class IntegerInputValidator implements IInputValidator
    {
        public String isValid(String newText)
        {
            if (newText == null || newText.length() == 0)
            {
                return " "; //$NON-NLS-1$
            }
            try
            {
                if (Integer.parseInt(newText) > 0)
                { return null; }
            }
            catch (NumberFormatException e)
            {}
            return Messages.FieldsContentProvider_notValidNumber;
        }
    };

    static class CustomExpandAction extends ExpandAction
    {
        CustomExpandAction(int limit, int size, IntConsumer limitUpdater, Control control)
        {
            super(Messages.FieldsContentProvider_CustomExpand, limit, size, limitUpdater, control);
        }

        @Override
        protected long nextLimit()
        {
            InputDialog inputDialog = new InputDialog(getControl().getShell(),
                            Messages.FieldsContentProvider_ExpandToLimit, Messages.FieldsContentProvider_EnterNumber,
                            null, new IntegerInputValidator());

            if (inputDialog.open() == 1)
            { return -1; }
            return getCurrentLimit() + Integer.parseInt(inputDialog.getValue());
        }
    }

    static class ExpandAllAction extends ExpandAction
    {
        ExpandAllAction(int limit, int size, IntConsumer limitUpdater, Control control)
        {
            super(Messages.FieldsContentProvider_ExpandAll, limit, size, limitUpdater, control);
        }

        @Override
        protected long nextLimit()
        {
            return Integer.MAX_VALUE;
        }
    }

}
