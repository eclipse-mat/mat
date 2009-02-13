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
package org.eclipse.mat.ui.internal.browser;

import org.eclipse.jface.resource.DeviceResourceException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

class QueryBrowserItem
{
    boolean lastInCategory;
    QueryBrowserPopup.Element element;
    QueryBrowserProvider provider;

    int start;
    int end;

    QueryBrowserItem(QueryBrowserPopup.Element element, QueryBrowserProvider provider, int start, int end)
    {
        this.element = element;
        this.provider = provider;
        this.start = start;
        this.end = end;
    }

    Image getImage(QueryBrowserPopup.Element element, ResourceManager resourceManager)
    {
        return findOrCreateImage(element.getImageDescriptor(), resourceManager);
    }

    private Image findOrCreateImage(ImageDescriptor imageDescriptor, ResourceManager resourceManager)
    {
        if (imageDescriptor == null) { return null; }
        Image image = (Image) resourceManager.find(imageDescriptor);
        if (image == null)
        {
            try
            {
                image = resourceManager.createImage(imageDescriptor);
            }
            catch (DeviceResourceException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
        }
        return image;
    }

    public void measure(Event event, TextLayout textLayout, ResourceManager resourceManager, TextStyle boldStyle)
    {
        Table table = ((TableItem) event.item).getParent();
        textLayout.setFont(table.getFont());
        if (event.index == 0)
        {
            if (element == null)
            {
                textLayout.setText(provider.getName());
                textLayout.setStyle(boldStyle, 0, provider.getName().length());
            }
            else
            {
                Image image = getImage(element, resourceManager);
                if (image != null)
                {
                    Rectangle imageRect = image.getBounds();
                    event.width += imageRect.width + 2;
                    event.height = Math.max(event.height, imageRect.height + 2);
                }
                else
                {
                    event.width += 16 + 2;
                }

                textLayout.setText(element.getLabel());
                if (start != end)
                    textLayout.setStyle(boldStyle, start, end);
            }
        }
        Rectangle rect = textLayout.getBounds();
        event.width += rect.width + 2;
        event.height = Math.max(event.height, rect.height + 2);
    }

    public void paint(Event event, TextLayout textLayout, ResourceManager resourceManager, TextStyle boldStyle)
    {
        final Table table = ((TableItem) event.item).getParent();
        textLayout.setFont(table.getFont());

        if (event.index == 0)
        {
            if (element == null)
            {
                textLayout.setText(provider.getName());
                textLayout.setStyle(boldStyle, 0, provider.getName().length());
                Rectangle availableBounds = ((TableItem) event.item).getTextBounds(event.index);
                Rectangle requiredBounds = textLayout.getBounds();
                textLayout.draw(event.gc, availableBounds.x + 1, availableBounds.y
                                + (availableBounds.height - requiredBounds.height) / 2);
            }
            else
            {
                Rectangle availableBounds = ((TableItem) event.item).getTextBounds(event.index);

                Image image = getImage(element, resourceManager);
                if (image != null)
                {
                    event.gc.drawImage(image, event.x + 1, event.y + 1);
                    availableBounds.x += 1 + image.getBounds().width;
                }
                else
                {
                    availableBounds.x += 1 + 16;
                }

                textLayout.setText(element.getLabel());

                if (start != end)
                    textLayout.setStyle(boldStyle, start, end);

                Rectangle requiredBounds = textLayout.getBounds();
                textLayout.draw(event.gc, availableBounds.x, availableBounds.y
                                + (availableBounds.height - requiredBounds.height) / 2);
            }
        }

        if (lastInCategory)
        {
            event.gc.setForeground(table.getDisplay().getSystemColor(SWT.COLOR_GRAY));
            Rectangle bounds = ((TableItem) event.item).getBounds(event.index);
            event.gc.drawLine(Math.max(0, bounds.x - 1), bounds.y + bounds.height - 1, bounds.x + bounds.width,
                            bounds.y + bounds.height - 1);
        }
    }

    public void erase(Event event)
    {
        event.detail &= ~SWT.FOREGROUND;
    }
}
