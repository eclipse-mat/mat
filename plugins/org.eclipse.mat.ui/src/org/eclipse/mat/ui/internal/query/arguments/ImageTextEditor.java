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

import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.util.PatternUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ImageHyperlink;

public class ImageTextEditor extends TextEditor
{
    private ControlDecoration field;
    private ImageHyperlink button;

    private class ButtonEditorLayout extends Layout
    {
        private final static int IMAGE_MARGIN = 17;

        public void layout(Composite editor, boolean force)
        {
            Rectangle bounds = editor.getClientArea();
            Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            if (text != null)
            {
                text.setBounds(IMAGE_MARGIN, 0, bounds.width - size.x - IMAGE_MARGIN, bounds.height);
            }
            button.setBounds(bounds.width - size.x, 0, size.x, bounds.height);
        }

        public Point computeSize(Composite editor, int wHint, int hHint, boolean force)
        {
            if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) { return new Point(wHint, hHint); }
            Point contentsSize = text.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            Point buttonSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            // Just return the button width to ensure the button is not clipped
            // if the label is long.
            // The label will just use whatever extra width there is
            Point result = new Point(buttonSize.x, Math.max(contentsSize.y, buttonSize.y));
            return result;
        }
    }

    public ImageTextEditor(Composite parent, IQueryContext context, ArgumentDescriptor descriptor, TableItem item,
                    final DecoratorType decorator)
    {
        super(parent, context, descriptor, item, decorator);

        text.addFocusListener(new FocusListener()
        {

            public void focusGained(FocusEvent e)
            {
                fireFocusEvent(decorator.getHelpText());
            }

            public void focusLost(FocusEvent e)
            {
                // do the smart fix of the pattern on focus lost
                if (decorator.equals(DecoratorType.PATTERN))
                {
                    text.setText(PatternUtil.smartFix(text.getText(), false));
                }
            }
        });
    }

    protected void createContents()
    {
        Font font = parent.getFont();
        Color bg = parent.getBackground();
        this.setFont(font);
        this.setBackground(bg);

        text = new Text(this, SWT.LEFT);
        text.setFont(font);
        // Create a decorated field for a text control
        field = new ControlDecoration(text, SWT.BEGINNING);
        field.setImage(decorator.getImage());

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

        createHelpControl(this);
        this.setLayout(new ButtonEditorLayout());
    }

    @Override
    protected void editingDone()
    {
        value = text.getText().trim();
        if (decorator.equals(DecoratorType.PATTERN))
            value = PatternUtil.smartFix(value.toString(), false);

        fireValueChangedEvent(value, this);

    }

    private void createHelpControl(Composite parent)
    {
        button = new ImageHyperlink(parent, SWT.CENTER);
        button.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HELP));
        button.setFont(parent.getFont());
        button.setBackground(parent.getBackground());
        button.setToolTipText(JFaceResources.getString("helpToolTip"));//$NON-NLS-1$
        button.addHyperlinkListener(new HyperlinkAdapter()
        {
            public void linkActivated(HyperlinkEvent e)
            {
                PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(
                                "/org.eclipse.mat.ui.help/reference/selectingqueries.html#queryarguments__"//$NON-NLS-1$
                                                + decorator.getHelpLink());
            }
        });

    }

    @Override
    public void dispose()
    {
        super.dispose();
        this.field.dispose();
    }

}
