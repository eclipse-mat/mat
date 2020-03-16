/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - tooltip
 *******************************************************************************/
package org.eclipse.mat.ui.internal.panes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.HeapObjectContextArgument;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.internal.viewer.RefinedTableViewer;
import org.eclipse.mat.ui.internal.viewer.RefinedTreeViewer;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;

public class QueryResultPane extends AbstractEditorPane implements ISelectionProvider
{
    private List<ISelectionChangedListener> listeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    protected Composite top;
    protected RefinedResultViewer viewer;
    protected QueryResult srcQueryResult;

    public void createPartControl(Composite parent)
    {
        top = parent;

        makeActions();
    }

    protected void makeActions()
    {}

    // //////////////////////////////////////////////////////////////
    // initialization
    // //////////////////////////////////////////////////////////////

    @Override
    public void initWithArgument(Object argument)
    {
        srcQueryResult = (QueryResult)argument;
        RefinedResultViewer viewer = createViewer(srcQueryResult);

        activateViewer(viewer);

        firePropertyChange(IWorkbenchPart.PROP_TITLE);
        firePropertyChange(MultiPaneEditor.PROP_FOLDER_IMAGE);
    }

    // //////////////////////////////////////////////////////////////
    // status indicators
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return viewer != null ? viewer.getQueryResult().getTitle() : Messages.QueryResultPane_QueryResult;
    }

    @Override
    public Image getTitleImage()
    {
        Image image = viewer != null ? MemoryAnalyserPlugin.getDefault().getImage(viewer.getQueryResult().getQuery())
                        : null;
        return image != null ? image : MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.QUERY);
    }

    @Override
    public String getTitleToolTip()
    {
        return viewer != null ? viewer.getQueryResult().getTitleToolTip() : null;
    }

    @Override
    public void setFocus()
    {
        if (viewer != null)
            viewer.setFocus();
    }

    @Override
    protected void editorContextMenuAboutToShow(PopupMenu menu)
    {
        menu.addSeparator();
        viewer.addContextMenu(menu);
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        viewer.contributeToToolBar(manager);
        IResult subject = viewer.getQueryResult().getSubject();
        if (subject instanceof IResultTree)
        {
            manager.add(new Separator());
            addShowAsHistogramAction(manager, (IResultTree) subject);
        }
    }

    private void addShowAsHistogramAction(IToolBarManager manager, final IResultTree subject)
    {
        Action showAsHistogramAction = new Action()
        {
            @Override
            public void run()
            {
                boolean foundObj = false;
                // we are interested in the objectIds of the 1st level elements
                List<?> elements = subject.getElements();
                final List<IContextObject> contextObjects = new ArrayList<IContextObject>();
                for (int i = 0; i < elements.size(); i++)
                {
                    IContextObject context = ((IStructuredResult) subject).getContext(elements.get(i));
                    if (context == null)
                        continue;

                    contextObjects.add(context);

                    if (context instanceof IContextObjectSet)
                    {
                        foundObj = foundObj || ((IContextObjectSet) context).getObjectIds().length > 0;
                    }
                    else if (context.getObjectId() >= 0)
                    {
                        foundObj = true;;
                    }
                }
                if (!foundObj)
                {
                    ErrorHelper.showInfoMessage(Messages.QueryResultPane_InfoMessage);
                }

                try
                {
                    IEditorPart editor = site.getPage().getActiveEditor();
                    QueryDescriptor descriptor = QueryRegistry.instance().getQuery("histogram"); //$NON-NLS-1$
                    ArgumentSet set = descriptor.createNewArgumentSet(getQueryContext());
                    HeapObjectContextArgument objs = new HeapObjectContextArgument((ISnapshot)getQueryContext().get(ISnapshot.class, null), contextObjects, getTitle());
                    set.setArgumentValue(descriptor.getArgumentByName("objects"), objs); //$NON-NLS-1$

                    QueryExecution.execute((HeapEditor) editor, ((HeapEditor) editor).getActiveEditor().getPaneState(),
                                    null, set, false, false);

                }
                catch (SnapshotException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }

            }
        };
        showAsHistogramAction.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SHOW_AS_HISTOGRAM));
        showAsHistogramAction.setToolTipText(Messages.QueryResultPane_ShowAsHistogram);
        manager.add(showAsHistogramAction);
    }

    protected RefinedResultViewer createViewer(QueryResult queryResult)
    {
        IResult subject = queryResult.getSubject();

        if (subject instanceof IResultTree)
        {
            RefinedTree refinedTree = (RefinedTree) new RefinedResultBuilder(getQueryContext(), //
                            (IResultTree) subject).build();
            return new RefinedTreeViewer(getQueryContext(), queryResult, refinedTree);
        }
        else if (subject instanceof IResultTable)
        {
            RefinedTable refinedTable = (RefinedTable) new RefinedResultBuilder(getQueryContext(), //
                            (IResultTable) subject).build();
            return new RefinedTableViewer(getQueryContext(), queryResult, refinedTable);
        }
        else
        {
            throw new IllegalArgumentException(subject.getClass().getName());
        }
    }

    protected void activateViewer(RefinedResultViewer viewer)
    {
        this.viewer = viewer;
        top.setRedraw(false);

        try
        {
            viewer.init(top, ((MultiPaneEditorSite) getEditorSite()).getMultiPageEditor(), this);
            hookContextMenu(viewer.getControl());
            hookContextAwareListeners();
            MultiPaneEditor multiPaneEditor = ((MultiPaneEditorSite) getEditorSite()).getMultiPageEditor();
            multiPaneEditor.updateToolbar();
        }
        finally
        {
            top.layout();
            top.setRedraw(true);
        }
    }

    protected void deactivateViewer()
    {
        // context menu is disposed when a new one is created
        unhookContextAwareListeners();
        viewer.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // selection provider
    // //////////////////////////////////////////////////////////////

    /** delay selection events -> do not flood object inspector */
    private Listener proxy = new Listener()
    {
        boolean arrowKeyDown = false;
        int[] count = new int[1];

        public void handleEvent(final Event e)
        {
            switch (e.type)
            {
                case SWT.KeyDown:
                    arrowKeyDown = ((e.keyCode == SWT.ARROW_UP) || (e.keyCode == SWT.ARROW_DOWN)
                                    || (e.keyCode == SWT.ARROW_LEFT) || e.keyCode == SWT.ARROW_RIGHT)
                                    && e.stateMask == 0;
                    //$FALL-THROUGH$
                case SWT.Selection:
                    count[0]++;
                    final int id = count[0];
                    viewer.getControl().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            if (arrowKeyDown)
                            {
                                viewer.getControl().getDisplay().timerExec(250, new Runnable()
                                {
                                    public void run()
                                    {
                                        if (id == count[0])
                                        {
                                            forwardSelectionChangedEvent();
                                        }
                                    }
                                });
                            }
                            else
                            {
                                forwardSelectionChangedEvent();
                            }
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    };

    protected void hookContextAwareListeners()
    {
        Control control = viewer.getControl();
        control.addListener(SWT.Selection, proxy);
        control.addListener(SWT.KeyDown, proxy);
    }

    protected void unhookContextAwareListeners()
    {
        Control control = viewer.getControl();
        control.removeListener(SWT.Selection, proxy);
        control.removeListener(SWT.KeyDown, proxy);
    }

    private void forwardSelectionChangedEvent()
    {
        List<ISelectionChangedListener> l = new ArrayList<ISelectionChangedListener>(listeners);
        for (ISelectionChangedListener listener : l)
            listener.selectionChanged(new SelectionChangedEvent(this, convertSelection(viewer.getSelection())));
    }

    public ISelection getSelection()
    {
        return viewer != null ? convertSelection(viewer.getSelection()) : StructuredSelection.EMPTY;
    }

    private ISelection convertSelection(IStructuredSelection selection)
    {
        if (!selection.isEmpty())
        {
            List<IContextObject> menuContext = new ArrayList<IContextObject>();

            for (Iterator<?> iter = selection.iterator(); iter.hasNext();)
            {
                Object selected = iter.next();
                IContextObject ctx = getInspectorContextObject(selected);
                if (ctx != null)
                    menuContext.add(ctx);
            }

            if (!menuContext.isEmpty())
                return new StructuredSelection(menuContext);
        }

        // return an empty selection
        return StructuredSelection.EMPTY;
    }

    private IContextObject getInspectorContextObject(Object subject)
    {
        return viewer.getQueryResult().getDefaultContextProvider().getContext(subject);
    }

    public void setSelection(ISelection selection)
    {
        // not supported (unable to convert the IContextObject
        // back to the original object)
        throw new UnsupportedOperationException();
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
    }

    public QueryResult getSrcQueryResult()
    {
        return srcQueryResult;
    }

    public <T> T getAdapter(Class<T> adapter)
    {
        if (adapter.isAssignableFrom(srcQueryResult.getClass()))
        {
            return (adapter.cast(srcQueryResult));
        }
        if (adapter.isAssignableFrom(RefinedStructuredResult.class))
        {
            return (adapter.cast(viewer.getResult()));
        }
        if (adapter.isAssignableFrom(srcQueryResult.getSubject().getClass()))
        {
            return (adapter.cast(srcQueryResult.getSubject()));
        }
        return super.getAdapter(adapter);
    }
}
