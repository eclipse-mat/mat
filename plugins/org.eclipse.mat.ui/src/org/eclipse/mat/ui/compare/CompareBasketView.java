/*******************************************************************************
 * Copyright (c) 2010 SAP AG. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.mat.ui.compare;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.compare.CompareTablesQuery.Mode;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.panes.TableResultPane;
import org.eclipse.mat.ui.snapshot.panes.HistogramPane;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.part.ViewPart;

public class CompareBasketView extends ViewPart
{

	public static final String ID = "org.eclipse.mat.ui.views.CompareBasketView";

	private Table table;
	private TableViewer tableViewer;
	private Action compareAction;
	private Action clearAction;
	private MoveAction moveUpAction;
	private MoveAction moveDownAction;
	List<ComparedResult> results = new ArrayList<ComparedResult>();

	@Override
	public void createPartControl(Composite parent)
	{
		createTable(parent);

		addToolbar();
	}

	private void createTable(Composite parent)
	{
		tableViewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		this.table = tableViewer.getTable();

		TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.LEFT);
		TableColumn tableColumn = column.getColumn();
		tableColumn.setText("Results to be compared");
		tableColumn.setWidth(400);

		table.setHeaderVisible(true);
		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseDoubleClick(MouseEvent event)
			{
				TableItem[] selection = table.getSelection();
				System.out.println("Clicked " + selection);
			}

		});

		tableViewer.setContentProvider(new CompareContentProvider());
		tableViewer.setLabelProvider(new CompareLabelProvider());
		tableViewer.setInput(results);
	}

	private void addToolbar()
	{
		IToolBarManager manager = getViewSite().getActionBars().getToolBarManager();
		moveUpAction = new MoveAction(SWT.UP);
		manager.add(moveUpAction);

		moveDownAction = new MoveAction(SWT.DOWN);
		manager.add(moveDownAction);

		compareAction = new CompareAction();
		manager.add(compareAction);

		clearAction = new ClearAction();
		manager.add(clearAction);
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

		results.add(entry);
		tableViewer.refresh();

		clearAction.setEnabled(true);
		if (results.size() > 1) compareAction.setEnabled(true);
	}

	public static boolean accepts(PaneState state, MultiPaneEditor editor)
	{
		AbstractEditorPane pane = editor.getEditor(state);
		return (pane instanceof HistogramPane) || (pane instanceof TableResultPane);
	}

	class ComparedResult
	{
		PaneState state;
		MultiPaneEditor editor;
		IResultTable table;

		public ComparedResult(PaneState state, MultiPaneEditor editor, IResultTable table)
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
			return res.state.getImage();
		}

		public String getColumnText(Object element, int columnIndex)
		{
			ComparedResult res = (ComparedResult) element;
			return res.state.getIdentifier();
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
			setText("Compare");
			setToolTipText("Compare the results");
			setEnabled(false);
		}

		@Override
		public void run()
		{
			MultiPaneEditor editor = getEditor();
			CompareTablesQuery compareQuery = new CompareTablesQuery();
			IResultTable[] tables = new IResultTable[results.size()];
			for (int i = 0; i < tables.length; i++)
			{
				tables[i] = results.get(i).table;
			}
			compareQuery.tables = tables;

			try
			{
				compareQuery.mode = Mode.ABSOLUTE;
				IResult absolute = compareQuery.execute(new VoidProgressListener());
				QueryResult queryResult = new QueryResult(null, "Compared Tables", absolute);
				QueryExecution.displayResult(editor, null, null, queryResult, false);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	class ClearAction extends Action
	{
		public ClearAction()
		{
			setText("Clear");
			setToolTipText("Clear table");
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

	class MoveAction extends Action implements ISelectionChangedListener
	{
		private int direction;

		public MoveAction(int direction)
		{
			this.direction = direction;
			if (direction == SWT.UP)
			{
				setText("Move Up");
				setToolTipText("Move Result Up");
			}
			else if (direction == SWT.DOWN)
			{
				setText("Move Down");
				setToolTipText("Move Result Down");
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

	private MultiPaneEditor getEditor()
	{
		if (results.size() > 0)
		{
			return results.get(results.size() - 1).editor;
		}
		return null;
	}

}
