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
package org.eclipse.mat.ui.internal.panes;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;


public class TextViewPane extends AbstractEditorPane
{
    Text text;

    public void createPartControl(Composite parent)
    {
        text = new Text(parent, SWT.MULTI);
        text.setEditable(false);
        
        makeActions();
        hookContextMenu();
    }

    private void makeActions()
    {
    }

    private void hookContextMenu()
    {
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {}

    @Override
    public void initWithArgument(final Object param)
    {
        if (!(param instanceof String))
            return;

        text.setText((String)param);
    }

    // //////////////////////////////////////////////////////////////
    // methods
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return "Text Display";
    }

    @Override
    public Image getTitleImage()
    {
        return null;
    }

}
