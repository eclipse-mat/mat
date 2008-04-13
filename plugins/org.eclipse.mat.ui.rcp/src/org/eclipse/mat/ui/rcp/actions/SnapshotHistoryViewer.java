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
package org.eclipse.mat.ui.rcp.actions;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.intro.config.IIntroContentProvider;
import org.eclipse.ui.intro.config.IIntroContentProviderSite;
import org.eclipse.ui.intro.config.IIntroURL;
import org.eclipse.ui.intro.config.IntroURLFactory;
import org.osgi.framework.Bundle;

public class SnapshotHistoryViewer implements IIntroContentProvider
{
    private static final String ICON = "../intro/css/graphics/icons/heapdump16.gif";

    private Image bulletImage;
    private boolean disposed;
    private FormText formText;

    public void init(IIntroContentProviderSite site)
    {}

    public void createContent(String id, PrintWriter out)
    {
        if (disposed)
            return;

        List<SnapshotHistoryService.Entry> lastHeaps = SnapshotHistoryService.getInstance().getVisitedEntries();
        if (lastHeaps == null)
        {
            out.print("<p class=\"status-text\">");
            out.print("Please wait... the list is loading");
            out.println("</p>");
        }
        else
        {
            if (lastHeaps.size() > 0)
            {
                out.println("<ul id=\"snapshot_history\">");
                for (SnapshotHistoryService.Entry entry : lastHeaps)
                {
                    out.print("<li><img src =\"" + ICON + "\">");
                    out.print("<a class=\"topicList\" href=\"http://org.eclipse.ui.intro/runAction?"
                                    + "pluginId=org.eclipse.mat.ui.rcp&amp;"
                                    + "class=org.eclipse.mat.ui.rcp.actions.OpenEditorAction&amp;param=");
                    out.print(entry.getFilePath());
                    out.print("\">");
                    out.print(entry.getFilePath());
                    out.print("</a>");
                    out.println("</li>");
                }
            }
            else
            {
                out.print("<p class=\"status-text\">");
                out.print("History is empty");
                out.println("</p>");
            }
            out.println("</ul>");
        }
    }

    public void createContent(String id, Composite parent, FormToolkit toolkit)
    {
        if (disposed)
            return;
        List<SnapshotHistoryService.Entry> lastHeaps = SnapshotHistoryService.getInstance().getVisitedEntries();
        if (formText == null)
        {
            // a one-time pass
            formText = toolkit.createFormText(parent, true);
            formText.addHyperlinkListener(new HyperlinkAdapter()
            {
                public void linkActivated(HyperlinkEvent e)
                {
                    openHeapDump((String) e.getHref());
                }
            });
            bulletImage = createImage(new Path("../intro/css/graphics/icons/arrow.gif"));
            if (bulletImage != null)
                formText.setImage("bullet", bulletImage);
        }
        StringBuffer buffer = new StringBuffer();
        buffer.append("<form>");
        if (lastHeaps == null)
        {
            buffer.append("<p>");
            buffer.append("Please wait... the list is loading");
            buffer.append("</p>");
        }
        else
        {
            if (lastHeaps.size() > 0)
            {
                for (SnapshotHistoryService.Entry entry : lastHeaps)
                {
                    buffer.append("<li style=\"image\" value=\"bullet\">");
                    buffer.append("<img src =\"" + ICON + "\">");
                    buffer.append("<a href=\"http://org.eclipse.ui.intro/runAction?"
                                    + "standby=true&amp;pluginId=org.eclipse.mat.ui.rcp&amp;"
                                    + "class=org.eclipse.mat.ui.rcp.actions.OpenEditorAction&amp;param=");
                    buffer.append(entry.getFilePath());
                    buffer.append("\">");
                    buffer.append(entry.getFilePath());
                    buffer.append("</a>");
                    buffer.append("</li>");
                }
            }
            else
            {
                buffer.append("<p>");
                buffer.append("History is empty");
                buffer.append("</p>");
            }
        }
        buffer.append("</form>");
        formText.setText(buffer.toString(), true, false);
    }

    private Image createImage(IPath path)
    {
        Bundle bundle = Platform.getBundle("org.eclipse.mat.ui.rcp");
        URL url = FileLocator.find(bundle, path, null);
        try
        {
            url = FileLocator.toFileURL(url);
            ImageDescriptor desc = ImageDescriptor.createFromURL(url);
            return desc.createImage();
        }
        catch (IOException e)
        {
            // $JL-EXC$ on image error do not fail but show no image at all
            return null;
        }
    }

    public void dispose()
    {
        if (bulletImage != null)
        {
            bulletImage.dispose();
            bulletImage = null;
        }
        disposed = true;
    }

    private void openHeapDump(final String href)
    {
        BusyIndicator.showWhile(PlatformUI.getWorkbench().getDisplay(), new Runnable()
        {
            public void run()
            {
                IIntroURL introUrl = IntroURLFactory.createIntroURL(href);
                if (introUrl != null)
                {
                    // execute the action embedded in the IntroURL
                    introUrl.execute();
                    return;
                }

            }
        });
    }
}
