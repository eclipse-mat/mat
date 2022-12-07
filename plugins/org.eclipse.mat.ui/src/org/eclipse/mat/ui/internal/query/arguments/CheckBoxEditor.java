/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
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

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.HeapObjectParamArgument;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ImageHyperlink;

public class CheckBoxEditor extends ArgumentEditor
{
    private Button checkBox;
    private Boolean value = false;
    private Type type;
    private ImageHyperlink button;

    private class ButtonEditorLayout extends Layout
    {
        private final static int IMAGE_MARGIN = 0;

        public void layout(Composite editor, boolean force)
        {
            Rectangle bounds = editor.getClientArea();
            Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            checkBox.setBounds(IMAGE_MARGIN, 0, bounds.width - size.x - IMAGE_MARGIN, bounds.height);
            button.setBounds(bounds.width - size.x, 0, size.x, bounds.height);
        }

        public Point computeSize(Composite editor, int wHint, int hHint, boolean force)
        {
            if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) { return new Point(wHint, hHint); }
            Point contentsSize = checkBox.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            Point buttonSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            // Just return the button width to ensure the button is not clipped
            // if the label is long.
            // The label will just use whatever extra width there is
            Point result = new Point(buttonSize.x, Math.max(contentsSize.y, buttonSize.y));
            return result;
        }
    }

    public enum Type
    {
        INCLUDE_CLASS_INSTANCE(Messages.CheckBoxEditor_includeClassInstance, //
                        Messages.CheckBoxEditor_includeClassInstanceAdditional, //
                        HeapObjectParamArgument.Flags.INCLUDE_CLASS_INSTANCE), //
        INCLUDE_SUBCLASSES(Messages.CheckBoxEditor_includeSubclasses, //
                        Messages.CheckBoxEditor_includeSubclassesAdditional, //
                        HeapObjectParamArgument.Flags.INCLUDE_SUBCLASSES), //
        INTEPRET_AS_CLASSLOADER(Messages.CheckBoxEditor_includeLoadedObjects,
                        Messages.CheckBoxEditor_includeLoadedObjectsAdditional, //
                        HeapObjectParamArgument.Flags.INCLUDE_LOADED_INSTANCES), //
        RETAINED(Messages.CheckBoxEditor_asRetainedSet, Messages.CheckBoxEditor_asRetainedSetAdditional, //
                        HeapObjectParamArgument.Flags.RETAINED), //
        VERBOSE(Messages.CheckBoxEditor_verbose, Messages.CheckBoxEditor_verboseAdditional, //
                        HeapObjectParamArgument.Flags.VERBOSE),
        GENERAL("", null, null); //$NON-NLS-1$

        private String label;
        private String helpText;
        private String flag;

        private Type(String label, String helpText, String flag)
        {
            this.label = label;
            this.helpText = helpText;
            this.flag = flag;
        }

        public String getLabel()
        {
            return label;
        }

        public String getHelpText()
        {
            return helpText;
        }

        public String getFlag()
        {
            return flag;
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
        if (type.getHelpText() != null)
            setToolTipText(type.getHelpText());
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
        if (getType() != null && getType().getFlag() != null)
        {
            createHelpControl(this);
            setLayout(new ButtonEditorLayout());
        }
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

    private void createHelpControl(Composite parent)
    {
        button = new ImageHyperlink(parent, SWT.CENTER)
        {
            {
                marginHeight = 0;
            }
        };
        button.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HELP));
        button.setFont(item.getFont());
        button.setBackground(item.getBackground());
        button.setToolTipText(JFaceResources.getString("helpToolTip"));//$NON-NLS-1$
        button.addHyperlinkListener(new HyperlinkAdapter()
        {
            public void linkActivated(HyperlinkEvent e)
            {
                PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(
                                "/org.eclipse.mat.ui.help/reference/selectingqueries.html#ref_queryarguments__"//$NON-NLS-1$
                                                + getType().getFlag().substring(1));
            }
        });

    }
}
