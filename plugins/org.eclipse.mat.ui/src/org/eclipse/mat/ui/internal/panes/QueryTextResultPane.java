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

import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.RendererRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchPart;

public class QueryTextResultPane extends AbstractEditorPane implements ISelectionProvider, LocationListener
{
    private Browser browser;

    private IStructuredSelection selection;
    private List<ISelectionChangedListener> listeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    QueryContextMenu contextMenu;
    QueryResult queryResult;
    private Menu menu;

    public void createPartControl(Composite parent)
    {
        final Composite top = new Composite(parent, SWT.NONE);
        top.setLayout(new FillLayout());

        browser = new Browser(top, SWT.NONE);
        browser.addLocationListener(this);

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
                browser.setText(textResult.getText());
            }
            else
            {
                try
                {
                    IOutputter outputter = RendererRegistry.instance().match("html", TextResult.class);
                    StringWriter writer = new StringWriter();
                    outputter.embedd(null, textResult, writer);
                    browser.setText(writer.toString());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        else if (queryResult.getSubject() instanceof DisplayFileResult)
        {
            try
            {
                DisplayFileResult r = (DisplayFileResult) queryResult.getSubject();
                browser.setUrl(r.getFile().toURL().toExternalForm());
            }
            catch (MalformedURLException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }
        else
        {
            browser.setText(String.valueOf(queryResult.getSubject()));
        }

        firePropertyChange(IWorkbenchPart.PROP_TITLE);
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
                    contextMenu.addContextActions(m, selection);

                    if (menu != null && !menu.isDisposed())
                        menu.dispose();

                    menu = m.createMenu(getEditorSite().getActionBars().getStatusLineManager(), browser);
                    menu.setVisible(true);
                }
                catch (Exception e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e, MessageFormat.format(
                                    "Unable to map address {0} to an object on the heap.", url.getTarget()));
                }
                event.doit = false;
                break;
            }
            case QUERY:
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
        }
    }

    public void changed(LocationEvent event)
    {}

    public String getTitle()
    {
        return queryResult != null ? queryResult.getTitle() : "Text";
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

    @Override
    public void dispose()
    {
        super.dispose();
        if (menu != null && !menu.isDisposed())
            menu.dispose();
    }

}
