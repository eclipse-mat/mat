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
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.impl.query.CommandLine;
import org.eclipse.mat.impl.query.DiggerUrl;
import org.eclipse.mat.impl.query.IndividualObjectUrl;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.HeapEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.query.ArgumentContextProvider;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;
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


public class QueryTextResultPane extends HeapEditorPane implements ISelectionProvider, LocationListener
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
            browser.setText(((TextResult) queryResult.getSubject()).getHtml());
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
        DiggerUrl url = DiggerUrl.parse(event.location);
        if (url == null)
            return;

        if (url instanceof IndividualObjectUrl)
        {
            IndividualObjectUrl objectUrl = (IndividualObjectUrl) url;

            try
            {
                final int objectId = getSnapshotInput().getSnapshot().mapAddressToId(objectUrl.getObjectAddress());
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
                                "Unable to map address 0x{0} to an object on the heap.", new Object[] { Long
                                                .toHexString(objectUrl.getObjectAddress()) }));
            }
            event.doit = false;
        }
        else if ("query".equals(url.getType()))
        {
            try
            {
                HeapEditor editor = (HeapEditor) ((MultiPaneEditorSite) getEditorSite()).getMultiPageEditor();
                ArgumentSet set = CommandLine.parse(new ArgumentContextProvider(editor), url.getContent());
                QueryExecution.execute(editor, this.getPaneState(), null, set, false, true);
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }

            event.doit = false;
        }
        else if ("result".equals(url.getType()))
        {
            QueryResult parent = queryResult.getParent();

            if (parent != null)
            {
                String path = url.getContent();
                int index = Integer.parseInt(path.substring(3)) - 1;

                CompositeResult.Entry entry = ((CompositeResult) parent.getSubject()).getResultEntries().get(index);
                HeapEditor editor = (HeapEditor) ((MultiPaneEditorSite) getEditorSite()).getMultiPageEditor();
                String label = entry.getName();
                if (label == null)
                    label = parent.getTitle() + " " + (index + 1);
                QueryExecution.displayResult(editor, this.getPaneState(), null, new QueryResult(parent, null, label, entry.getResult()), false);
            }

            event.doit = false;
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
        Image image = queryResult != null ? ImageHelper.getImage(queryResult.getQuery()) : null;
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
