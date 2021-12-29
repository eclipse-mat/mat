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
import org.eclipse.swt.widgets.Control;

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
            this.size = size;
            this.limit = limit;
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
                Action next25 = new Action(Messages.FieldsContentProvider_Next25)
                {
                    @Override
                    public void run()
                    {
                        limitUpdater.accept(limit + 25);
                    }
                };
                next25.setImageDescriptor(
                                MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
                actions.add(next25);
            }
            Action customExpand = new Action(Messages.FieldsContentProvider_CustomExpand)
            {
                @Override
                public void run()
                {
                    IInputValidator inputValidator = new IInputValidator()
                    {

                        public String isValid(String newText)
                        {
                            if (newText == null || newText.length() == 0)
                                return " "; //$NON-NLS-1$
                            try
                            {
                                if (Integer.parseInt(newText) > 0)
                                    return null;
                            }
                            catch (NumberFormatException e)
                            {}
                            return Messages.FieldsContentProvider_notValidNumber;

                        }

                    };

                    InputDialog inputDialog = new InputDialog(control.getDisplay().getActiveShell(),
                                    Messages.FieldsContentProvider_ExpandToLimit,
                                    Messages.FieldsContentProvider_EnterNumber, null, inputValidator);

                    if (inputDialog.open() == 1) // if canceled
                        return;
                    int number = Integer.parseInt(inputDialog.getValue());
                    limitUpdater.accept(limit + number);
                }

            };
            customExpand.setImageDescriptor(
                            MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
            actions.add(customExpand);

            Action expandAll = new Action(Messages.FieldsContentProvider_ExpandAll)
            {
                @Override
                public void run()
                {
                    limitUpdater.accept(Integer.MAX_VALUE);
                }
            };
            expandAll.setImageDescriptor(
                            MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
            actions.add(expandAll);

            return actions;
        }
    }

}
