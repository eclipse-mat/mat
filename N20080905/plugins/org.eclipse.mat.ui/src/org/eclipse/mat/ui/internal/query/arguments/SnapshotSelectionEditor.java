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
package org.eclipse.mat.ui.internal.query.arguments;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.SnapshotArgument;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.snapshot.OpenSnapshot;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TableItem;

public class SnapshotSelectionEditor extends ArgumentEditor
{
    private CCombo combo;
    private Button button;
    private SnapshotArgument snapshot;

    private class XLayout extends Layout
    {
        public void layout(Composite editor, boolean force)
        {
            Rectangle bounds = editor.getClientArea();
            Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            if (combo != null)
                combo.setBounds(0, 0, bounds.width - size.x, bounds.height);
            button.setBounds(bounds.width - size.x, 0, size.x, bounds.height);
        }

        public Point computeSize(Composite editor, int wHint, int hHint, boolean force)
        {
            if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) { return new Point(wHint, hHint); }
            Point contentsSize = combo.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            Point buttonSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);

            // Just return the button width to ensure the button is not clipped
            // if the label is long.
            // The label will just use whatever extra width there is
            Point result = new Point(buttonSize.x, Math.max(contentsSize.y, buttonSize.y));
            return result;
        }
    }

    public SnapshotSelectionEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor,
                    TableItem item)
    {
        super(parent, context, descriptor, item);
        createContents();
    }

    private void createContents()
    {
        this.setFont(this.getParent().getFont());
        this.setBackground(this.getParent().getBackground());

        createComboBox();

        button = new Button(this, SWT.NONE);
        button.setText("...");
        button.setFont(this.getParent().getFont());

        this.setLayout(new XLayout());

        button.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent event)
            {
                openDialogBox();
            }
        });
    }

    private void createComboBox()
    {
        combo = new CCombo(this, SWT.SIMPLE);
        combo.setBackground(this.getParent().getBackground());

        for (SnapshotHistoryService.Entry entry : SnapshotHistoryService.getInstance().getVisitedEntries())
        {
            if (MemoryAnalyserPlugin.EDITOR_ID.equals(entry.getEditorId()))
                combo.add(entry.getFilePath());
        }

        combo.addSelectionListener(new SelectionListener()
        {
            public void widgetDefaultSelected(SelectionEvent e)
            {}

            public void widgetSelected(SelectionEvent e)
            {
                editingDone();
            }
        });

        combo.addModifyListener(new ModifyListener()
        {

            public void modifyText(ModifyEvent e)
            {
                editingDone();
            }
        });
    }

    protected void editingDone()
    {
        String path = combo.getText().trim();

        if (path.length() > 0 && !new File(path).exists())
        {
            fireErrorEvent(MessageFormat.format("File does not exist.", path), this);
        }
        else
        {
            snapshot = path.length() > 0 ? new SnapshotArgument(path) : null;
            fireValueChangedEvent(snapshot, this);
        }
    }

    private void openDialogBox()
    {
        boolean successful = new OpenSnapshot.Visitor()
        {

            @Override
            public void visit(IFileStore fileStore)
            {
                String path = fileStore.toString();
                combo.setText(path);
                combo.add(path, 0);
            }

        }.go(this.getShell());

        if (successful)
            editingDone();
    }

    @Override
    public Object getValue()
    {
        return snapshot;
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {
        snapshot = (SnapshotArgument) value;
        combo.setText(snapshot.getFilename());
    }

    @Override
    public boolean setFocus()
    {
        return combo.setFocus();
    }

}
