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
package org.eclipse.mat.ui.util;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * Using this class one can create a tool bar button that drops down its menu
 * also when the button itself was clicked (user don't have to aim at the small
 * arrow next to it).
 */
public abstract class EasyToolBarDropDown extends Action implements IMenuCreator
{
    private IStatusLineManager statusLineManager;
    private ToolBar toolBar;
    private Menu menu;

    public EasyToolBarDropDown(String text, ImageDescriptor image, AbstractEditorPane pane)
    {
        this(text, image, pane.getEditor());
    }

    public EasyToolBarDropDown(String text, ImageDescriptor image, MultiPaneEditor editor)
    {
        super(text, image);
        this.toolBar = editor.getToolBarManager().getControl();
        this.statusLineManager = editor.getEditorSite().getActionBars().getStatusLineManager();
        this.setMenuCreator(this);
    }

    @Override
    public final void run()
    {
        // make the drop-down menu appear also in case the button itself
        // (and not the small arrow on its right side) is clicked

        // find the ToolItem representing this Action by iterating
        // through all tool items and checking the Action associated to
        // them

        ToolItem myself = null;
        for (ToolItem item : toolBar.getItems())
        {
            if (item != null //
                            && item.getData() != null && item.getData() instanceof ActionContributionItem //
                            && ((ActionContributionItem) item.getData()).getAction() == this)
            {
                myself = item;
                break;
            }
        }

        if (myself == null)
            return;

        // display the menu below the drop down item
        Menu m = getMenu(myself.getParent());
        Rectangle b = myself.getBounds();
        Point p = myself.getParent().toDisplay(new Point(b.x, b.y + b.height));
        m.setLocation(p.x, p.y);
        m.setVisible(true);
    }

    public abstract void contribute(PopupMenu menu);

    // //////////////////////////////////////////////////////////////
    // menu creator
    // //////////////////////////////////////////////////////////////

    public final void dispose()
    {
        if (menu != null && !menu.isDisposed())
            menu.dispose();
    }

    public final Menu getMenu(Control parent)
    {
        if (menu != null && !menu.isDisposed())
            menu.dispose();

        PopupMenu popup = new PopupMenu();
        contribute(popup);
        return menu = popup.createMenu(statusLineManager, parent);
    }

    public final Menu getMenu(Menu parent)
    {
        return null;
    }
}
