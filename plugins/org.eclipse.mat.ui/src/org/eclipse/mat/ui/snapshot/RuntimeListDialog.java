/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.snapshot.MultipleSnapshotsException.Context;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ListDialog;

public class RuntimeListDialog extends ListDialog
{

    private Button detailsButton;
    private Text detailsText;
    private Composite parent;
    private static final String NEWLINE = "\n"; //$NON-NLS-1$

    public RuntimeListDialog(Shell parent, MultipleSnapshotsException mre)
    {
        super(parent);
        setBlockOnOpen(true);

        setLabelProvider(new LabelProvider()
        {

            @Override
            public String getText(Object element)
            {
                if (element instanceof MultipleSnapshotsException.Context)
                {
                    MultipleSnapshotsException.Context rt = (MultipleSnapshotsException.Context) element;
                    return rt.getRuntimeId();
                }
                return super.getText(element);
            }

        });

        setContentProvider(new IStructuredContentProvider()
        {

            public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
            {}

            public void dispose()
            {}

            public Object[] getElements(Object inputElement)
            {
                if (inputElement instanceof MultipleSnapshotsException) { return ((MultipleSnapshotsException) inputElement)
                                .getRuntimes().toArray(); }
                return null;
            }
        });

        setInput(mre);
        setTitle(Messages.RuntimeSelector_SelectSnapshotTitle);
        setMessage(Messages.RuntimeSelector_SelectSnapshot);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        detailsButton = createButton(parent, IDialogConstants.DETAILS_ID, IDialogConstants.SHOW_DETAILS_LABEL, true);
        super.createButtonsForButtonBar(parent);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.DETAILS_ID)
        {
            if (detailsButton.getText().equals(IDialogConstants.SHOW_DETAILS_LABEL))
            {
                detailsButton.setText(IDialogConstants.HIDE_DETAILS_LABEL);
                Object selection = ((IStructuredSelection) getTableViewer().getSelection()).getFirstElement();
                if (selection instanceof MultipleSnapshotsException.Context)
                {
                    updateDetails((MultipleSnapshotsException.Context) selection);
                }
            }
            else
            {
                detailsButton.setText(IDialogConstants.SHOW_DETAILS_LABEL);
                updateDetails(null);
            }

        }
        else
        {
            super.buttonPressed(buttonId);
        }
    }

    @Override
    protected Control createDialogArea(Composite container)
    {
        parent = (Composite) super.createDialogArea(container);
        getTableViewer().addSelectionChangedListener(new ISelectionChangedListener()
        {

            public void selectionChanged(SelectionChangedEvent event)
            {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                if (selection != null)
                {
                    Object selected = selection.getFirstElement();
                    if (selected instanceof MultipleSnapshotsException.Context)
                    {
                        updateDetails((MultipleSnapshotsException.Context) selected);
                    }
                }
            }
        });
        return parent;
    }

    private void updateDetails(MultipleSnapshotsException.Context runtime)
    {
        Point shellSize = getShell().getSize();
        Point originalContentsSize = getContents().computeSize(SWT.DEFAULT, SWT.DEFAULT);

        if (runtime != null && detailsButton.getText().equals(IDialogConstants.HIDE_DETAILS_LABEL))
        {
            if (detailsText == null)
            {
                detailsText = new Text(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
            }
            detailsText.setText(getDetails(runtime));
            GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL
                            | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL);
            data.heightHint = detailsText.getLineHeight() * 15;
            data.horizontalSpan = 2;
            detailsText.setLayoutData(data);
        }
        else
        {
            if (detailsText != null)
            {
                detailsText.dispose();
                detailsText = null;
            }
        }

        Point newContentsSize = getContents().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        getShell().setSize(new Point(shellSize.x, shellSize.y + (newContentsSize.y - originalContentsSize.y)));
    }

    private String getDetails(Context runtime)
    {
        StringBuffer details = new StringBuffer(200);
        details.append(MessageUtil.format(Messages.RuntimeSelector_Snapshot_Identifier, runtime.getRuntimeId()));
        details.append(NEWLINE + NEWLINE);
        details.append(Messages.RuntimeSelector_Runtime_Description + NEWLINE);
        details.append(runtime.getDescription());
        details.append(NEWLINE + NEWLINE);
        details.append(MessageUtil.format(Messages.RuntimeSelector_Java_Version, runtime.getVersion()));

        List<String> options = runtime.getOptions();
        if (options != null && options.size() > 0)
        {
            details.append(NEWLINE + NEWLINE);
            details.append(Messages.RuntimeSelector_Java_Options);
            Iterator<String> i = options.iterator();
            while (i.hasNext())
            {
                details.append(NEWLINE);
                details.append(i.next());
            }
        }
        return details.toString();
    }

}
