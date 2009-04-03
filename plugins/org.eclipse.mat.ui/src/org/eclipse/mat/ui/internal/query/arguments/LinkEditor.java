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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TableItem;

public class LinkEditor extends ArgumentEditor
{
    public enum Mode
    {
        ADVANCED_MODE(Messages.LinkEditor_simpleMode), SIMPLE_MODE(Messages.LinkEditor_moreOptions);

        private String modeType;

        Mode(String modeType)
        {
            this.modeType = modeType;
        }

        public String getModeType()
        {
            return modeType;
        }
    }

    private Composite parent;
    private Mode mode;

    public LinkEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item, Mode mode)
    {
        super(parent, context, descriptor, item);
        this.parent = parent;
        this.mode = mode;
        createContents();
    }

    private void createContents()
    {
        this.setFont(parent.getFont());
        this.setBackground(parent.getBackground());
        GridLayout layout = new GridLayout();
        layout.marginLeft = 20;
        layout.marginRight = 0;
        layout.marginHeight = 0;
        this.setLayout(layout);
        Link link = new Link(this, SWT.WRAP);
        link.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
        link.setText("<a>" + mode.getModeType() + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$     
        link.setFont(parent.getFont());
        link.setBackground(parent.getBackground());
        link.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                changeMode();
                editingDone();
                fireModeChangeEvent(mode);
            }

            private void changeMode()
            {
                if (mode.equals(Mode.ADVANCED_MODE))
                    mode = Mode.SIMPLE_MODE;
                else
                    mode = Mode.ADVANCED_MODE;

            }
        });
    }

    @Override
    public Object getValue()
    {
        return null;
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {}

    private void editingDone()
    {
        fireValueChangedEvent(null, this);
    }
}
