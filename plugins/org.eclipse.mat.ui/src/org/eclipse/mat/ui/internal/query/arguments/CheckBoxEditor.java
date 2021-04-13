/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - general version for boolean fields
 *******************************************************************************/
package org.eclipse.mat.ui.internal.query.arguments;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;

public class CheckBoxEditor extends ArgumentEditor
{
    private Button checkBox;
    private Boolean value = false;
    private Type type;

    public enum Type
    {
        INCLUDE_CLASS_INSTANCE(Messages.CheckBoxEditor_includeClassInstance, null), //
        INCLUDE_SUBCLASSES(Messages.CheckBoxEditor_includeSubclasses, //
                        Messages.CheckBoxEditor_includeSubclassesAdditional), //
        INTEPRET_AS_CLASSLOADER(Messages.CheckBoxEditor_includeLoadedObjects,
                        Messages.CheckBoxEditor_includeLoadedObjectsAdditional), //
        RETAINED(Messages.CheckBoxEditor_asRetainedSet, Messages.CheckBoxEditor_asRetainedSetAdditional), //
        VERBOSE(Messages.CheckBoxEditor_verbose, Messages.CheckBoxEditor_verboseAdditional),
        GENERAL("", null); //$NON-NLS-1$

        private String label;
        private String helpText;

        private Type(String label, String helpText)
        {
            this.label = label;
            this.helpText = helpText;
        }

        public String getLabel()
        {
            return label;
        }

        public String getHelpText()
        {
            return helpText;
        }
    }

    public CheckBoxEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item,
                    Type type)
    {
        super(parent, context, descriptor, item);
        this.type = type;
        setFont(item.getFont());
        setBackground(item.getBackground());
        setLayout(new FillLayout());
        createContents(parent);
    }

    private void createContents(Composite parent)
    {
        checkBox = new Button(this, SWT.CHECK);
        checkBox.setFont(item.getFont());
        checkBox.setBackground(item.getBackground());
        checkBox.setText(type.getLabel());
        checkBox.addFocusListener(new FocusListener()
        {

            public void focusGained(FocusEvent e)
            {
                fireFocusEvent(type == Type.GENERAL ? descriptor.getHelp() : type.getHelpText());
            }

            public void focusLost(FocusEvent e)
            {
                fireFocusEvent(null);
            }
        });

        checkBox.addSelectionListener(new SelectionListener()
        {
            public void widgetDefaultSelected(SelectionEvent e)
            {}

            public void widgetSelected(SelectionEvent e)
            {
                editingDone();
            }
        });
        checkBox.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                if (e.character == '\r')
                { // Return key
                    editingDone();
                }
            }
        });

    }

    protected void editingDone()
    {
        this.value = checkBox.getSelection();
        fireValueChangedEvent(this.value, this);
    }

    @Override
    public Object getValue()
    {
        return value;
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {
        this.value = (Boolean) value;
        checkBox.setSelection(this.value.booleanValue());

    }

    @Override
    public boolean setFocus()
    {
        return checkBox.setFocus();
    }

    public Type getType()
    {
        return type;
    }
}
