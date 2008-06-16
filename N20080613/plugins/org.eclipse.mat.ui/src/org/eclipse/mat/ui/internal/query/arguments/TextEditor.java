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

import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
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
        PATTERN(MemoryAnalyserPlugin.ISharedImages.CLASS, "Enter a class name pattern (java.util.*)", "#pattern"), OBJECT_ADDRESS(
                        MemoryAnalyserPlugin.ISharedImages.ID, "Enter an address (0x4711)", "#address"), QUERY(
                        MemoryAnalyserPlugin.ISharedImages.OQL, "Enter an OQL query (select * from ...)", "#oql_query");

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

    public TextEditor(Composite parent, ArgumentDescriptor descriptor, TableItem item, DecoratorType decorator)
    {
        super(parent, descriptor, item);
        this.setBackground(parent.getBackground());
        this.parent = parent;
        this.decorator = decorator;
        createContents();
    }

    public TextEditor(Composite parent, ArgumentDescriptor descriptor, TableItem item)
    {
        this(parent, descriptor, item, null);
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
                value = descriptor.stringToValue(t);
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
        text.setText(descriptor.valueToString(value));
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
