/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - MAT Calcite
 *******************************************************************************/
package org.eclipse.mat.ui.internal.panes;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.actions.OpenHelpPageAction;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;

public class QueryTextResultPane extends AbstractEditorPane implements ISelectionProvider, LocationListener
{
    private Browser browser;
    private Text text;

    private IStructuredSelection selection;
    private List<ISelectionChangedListener> listeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    QueryContextMenu contextMenu;
    QueryResult queryResult;
    private Menu menu;

    public void createPartControl(Composite parent)
    {
        final Composite top = new Composite(parent, SWT.NONE);
        top.setLayout(new FillLayout(SWT.VERTICAL));

        try
        {
            browser = new Browser(top, SWT.NONE);
            browser.addLocationListener(this);
        }
        catch (SWTError e)
        {
            Text text1 = new Text(top, SWT.MULTI);
            text1.setText(MessageUtil.format(Messages.QueryTextResultPane_FailedBrowser, e.getLocalizedMessage()));
            PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.report"); //$NON-NLS-1$
            text = new Text(top, SWT.MULTI | SWT.READ_ONLY);
            ErrorHelper.logThrowableAndShowMessage(e, MessageUtil.format(Messages.QueryTextResultPane_FailedBrowser2, e.getLocalizedMessage()));
        }

        contextMenu = new QueryContextMenu(this, new ContextProvider((String) null)
        {
            public IContextObject getContext(Object obj)
            {
                return (IContextObject) obj;
            }
        });
    }

    @Override
    public void initWithArgument(Object argument)
    {
        if (!(argument instanceof QueryResult))
            return;

        queryResult = (QueryResult) argument;

        if (queryResult.getSubject() instanceof TextResult)
        {
            TextResult textResult = (TextResult) queryResult.getSubject();

            if (textResult.isHtml())
            {
                if (browser != null)
                    browser.setText(textResult.getText());
                else
                    text.setText(textResult.getText());
            }
            else
            {
                String html = "<pre>" + HTMLUtils.escapeText(textResult.getText()) + "</pre>"; //$NON-NLS-1$//$NON-NLS-2$
                if (browser != null)
                    browser.setText(html);
                else
                    text.setText(html);
            }
        }
        else if (queryResult.getSubject() instanceof DisplayFileResult)
        {
            try
            {
                DisplayFileResult r = (DisplayFileResult) queryResult.getSubject();
                if (browser != null)
                    browser.setUrl(r.getFile().toURI().toURL().toExternalForm());
                else
                    text.setText(r.getFile().toURI().toURL().toExternalForm());
            }
            catch (MalformedURLException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }
        else
        {
            if (browser != null)
                browser.setText(String.valueOf(queryResult.getSubject()));
            else
                text.setText(String.valueOf(queryResult.getSubject()));
        }

        firePropertyChange(IWorkbenchPart.PROP_TITLE);
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        if (queryResult.getQuery() != null && queryResult.getQuery().getHelpUrl() != null)
            manager.appendToGroup("help", new OpenHelpPageAction(queryResult.getQuery().getHelpUrl())); //$NON-NLS-1$

        super.contributeToToolBar(manager);
    }

    @Override
    public void setFocus()
    {
        // unregister the old global action handler (handles trees and tables)
        // to be able to use Ctrl-C to copy text
        site.getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), null);
        site.getActionBars().updateActionBars();
        if (browser != null)
            browser.setFocus();
        else
            text.setFocus();
    }

    public ISelection getSelection()
    {
        return selection != null ? selection : StructuredSelection.EMPTY;
    }

    public void changing(LocationEvent event)
    {
        QueryObjectLink url = QueryObjectLink.parse(event.location);
        if (url == null)
            return;

        switch (url.getType())
        {
            case OBJECT:
                onObjectLinkEvent(event, url);
                break;
            case QUERY:
                onQueryLinkEvent(event, url);
                break;
            case DETAIL_RESULT:
                break;
        }
    }

    private void onObjectLinkEvent(LocationEvent event, QueryObjectLink url)
    {
        try
        {
            final int objectId = getEditor().getQueryContext().mapToObjectId(url.getTarget());
            if (objectId < 0)
                return;

            this.selection = new StructuredSelection(new IContextObject()
            {
                public int getObjectId()
                {
                    return objectId;
                }
            });

            for (ISelectionChangedListener l : new ArrayList<ISelectionChangedListener>(listeners))
                l.selectionChanged(new SelectionChangedEvent(QueryTextResultPane.this, selection));

            PopupMenu m = new PopupMenu();
            contextMenu.addContextActions(m, selection, null);

            if (menu != null && !menu.isDisposed())
                menu.dispose();

            menu = m.createMenu(getEditorSite().getActionBars().getStatusLineManager(), browser);
            menu.setVisible(true);

        }
        catch (Exception e)
        {
            ErrorHelper.logThrowableAndShowMessage(e, MessageUtil.format(
                            Messages.QueryTextResultPane_UnableToMapAddress, url.getTarget()));
        }
        event.doit = false;
    }

    private void onQueryLinkEvent(LocationEvent event, QueryObjectLink url)
    {
        try
        {
            ArgumentSet set = CommandLine.parse(getQueryContext(), url.getTarget());
            QueryExecution.execute(getEditor(), this.getPaneState(), null, set, false, true);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }

        event.doit = false;
    }

    public void changed(LocationEvent event)
    {}

    public String getTitle()
    {
        return queryResult != null ? queryResult.getTitle() : Messages.QueryTextResultPane_Text;
    }

    @Override
    public Image getTitleImage()
    {
        Image image = queryResult != null ? MemoryAnalyserPlugin.getDefault().getImage(queryResult.getQuery()) : null;
        return image != null ? image : MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.QUERY);
    }

    @Override
    public String getTitleToolTip()
    {
        return queryResult != null ? queryResult.getTitleToolTip() : null;
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
    }

    public void setSelection(ISelection selection)
    {
        throw new UnsupportedOperationException();
    }

    public <T> T getAdapter(Class<T> adapter)
    {
        if (adapter.isAssignableFrom(queryResult.getClass()))
        {
            return (adapter.cast(queryResult));
        }
        if (adapter.isAssignableFrom(queryResult.getSubject().getClass()))
        {
            return (adapter.cast(queryResult.getSubject()));
        }
        return super.getAdapter(adapter);
    }

    @Override
    public void dispose()
    {
        super.dispose();
        if (menu != null && !menu.isDisposed())
            menu.dispose();
    }

}
