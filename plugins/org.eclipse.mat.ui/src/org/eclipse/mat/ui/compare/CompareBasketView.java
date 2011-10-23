/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 * Andrew Johnson - compare tables query browser
 ******************************************************************************/
package org.eclipse.mat.ui.compare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.accessibility.AccessibleCompositeAdapter;
import org.eclipse.mat.ui.actions.QueryDropDownMenuAction;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.panes.TableResultPane;
import org.eclipse.mat.ui.snapshot.panes.HistogramPane;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.IPolicy;
import org.eclipse.mat.ui.util.NavigatorState.IStateChangeListener;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;

public class CompareBasketView extends ViewPart
{

	public static final String ID = "org.eclipse.mat.ui.views.CompareBasketView"; //$NON-NLS-1$

	private Table table;
	private TableViewer tableViewer;
	private Action compareAction;
	private Action clearAction;
	private MoveAction moveUpAction;
	private MoveAction moveDownAction;
	private Action removeAction;
	private Set<MultiPaneEditor> editors = new HashSet<MultiPaneEditor>();

	List<ComparedResult> results = new ArrayList<ComparedResult>();

	@Override
	public void createPartControl(Composite parent)
	{
		createTable(parent);

		addToolbar();
		hookContextMenu();
	}

	private void createTable(Composite parent)
	{
		tableViewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		this.table = tableViewer.getTable();
        AccessibleCompositeAdapter.access(table);

		TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.LEFT);
		TableColumn tableColumn = column.getColumn();
		tableColumn.setText(Messages.CompareBasketView_ResultsToBeComparedColumnHeader);
		tableColumn.setWidth(200);

		TableViewerColumn heapDumpColumn = new TableViewerColumn(tableViewer, SWT.LEFT);
		tableColumn = heapDumpColumn.getColumn();
		tableColumn.setText(Messages.CompareBasketView_HeapDumpColumnHeader);
		tableColumn.setWidth(400);

		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		tableViewer.setContentProvider(new CompareContentProvider());
		tableViewer.setLabelProvider(new CompareLabelProvider());
		tableViewer.setInput(results);
	}

	private void addToolbar()
	{
		IToolBarManager manager = getViewSite().getActionBars().getToolBarManager();
		moveDownAction = new MoveAction(SWT.DOWN);
		manager.add(moveDownAction);

		moveUpAction = new MoveAction(SWT.UP);
		manager.add(moveUpAction);

		manager.add(new Separator());

		removeAction = new RemoveAction();
		manager.add(removeAction);

		clearAction = new RemoveAllAction();
		manager.add(clearAction);

		manager.add(new Separator());

		compareAction = new CompareAction();
		manager.add(compareAction);
	}

	@Override
	public void setFocus()
	{
	// TODO Auto-generated method stub

	}

	public void addResultToCompare(PaneState state, MultiPaneEditor editor)
	{
		AbstractEditorPane pane = editor.getEditor(state);
		ComparedResult entry = null;
		if (pane instanceof HistogramPane)
		{
			entry = new ComparedResult(state, editor, ((HistogramPane) pane).getHistogram());
		}
		else if (pane instanceof TableResultPane)
		{
			entry = new ComparedResult(state, editor, (IResultTable) ((TableResultPane) pane).getSrcQueryResult().getSubject());
		}
	    else if (pane instanceof QueryResultPane)
        {
            entry = new ComparedResult(state, editor, (IResultTree) ((QueryResultPane) pane).getSrcQueryResult().getSubject());
	    }

		if (entry != null)
		{
		    results.add(entry);
		}
		tableViewer.refresh();

		clearAction.setEnabled(true);
		if (results.size() > 1) compareAction.setEnabled(true);

		// is the result from a unknown editor => add some cleanup actions
		if (editors.add(editor))
		{
			// listen if the editor gets closed
			editor.getSite().getPage().addPartListener(new IPartListener2() {

				public void partVisible(IWorkbenchPartReference partRef)
				{}

				public void partOpened(IWorkbenchPartReference partRef)
				{}

				public void partInputChanged(IWorkbenchPartReference partRef)
				{}

				public void partHidden(IWorkbenchPartReference partRef)
				{}

				public void partDeactivated(IWorkbenchPartReference partRef)
				{}

				public void partBroughtToTop(IWorkbenchPartReference partRef)
				{}

				public void partActivated(IWorkbenchPartReference partRef)
				{}

				public void partClosed(IWorkbenchPartReference partRef)
				{
					IWorkbenchPart part = partRef.getPart(false);
					List<ComparedResult> toBeRemoved = new ArrayList<ComparedResult>();
					for (ComparedResult res : results)
					{
						if (res.editor == part) toBeRemoved.add(res);
					}
					editors.remove(part);
					results.removeAll(toBeRemoved);

                    if (!table.isDisposed())
                    {
                        tableViewer.refresh();
                    }
					updateButtons();
				}

			});

			// listen if some result from the editor gets closed
			editor.getNavigatorState().addChangeStateListener(new IStateChangeListener() {

				public void onStateChanged(PaneState state)
				{
					if (state != null && !state.isActive())
					{
						List<ComparedResult> toBeRemoved = new ArrayList<ComparedResult>();
						for (ComparedResult res : results)
						{
							if (res.state == state) toBeRemoved.add(res);
						}
						results.removeAll(toBeRemoved);
						tableViewer.refresh();
						updateButtons();
					}
				}
			});

		}

	}

	public static boolean accepts(PaneState state, MultiPaneEditor editor)
	{
		AbstractEditorPane pane = editor.getEditor(state);
		return (pane instanceof HistogramPane) || (pane instanceof TableResultPane) ||
		                pane instanceof QueryResultPane && 
		                ((QueryResultPane) pane).getSrcQueryResult().getSubject() instanceof IResultTree;
	}

	private void hookContextMenu()
	{
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager)
			{
				editorContextMenuAboutToShow(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(table);
		this.table.setMenu(menu);
        menu.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuShown(MenuEvent e)
            {
                TableItem[] items = table.getSelection();
                final List<IStructuredResult> tables = new ArrayList<IStructuredResult>(items.length);
                final List<IQueryContext> contexts = new ArrayList<IQueryContext>(items.length);
                for (TableItem item : items)
                {
                    ComparedResult result = (ComparedResult) item.getData();
                    tables.add(result.table);
                    contexts.add(result.editor.getQueryContext());
                }
                QueryDropDownMenuAction qa = new QueryDropDownMenuAction(getEditor(), new ComparePolicy(tables, contexts, getEditor().getQueryContext()), false);
                PopupMenu menu1 = new PopupMenu();
                qa.contribute(menu1);
                qa.getHelpListener();
                IStatusLineManager statusLineManager = getEditor().getEditorSite().getActionBars().getStatusLineManager();
                menu1.addToMenu(statusLineManager, table.getMenu());
            }
        });
	}

	private void editorContextMenuAboutToShow(IMenuManager manager)
	{
		TableItem[] selection = table.getSelection();
		if (selection.length == 0) return;

		manager.add(moveDownAction);
		manager.add(moveUpAction);
		manager.add(removeAction);
	}

	class ComparedResult
	{
		PaneState state;
		MultiPaneEditor editor;
		IStructuredResult table;

		public ComparedResult(PaneState state, MultiPaneEditor editor, IStructuredResult table)
		{
			super();
			this.state = state;
			this.editor = editor;
			this.table = table;
		}
	}

	class CompareLabelProvider extends LabelProvider implements ITableLabelProvider
	{

		public Image getColumnImage(Object element, int columnIndex)
		{
			ComparedResult res = (ComparedResult) element;
			return (columnIndex == 0) ? res.state.getImage() : null;
		}

		public String getColumnText(Object element, int columnIndex)
		{
			ComparedResult res = (ComparedResult) element;
			switch (columnIndex)
			{
			case 0:
				return res.state.getIdentifier();
			case 1:
				return res.editor.getResourceFile().getAbsolutePath();
			default:
				return null;
			}
		}
	}

	class CompareContentProvider implements IStructuredContentProvider
	{

		public Object[] getElements(Object inputElement)
		{
			@SuppressWarnings("unchecked")
			List<ComparedResult> results = (List<ComparedResult>) inputElement;
			return results.toArray();
		}

		public void dispose()
		{}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
		{
		// TODO Auto-generated method stub
		}

	}

	class CompareAction extends Action
	{
		public CompareAction()
		{
			setText(Messages.CompareBasketView_CompareButtonLabel);
			setToolTipText(Messages.CompareBasketView_CompareTooltip);
			setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXECUTE_QUERY));
			setEnabled(false);
		}

		@Override
		public void run()
		{
		    try
		    {
                MultiPaneEditor editor = getEditor();

                final List<IStructuredResult> tables = new ArrayList<IStructuredResult>(results.size());
                final List<IQueryContext> contexts = new ArrayList<IQueryContext>(results.size());
                for (int i = 0; i < results.size(); i++)
                {
                    tables.add(results.get(i).table);
                    contexts.add(results.get(i).editor.getQueryContext());
                }

                String query = "comparetablesquery"; //$NON-NLS-1$
                SnapshotQuery compareQuery = SnapshotQuery.lookup(query,
                                (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null));
                compareQuery.setArgument("tables", tables); //$NON-NLS-1$
                compareQuery.setArgument("queryContexts", contexts); //$NON-NLS-1$
                IResult absolute = compareQuery.execute(new VoidProgressListener());
                QueryResult queryResult = new QueryResult(null, Messages.CompareBasketView_ComparedTablesResultTitle,
                                absolute);
                QueryExecution.displayResult(editor, null, null, queryResult, false);
            }
            catch (Exception e)
            {
                ErrorHelper.logThrowable(e);
            }
		}
	}

	class RemoveAllAction extends Action
	{
		public RemoveAllAction()
		{
			setText(Messages.CompareBasketView_ClearButtonLabel);
			setToolTipText(Messages.CompareBasketView_ClearTooltip);
			setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.REMOVE_ALL));
			setEnabled(false);
		}

		@Override
		public void run()
		{
			results.clear();
			tableViewer.refresh();
			setEnabled(false);
			compareAction.setEnabled(false);
		}
	}

	class RemoveAction extends Action implements ISelectionChangedListener
	{
		public RemoveAction()
		{
			setText(Messages.CompareBasketView_RemoveButtonLabel);
			setToolTipText(Messages.CompareBasketView_RemoveTooltip);
			setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.REMOVE));
			setEnabled(false);
			tableViewer.addSelectionChangedListener(this);
		}

		@Override
		public void run()
		{
			int[] selectionIndeces = tableViewer.getTable().getSelectionIndices();
			int alreadyRemoved = 0;
			for (int idx : selectionIndeces)
			{
				results.remove(idx - alreadyRemoved);
				alreadyRemoved++;
			}
			tableViewer.refresh();

			setEnabled(false);
			if (results.size() < 2) compareAction.setEnabled(false);
		}

		public void selectionChanged(SelectionChangedEvent event)
		{
			setEnabled(!tableViewer.getSelection().isEmpty());
		}

	}

	class MoveAction extends Action implements ISelectionChangedListener
	{
		private int direction;

		public MoveAction(int direction)
		{
			this.direction = direction;
			if (direction == SWT.UP)
			{
				setText(Messages.CompareBasketView_MoveUpButtonLabel);
				setToolTipText(Messages.CompareBasketView_MoveUpTooltip);
				setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.MOVE_UP));
			}
			else if (direction == SWT.DOWN)
			{
				setText(Messages.CompareBasketView_MoveDownButtonLabel);
				setToolTipText(Messages.CompareBasketView_MoveDownTooltip);
				setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.MOVE_DOWN));
			}
			setEnabled(false);
			tableViewer.addSelectionChangedListener(this);
		}

		@Override
		public void run()
		{
			int idx = tableViewer.getTable().getSelectionIndex();
			ComparedResult selectedResult = (ComparedResult) ((StructuredSelection) tableViewer.getSelection()).getFirstElement();
			if (direction == SWT.UP)
			{
				results.set(idx, results.get(idx - 1));
				results.set(idx - 1, selectedResult);

			}
			else if (direction == SWT.DOWN)
			{
				results.set(idx, results.get(idx + 1));
				results.set(idx + 1, selectedResult);
			}
			tableViewer.setInput(results);
			tableViewer.getTable().setSelection(direction == SWT.UP ? idx - 1 : idx + 1);
			moveUpAction.updateState();
			moveDownAction.updateState();
		}

		public void selectionChanged(SelectionChangedEvent event)
		{
			if (event.getSelection() instanceof StructuredSelection)
			{
				updateState();
			}
			else
			{
				setEnabled(false);
			}
		}

		void updateState()
		{
			StructuredSelection selection = (StructuredSelection) tableViewer.getSelection();
			if (selection.size() != 1)
			{
				setEnabled(false);
				return;
			}

			int idx = tableViewer.getTable().getSelectionIndex();
			if (idx == 0 && direction == SWT.UP) setEnabled(false);
			else if (idx == results.size() - 1 && direction == SWT.DOWN) setEnabled(false);
			else setEnabled(true);
		}

	}

	private static final class ComparePolicy implements IPolicy
    {
        private final List<IStructuredResult> tables;
        private final List<IQueryContext> contexts;
        private final IQueryContext currentContext;
        private final List<ISnapshot> snapshots;
        private final ISnapshot currentSnapshot;
    
        private ComparePolicy(List<IStructuredResult> tables, List<IQueryContext> contexts, IQueryContext currentContext)
        {
            this.tables = tables;
            this.contexts = contexts;
            this.currentContext = currentContext;
            snapshots = new ArrayList<ISnapshot>();
            for (IQueryContext ctx : contexts)
            {
                snapshots.add((ISnapshot)ctx.get(ISnapshot.class, null));
            }
            currentSnapshot = (ISnapshot)currentContext.get(ISnapshot.class, null);
        }
    
        /**
         * Only operate on queries with multiple tables and query contexts
         */
        public boolean accept(QueryDescriptor query)
        {
            boolean foundTables = false;
            boolean foundContexts = false;
            boolean foundSnapshots = false;
            for (ArgumentDescriptor argument : query.getArguments()) {
                if (IStructuredResult.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        for (IStructuredResult res : tables)
                        {
                            if (!argument.getType().isAssignableFrom(res.getClass()))
                            {
                                // Can't convert table/tree
                                return false;
                            }
                        }
                        foundTables = true;
                    }
                }
            }
            for (ArgumentDescriptor argument : query.getArguments()) {
                if (IQueryContext.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        foundContexts = true;
                    }
                }
                if (ISnapshot.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        foundSnapshots = true;
                    }
                }
            }
            if (!(foundContexts || foundSnapshots))
            {
                foundContexts = true;
                for (IQueryContext ctx : contexts)
                {
                    if (!ctx.equals(currentContext))
                    {
                        foundContexts = false;
                        break;
                    }
                }
            }
            if (!(foundContexts || foundSnapshots))
            {
                foundSnapshots = true;
                for (ISnapshot snap : snapshots)
                {
                    if (!snap.equals(currentSnapshot))
                    {
                        foundSnapshots = false;
                        break;
                    }
                }
            }
            return foundTables && (foundContexts || foundSnapshots);
        }
    
        public void fillInObjectArguments(ISnapshot snapshot, QueryDescriptor query, ArgumentSet set)
        {
            for (ArgumentDescriptor argument : query.getArguments()) {
                if (IStructuredResult.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        set.setArgumentValue(argument, tables);
                    }
                    else
                    {
                        set.setArgumentValue(argument, tables.get(0));
                    }
                }
                else if (IQueryContext.class.isAssignableFrom(argument.getType()))
                {
                    /*
                     * Only fill in multiple contexts from the tables.
                     * A single is the editor context.
                     */
                    if (argument.isMultiple())
                    {
                        set.setArgumentValue(argument, contexts);
                    }
                }
                else if (ISnapshot.class.isAssignableFrom(argument.getType()))
                {
                    /*
                     * Only fill in multiple snapshots from the tables.
                     * A single snapshot is the editor snapshot.
                     */
                    if (argument.isMultiple())
                    {
                        set.setArgumentValue(argument, snapshots);
                    }
                }
            }
        }
    }

    private void updateButtons()
	{
		compareAction.setEnabled(results.size() > 1);
		clearAction.setEnabled(results.size() > 0);
	}

	private MultiPaneEditor getEditor()
	{
		if (results.size() > 0)
		{
			return (MultiPaneEditor) results.get(results.size() - 1).editor.getSite().getPage().getActiveEditor();
		}
		return null;
	}

}
