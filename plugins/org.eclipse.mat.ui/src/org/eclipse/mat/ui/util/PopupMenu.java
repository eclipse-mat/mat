/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ExternalActionManager;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

public final class PopupMenu
{
    private static final Object SEPARATOR = new Object();

    private String name;
    private String toolTipText;
    private String actionDefinitionId;
    private ImageDescriptor imageDescriptor;
    private List<Object> children = new ArrayList<Object>();

    private Boolean showImages;

    public PopupMenu()
    {
        this.name = Messages.PopupMenu_NONE;
    }

    public PopupMenu(String name)
    {
        this.name = name;
    }

    public void add(Action action)
    {
        children.add(action);
    }

    public void add(PopupMenu menu)
    {
        children.add(menu);
    }

    public void addSeparator()
    {
        children.add(SEPARATOR);
    }

    public PopupMenu getChildMenu(String name)
    {
        for (Object child : children)
        {
            if ((child instanceof PopupMenu) && ((PopupMenu) child).name.equals(name))
                return (PopupMenu) child;
        }
        return null;
    }

    public void setToolTipText(String tooltip)
    {
        this.toolTipText = tooltip;
    }

    public void setImageDescriptor(ImageDescriptor imageDescriptor)
    {
        this.imageDescriptor = imageDescriptor;
    }

    public void setActionDefinitionId(String actionDefinitionId)
    {
        this.actionDefinitionId = actionDefinitionId;
    }

    public void addToMenu(IStatusLineManager statusLineManager, Menu menu)
    {
        Listener hideListener = new HideListener(statusLineManager);
        Listener showListener = new ShowListener(statusLineManager);
        Listener armListener = new ArmListener(statusLineManager);
        Listener selectionListener = new SelectionListener();

        addToMenu(menu, hideListener, showListener, armListener, selectionListener);
    }

    public Menu createMenu(IStatusLineManager statusLineManager, Control parent)
    {
        final Menu menu = new Menu(parent.getShell());
        addToMenu(statusLineManager, menu);
        return menu;
    }

    private void addToMenu(Menu menu, Listener hide, Listener show, Listener arm, Listener selection)
    {
        for (Object item : children)
        {
            if (item instanceof Action)
            {
                Action action = (Action) item;

                int flags = SWT.PUSH;

                int style = action.getStyle();
                if (style == IAction.AS_CHECK_BOX)
                    flags = SWT.CHECK;
                else if (style == IAction.AS_RADIO_BUTTON)
                    flags = SWT.RADIO;

                MenuItem menuItem = new MenuItem(menu, flags);

                String acceleratorText = null;
                ExternalActionManager.ICallback callback = ExternalActionManager.getInstance().getCallback();
                String commandId = action.getActionDefinitionId();
                if (commandId != null && callback != null)
                    acceleratorText = callback.getAcceleratorText(commandId);

                if (acceleratorText == null)
                    menuItem.setText(action.getText());
                else
                    menuItem.setText(action.getText() + '\t' + acceleratorText);

                if (flags != SWT.PUSH)
                    menuItem.setSelection(action.isChecked());

                if (showImages())
                {
                    ImageDescriptor id = action.getImageDescriptor();
                    if (id != null)
                        menuItem.setImage(id.createImage());
                }

                menuItem.setData(action);
                menuItem.addListener(SWT.Selection, selection);
                menuItem.addListener(SWT.Arm, arm);
            }
            else if (item instanceof PopupMenu)
            {
                PopupMenu popup = (PopupMenu) item;

                if (!popup.isEmpty())
                {
                    ImageDescriptor imageDescriptor = popup.getImageDescriptor();

                    Menu subMenu = new Menu(menu.getShell(), SWT.DROP_DOWN);
                    subMenu.addListener(SWT.Hide, hide);
                    subMenu.addListener(SWT.Show, show);

                    popup.addToMenu(subMenu, hide, show, arm, selection);

                    MenuItem menuItem = new MenuItem(menu, SWT.CASCADE);

                    String acceleratorText = null;
                    ExternalActionManager.ICallback callback = ExternalActionManager.getInstance().getCallback();
                    if (popup.actionDefinitionId != null && callback != null)
                        acceleratorText = callback.getAcceleratorText(popup.actionDefinitionId);

                    if (acceleratorText == null)
                        menuItem.setText(popup.name);
                    else
                        menuItem.setText(popup.name + '\t' + acceleratorText);

                    if (imageDescriptor != null)
                        menuItem.setImage(imageDescriptor.createImage());
                    menuItem.setMenu(subMenu);
                }
            }
            else if (item == SEPARATOR)
            {
                int count = menu.getItemCount();
                if (count > 0 && ((menu.getItem(count - 1).getStyle() & SWT.SEPARATOR) != SWT.SEPARATOR))
                {
                    new MenuItem(menu, SWT.SEPARATOR);
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // internal methods
    // //////////////////////////////////////////////////////////////

    private boolean showImages()
    {
        return showImages != null ? showImages.booleanValue() : true;
    }

    private ImageDescriptor getImageDescriptor()
    {
        if (showImages != null || imageDescriptor != null)
            return imageDescriptor;

        showImages = Boolean.FALSE;

        for (int ii = 0; ii < children.size(); ii++)
        {
            ImageDescriptor d = null;
            Object child = children.get(ii);

            if (child instanceof Action)
                d = ((Action) child).getImageDescriptor();
            else if (child instanceof PopupMenu)
                d = ((PopupMenu) child).getImageDescriptor();

            if (ii == 0)
            {
                imageDescriptor = d;
            }
            else
            {
                if ((d != null && !d.equals(imageDescriptor)) //
                                || (d == null && imageDescriptor != null))
                {
                    imageDescriptor = null;
                    showImages = Boolean.TRUE;
                    break;
                }
            }
        }

        return imageDescriptor;
    }

    private boolean isEmpty()
    {
        if (children.isEmpty())
            return true;

        for (Object child : children)
        {
            if (child instanceof PopupMenu)
            {
                if (!((PopupMenu) child).isEmpty())
                    return false;
                continue;
            }
            else if (child == SEPARATOR)
            {
                continue;
            }
            else
            {
                return false;
            }
        }

        return true;
    }

    // //////////////////////////////////////////////////////////////
    // listener implementations
    // //////////////////////////////////////////////////////////////

    private static final class SelectionListener implements Listener
    {
        public void handleEvent(Event event)
        {
            try
            {
                Action action = (Action) event.widget.getData();

                int style = action.getStyle();
                if ((style & (SWT.TOGGLE | SWT.CHECK)) != 0)
                {
                    if (action.getStyle() == IAction.AS_CHECK_BOX)
                        action.setChecked(((MenuItem) event.widget).getSelection());
                }

                action.run();
            }
            catch (RuntimeException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }

        }
    }

    private static final class HideListener implements Listener
    {
        IStatusLineManager statusLineManager;

        private HideListener(IStatusLineManager statusLineManager)
        {
            this.statusLineManager = statusLineManager;
        }

        public void handleEvent(Event event)
        {
            statusLineManager.setMessage(""); //$NON-NLS-1$
        }
    }

    private static final class ShowListener implements Listener
    {
        IStatusLineManager statusLineManager;

        private ShowListener(IStatusLineManager statusLineManager)
        {
            this.statusLineManager = statusLineManager;
        }

        public void handleEvent(Event event)
        {
            statusLineManager.setMessage("");//$NON-NLS-1$
        }
    }

    private static final class ArmListener implements Listener
    {
        IStatusLineManager statusLineManager;

        private ArmListener(IStatusLineManager statusLineManager)
        {
            this.statusLineManager = statusLineManager;
        }

        public void handleEvent(Event event)
        {
            String tooltip = null;

            Object data = event.widget.getData();
            if (data instanceof Action)
                tooltip = ((Action) data).getToolTipText();
            else if (data instanceof PopupMenu)
                tooltip = ((PopupMenu) data).toolTipText;

            if (tooltip == null)
                tooltip = "";//$NON-NLS-1$

            statusLineManager.setMessage(tooltip);
        }
    }
}
