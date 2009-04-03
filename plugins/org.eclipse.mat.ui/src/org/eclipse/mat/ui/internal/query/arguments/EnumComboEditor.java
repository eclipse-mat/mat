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
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;

public class EnumComboEditor extends ArgumentEditor
{
    private CCombo combo;
    Object[] enumConstants;
    Object value;

    public EnumComboEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item)
    {
        super(parent, context, descriptor, item);
        enumConstants = descriptor.getType().getEnumConstants();
        setFont(parent.getFont());
        setBackground(parent.getBackground());
        setLayout(new FillLayout());
        createContents(parent);
    }

    private void createContents(Composite parent)
    {
        combo = new CCombo(this, SWT.READ_ONLY | SWT.SIMPLE);
        combo.setFont(parent.getFont());
        combo.setBackground(parent.getBackground());
        for (Object obj : enumConstants)
        {
            combo.add(obj.toString());
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

    }

    @Override
    public Object getValue()
    {
        return value;
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {
        this.value = value;
        for (int i = 0; i < enumConstants.length; i++)
        {
            if (value.equals(enumConstants[i]))
            {
                combo.select(i);
                combo.setSelection(new Point(0, 0));
                break;
            }
        }
    }

    private void editingDone()
    {
        this.value = enumConstants[combo.getSelectionIndex()];
        fireValueChangedEvent(this.value, this);
    }

    @Override
    public boolean setFocus()
    {
        return combo.setFocus();
    }

}
