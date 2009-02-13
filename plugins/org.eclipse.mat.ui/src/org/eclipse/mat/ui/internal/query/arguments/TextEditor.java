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
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.PatternUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class TextEditor extends ArgumentEditor
{
    protected Object value;
    protected Text text;
    protected Composite parent;
    protected DecoratorType decorator;

    public enum DecoratorType
    {
        PATTERN(MemoryAnalyserPlugin.ISharedImages.CLASS, Messages.TextEditor_EnterPattern, "pattern"), OBJECT_ADDRESS( //$NON-NLS-1$
                        MemoryAnalyserPlugin.ISharedImages.ID, Messages.TextEditor_EnterAddress, "address"), QUERY( //$NON-NLS-1$
                        MemoryAnalyserPlugin.ISharedImages.OQL, Messages.TextEditor_EnterOQLQuery, "oql_query"); //$NON-NLS-1$

        private String imageType;
        private String helpText;
        private String helpLink;

        private DecoratorType(String imageType, String helpText, String helpLink)
        {
            this.imageType = imageType;
            this.helpText = helpText;
            this.helpLink = helpLink;
        }

        public Image getImage()
        {
            return MemoryAnalyserPlugin.getImage(imageType);
        }

        public String getImageType()
        {
            return imageType;
        }

        public String getHelpText()
        {
            return helpText;
        }

        public String getHelpLink()
        {
            return helpLink;
        }
    }

    public TextEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item,
                    DecoratorType decorator)
    {
        super(parent, context, descriptor, item);
        this.setBackground(parent.getBackground());
        this.parent = parent;
        this.decorator = decorator;
        createContents();
    }

    public TextEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item)
    {
        this(parent, context, descriptor, item, null);
    }

    protected void createContents()
    {
        this.setLayout(new FillLayout());
        text = new Text(this, SWT.LEFT);

        text.addKeyListener(new KeyAdapter()
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

        text.addModifyListener(new ModifyListener()
        {
            // here we verify whether the
            // "Finish" button should be enabled
            public void modifyText(ModifyEvent e)
            {
                editingDone();
            }
        });

        text.addFocusListener(new FocusListener()
        {

            public void focusGained(FocusEvent e)
            {}

            public void focusLost(FocusEvent e)
            {
                if (descriptor.getAdvice() == Argument.Advice.CLASS_NAME_PATTERN)
                {
                    text.setText(PatternUtil.smartFix(text.getText(), false));
                }
            }

        });
    }

    protected void editingDone()
    {
        try
        {
            fireErrorEvent(null, this);
            String t = text.getText().trim();

            if (t.length() == 0)
            {
                value = null;
            }
            else
            {
                value = context.convertToValue(descriptor.getType(), descriptor.getAdvice(), t);
            }

            fireValueChangedEvent(value, this);
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            fireErrorEvent(e.getMessage(), this);
        }
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {
        this.value = value;
        text.setText(context.convertToString(descriptor.getType(), descriptor.getAdvice(), value));
    }

    @Override
    public Object getValue()
    {
        return this.value;
    }

    @Override
    public boolean setFocus()
    {
        return text.setFocus();
    }

    public DecoratorType getDecorator()
    {
        return decorator;
    }

}
