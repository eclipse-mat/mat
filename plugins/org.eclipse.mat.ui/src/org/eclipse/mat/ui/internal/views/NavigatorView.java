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
package org.eclipse.mat.ui.internal.views;

import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.MessagePage;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PageBookView;

public class NavigatorView extends PageBookView
{
    private static String defaultMessage = Messages.NavigatorView_ViewNotAvailable;

    @Override
    protected IPage createDefaultPage(PageBook book)
    {
        MessagePage page = new MessagePage();
        initPage(page);
        page.createControl(book);
        page.setMessage(defaultMessage);
        return page;
    }

    @Override
    protected PageRec doCreatePage(IWorkbenchPart part)
    {
        if (!(part instanceof MultiPaneEditor))
            return null;

        NavigatorViewPage page = new NavigatorViewPage((MultiPaneEditor) part);
        initPage(page);
        page.createControl(getPageBook());
        return new PageRec(part, page);

    }

    @Override
    protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord)
    {
        NavigatorViewPage page = (NavigatorViewPage) pageRecord.page;
        page.dispose();
        pageRecord.dispose();

    }

    @Override
    protected IWorkbenchPart getBootstrapPart()
    {
        IWorkbenchPage page = getSite().getPage();
        if (page != null)
            return page.getActiveEditor();
        else
            return null;
    }

    @Override
    protected boolean isImportant(IWorkbenchPart part)
    {
        return (part instanceof MultiPaneEditor);
    } 

}
