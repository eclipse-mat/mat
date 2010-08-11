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
package org.eclipse.mat.ui.internal.viewer;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextDerivedData.DerivedColumn;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedStructuredResult.DerivedDataJobDefinition;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.actions.OpenHelpPageAction;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.mat.ui.util.SearchOnTyping;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.themes.ColorUtil;

public abstract class RefinedResultViewer
{
    protected static final int LIMIT = 25;
    protected static final int MAX_COLUMN_WIDTH = 500;
    protected static final int MIN_COLUMN_WIDTH = 90;

    /* package */interface Key
    {
        String CONTROL = "$control";//$NON-NLS-1$
    }

    /* package */static class ControlItem
    {
        public ControlItem(boolean expandAndSelect, int level)
        {
            this.expandAndSelect = expandAndSelect;
            this.level = level;
        }

        boolean expandAndSelect;
        int level;

        List<?> children;
        TotalsRow totals;
        boolean hasBeenPainted;

        @Override
        public String toString()
        {
            return level + " " + hashCode() + " " + totals;//$NON-NLS-1$//$NON-NLS-2$
        }

        public TotalsRow getTotals()
        {
            return totals;
        }

    }

    /* package */interface WidgetAdapter
    {
        Composite createControl(Composite parent);

        Item createColumn(Column column, int ii, SelectionListener selectionListener);

        ControlEditor createEditor();

        void setEditor(Composite composite, Item item, int columnIndex);

        Item[] getSelection();

        int indexOf(Item item);

        Item getItem(Item item, int index);

        Item getParentItem(Item item);

        Item getItem(Point pt);

        Rectangle getBounds(Item item, int columnIndex);

        Rectangle getImageBounds(Item item, int columnIndex);

        void apply(Item item, int index, String label, Color color, Font font);

        void apply(Item item, int index, String label);

        void apply(Item item, Font font);

        Font getFont();

        Item getSortColumn();

        int getSortDirection();

        void setSortColumn(Item column);

        void setSortDirection(int direction);

        void setItemCount(Item item, int count);

        int getItemCount(Item object);

        void setExpanded(Item parentItem, boolean b);

        Rectangle getTextBounds(Widget item, int index);

        int getLineHeightEstimation();
    }

    /** pane in which the viewer is embedded */
    protected MultiPaneEditor editor;

    /** the editor pane */
    protected AbstractEditorPane pane;

    /** adapter hiding specifics of table or tree */
    protected WidgetAdapter adapter;

    /** load SWT resources */
    protected LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

    /** the control (either tree or table) */
    protected Composite control;

    /** the columns (either tree or table) */
    protected Item[] columns;

    /** the editor (either tree or table) */
    protected ControlEditor controlEditor;

    /** number of items visible in the window */
    protected int visibleItemsEstimate;

    /** fonts & colors for filter & total row */
    protected Font boldFont;
    protected Color grayColor;
    protected Color greenColor;

    /** fonts used for decorated columns */
    protected Font[] fonts;
    /** colors used for decorated columns */
    protected Color[] colors;

    protected IQueryContext context;
    protected QueryResult queryResult;
    protected RefinedStructuredResult result;

    /** details a/b retained size calculation */
    protected List<DerivedDataJobDefinition> jobs;

    protected QueryContextMenu contextMenu;
    protected TotalsRow rootTotalsRow;
    protected boolean needsPacking = true;

    // //////////////////////////////////////////////////////////////
    // initialization
    // //////////////////////////////////////////////////////////////

    /* package */RefinedResultViewer(IQueryContext context, QueryResult result, RefinedStructuredResult refinedResult)
    {
        this.context = context;
        this.queryResult = result;
        this.result = refinedResult;
        this.jobs = new ArrayList<DerivedDataJobDefinition>(refinedResult.getJobs());
    }

    public abstract void init(Composite parent, MultiPaneEditor editor, AbstractEditorPane pane);

    protected void init(WidgetAdapter viewer, Composite parent, MultiPaneEditor editor, AbstractEditorPane pane)
    {
        this.adapter = viewer;
        this.editor = editor;
        this.pane = pane;

        parent.setRedraw(false);
        try
        {
            control = adapter.createControl(parent);
            control.addFocusListener(new FocusListener()
            {

                public void focusGained(FocusEvent e)
                {
                    RefinedResultViewer.this.editor.getEditorSite().getActionBars().setGlobalActionHandler(
                                    ActionFactory.COPY.getId(), new Action()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            Copy.copyToClipboard(control);
                                        }
                                    });
                    RefinedResultViewer.this.editor.getEditorSite().getActionBars().updateActionBars();
                }

                public void focusLost(FocusEvent e)
                {}
            });

            boldFont = resourceManager.createFont(FontDescriptor.createFrom(adapter.getFont()).setStyle(SWT.BOLD));
            grayColor = resourceManager.createColor( //
                            ColorUtil.blend(control.getBackground().getRGB(), control.getForeground().getRGB()));
            greenColor = control.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);

            createColumns();
            addPaintListener();
            addDoubleClickListener();
            createContextMenu();

            if (result.getSortColumn() >= 0)
            {
                adapter.setSortColumn(columns[result.getSortColumn()]);
                adapter.setSortDirection(result.getSortDirection().getSwtCode());
            }

            SearchOnTyping.attachTo(control, 0);

            // estimate the number of lines to render
            // (the control itself is not visible, possibly even the parent is
            // not visible)
            Rectangle bounds = parent.getParent().getBounds();
            if (bounds.height == 0 && bounds.width == 0)
                visibleItemsEstimate = 10;
            else
                visibleItemsEstimate = Math.max(((bounds.height - 10) / adapter.getLineHeightEstimation()) - 2, 1);

            refresh(true);

            addTextEditors();
        }
        finally
        {
            parent.setRedraw(true);
        }
    }

    private void createColumns()
    {
        Column[] queryColumns = result.getColumns();
        int nrOfColumns = queryColumns.length;

        columns = new Item[nrOfColumns];
        for (int ii = 0; ii < nrOfColumns; ++ii)
        {
            Column queryColumn = queryColumns[ii];
            columns[ii] = adapter.createColumn(queryColumn, ii, new ColumnSelectionListener());

            if (ii == 0)
            {
                columns[ii].addListener(SWT.Resize, new Listener()
                {
                    public void handleEvent(Event event)
                    {
                        control.redraw();
                    }
                });
            }
        }
    }

    private void addDoubleClickListener()
    {
        control.addListener(SWT.DefaultSelection, new Listener()
        {
            public void handleEvent(Event event)
            {
                Item widget = (Item) event.item;
                Object data = widget.getData();
                if (data != null)
                    return;

                ControlItem ctrl = null;

                Item parent = adapter.getParentItem(widget);
                if (parent == null)
                {
                    if (adapter.indexOf(widget) != 0)
                        ctrl = (ControlItem) control.getData(Key.CONTROL);
                }
                else
                {
                    ctrl = (ControlItem) parent.getData(Key.CONTROL);
                }

                if (ctrl == null || ctrl.totals == null)
                    return;

                if (ctrl.totals.getVisibleItems() >= ctrl.totals.getNumberOfItems())
                    return;

                doRevealChildren(parent, LIMIT);

                event.doit = false;
            }
        });
    }

    protected abstract List<?> getElements(Object parent);
    
    protected abstract void configureColumns();
    
    protected void applyTextAndImage(Item item, Object element)
    {
        item.setData(element);
        adapter.apply(item, adapter.getFont());

        URL image = result.getIcon(element);
        if (image != null)
            item.setImage(MemoryAnalyserPlugin.getDefault().getImage(image));

        for (int ii = 0; ii < columns.length; ii++)
        {
            if (!result.isDecorated(ii))
            {
                adapter.apply(item, ii, result.getFormattedColumnValue(element, ii));
            }
            else
            {
                String[] texts = new String[3];
                texts[0] = result.getColumns()[ii].getDecorator().prefix(element);
                texts[1] = result.getFormattedColumnValue(element, ii);
                texts[2] = result.getColumns()[ii].getDecorator().suffix(element);
                item.setData(String.valueOf(ii), texts);
                adapter.apply(item, ii, asString(texts));
            }
        }
    }

    protected void applyTotals(Item item, TotalsRow totalsRow)
    {
        item.setImage(MemoryAnalyserPlugin.getDefault().getImage(totalsRow.getIcon()));

        for (int ii = 0; ii < columns.length; ii++)
            adapter.apply(item, ii, totalsRow.getLabel(ii));

        adapter.apply(item, boldFont);

        item.setData(null);
        item.setData(Key.CONTROL, null);
    }

    protected void applyUpdating(Item item)
    {
        item.setText(Messages.RefinedResultViewer_updating);
        item.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.REFRESHING));
        item.setData(null);
        item.setData(Key.CONTROL, null);
    }

    protected void applyFilterData(Item item)
    {
        Filter[] filter = result.getFilter();
        for (int ii = 0; ii < filter.length; ii++)
            applyFilterData(item, ii, filter[ii]);
    }

    protected void applyFilterData(Item item, int columnIndex, Filter filter)
    {
        if (columnIndex == 0)
            item.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.FILTER));

        String label = filter.isActive() ? filter.getCriteria() : filter.getLabel();
        Color color = filter.isActive() ? greenColor : grayColor;
        Font font = filter.isActive() ? boldFont : adapter.getFont();
        adapter.apply(item, columnIndex, label, color, font);
    }

    protected String asString(String[] texts)
    {
        StringBuilder buf = new StringBuilder();
        for (int ii = 0; ii < texts.length; ii++)
        {
            if (texts[ii] != null)
            {
                if (buf.length() > 0)
                    buf.append(" ");//$NON-NLS-1$
                buf.append(texts[ii]);
            }
        }
        return buf.toString();
    }

    private void addPaintListener()
    {
        boolean isDecorated = false;
        for (int ii = 0; !isDecorated && ii < result.getColumns().length; ii++)
            isDecorated = result.isDecorated(ii);

        if (!isDecorated)
            return;

        // fonts
        fonts = new Font[3];
        fonts[0] = boldFont; // prefix
        fonts[1] = adapter.getFont(); // normal
        fonts[2] = boldFont; // suffix

        // colors
        colors = new Color[3];
        colors[0] = null;
        colors[1] = null;
        colors[2] = grayColor;

        control.addListener(SWT.MeasureItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts == null)
                    return;

                if (texts.length > 0)
                {
                    int width = 0;
                    int height = 0;

                    Image image = ((Item) event.item).getImage();
                    if (image != null)
                        width += image.getBounds().width + 4;

                    for (int ii = 0; ii < texts.length; ii++)
                    {
                        if (texts[ii] != null)
                        {
                            event.gc.setFont(fonts[ii]);
                            Point size = event.gc.textExtent(texts[ii]);
                            width += size.x + 4;
                            height = Math.max(event.height, size.y + 2);
                        }
                    }

                    event.width = width;
                    event.height = height;
                }
                else
                {
                    event.height = Math.max(event.height, 16 + 1);
                    event.width = MIN_COLUMN_WIDTH;
                }

                event.doit = false;
            }
        });

        control.addListener(SWT.EraseItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts != null)
                {
                    event.detail &= ~SWT.FOREGROUND;
                }
            }
        });

        control.addListener(SWT.PaintItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts != null)
                {
                    boolean isSelected = (event.detail & SWT.SELECTED) != 0;

                    if (isSelected)
                    {
                        Rectangle r = event.gc.getClipping();
                        event.gc.fillRectangle(event.x, event.y, r.width, event.height - 1);
                    }

                    int x = event.x;
                    Image image = ((Item) event.item).getImage();
                    if (image != null)
                    {
                        event.gc.drawImage(image, event.x + 1, event.y);
                        x += image.getBounds().width + 4;
                    }

                    Color fg = event.gc.getForeground();

                    for (int ii = 0; ii < texts.length; ii++)
                    {
                        if (texts[ii] != null)
                        {
                            event.gc.setFont(fonts[ii]);
                            if (!isSelected)
                                event.gc.setForeground(colors[ii] != null ? colors[ii] : fg);

                            Point size = event.gc.textExtent(texts[ii]);
                            event.gc.drawText(texts[ii], x + 1, event.y + Math.max(0, (event.height - size.y) / 2),
                                            true);
                            x += size.x + 4;
                        }
                    }

                    event.gc.setForeground(fg);

                    event.doit = false;
                }

            }

        });

    }

    private void createContextMenu()
    {
        contextMenu = new QueryContextMenu(editor, queryResult)
        {
            @Override
            protected void customMenu(PopupMenu menu, List<IContextObject> menuContext, final ContextProvider provider,
                            String label)
            {
                menu.addSeparator();

                for (DerivedColumn derivedColumn : context.getContextDerivedData().getDerivedColumns())
                {
                    for (final DerivedOperation derivedOperation : derivedColumn.getOperations())
                    {
                        Action action = new Action(derivedOperation.getLabel())
                        {
                            @Override
                            public void run()
                            {
                                doCalculateDerivedValuesForSelection(provider, derivedOperation);
                            }
                        };

                        action.setImageDescriptor(MemoryAnalyserPlugin
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                        menu.add(action);
                    }
                }
            }
        };
    }

    private void addTextEditors()
    {
        controlEditor = adapter.createEditor();

        control.addListener(SWT.MouseDown, new Listener()
        {
            public void handleEvent(Event event)
            {
                Point pt = new Point(event.x, event.y);

                Item item = adapter.getItem(pt);
                if (item != adapter.getItem(null, 0))
                    return;

                int columnIndex = getColumnIndex(item, pt);
                if (columnIndex < 0)
                    return;

                Filter filter = result.getFilter()[columnIndex];

                activateEditor(item, filter, columnIndex);
            }

            private int getColumnIndex(Item item, Point pt)
            {
                for (int ii = 0; ii < columns.length; ii++)
                {
                    Rectangle bounds = adapter.getBounds(item, ii);
                    if (bounds.contains(pt))
                        return ii;
                }
                return -1;
            }
        });
    }

    // //////////////////////////////////////////////////////////////
    // tool bar / context menu handling
    // //////////////////////////////////////////////////////////////

    public void contributeToToolBar(IToolBarManager manager)
    {
        Action calculateRetainedSizeMenu = new EasyToolBarDropDown(Messages.RefinedResultViewer_CalculateRetainedSize, //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR), //
                        editor)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                List<ContextProvider> providers = new ArrayList<ContextProvider>();

                List<ContextProvider> p = result.getResultMetaData().getContextProviders();
                if (p != null && !p.isEmpty())
                    providers.addAll(p);
                else
                    providers.add(queryResult.getDefaultContextProvider());

                if (!providers.isEmpty())
                {
                    for (ContextProvider cp : providers)
                    {
                        PopupMenu toThisMenu = menu;
                        if (providers.size() > 1)
                        {
                            PopupMenu subMenu = new PopupMenu(cp.getLabel());
                            menu.add(subMenu);
                            toThisMenu = subMenu;
                        }

                        addRetainedSizeActions(toThisMenu, cp);
                    }
                }
            }

            private void addRetainedSizeActions(PopupMenu toThisMenu, final ContextProvider cp)
            {
                ContextDerivedData derivedData = context.getContextDerivedData();
                if (derivedData == null)
                    return;

                for (DerivedColumn derivedColumn : derivedData.getDerivedColumns())
                {
                    for (final DerivedOperation derivedOperation : derivedColumn.getOperations())
                    {
                        Action action = new Action(derivedOperation.getLabel())
                        {
                            @Override
                            public void run()
                            {
                                doCalculateDerivedValuesForAll(cp, derivedOperation);
                            }
                        };

                        action.setImageDescriptor(MemoryAnalyserPlugin
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                        toThisMenu.add(action);
                    }
                }
            }

        };

        Action exportMenu = new EasyToolBarDropDown(Messages.RefinedResultViewer_Export, MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_MENU), //
                        editor)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                menu.add(new ExportActions.HtmlExport(control, result, context));
                menu.add(new ExportActions.CsvExport(control, result, context));
                menu.add(new ExportActions.TxtExport(control));
            }
        };

        manager.add(calculateRetainedSizeMenu);
        manager.add(exportMenu);

        if (queryResult.getQuery() != null && queryResult.getQuery().getHelpUrl() != null)
            manager.appendToGroup("help", new OpenHelpPageAction(queryResult.getQuery().getHelpUrl()));//$NON-NLS-1$

    }

    public void addContextMenu(PopupMenu menu)
    {
        contextMenu.addContextActions(menu, getSelection(), getControl());
        addColumnsMenu(menu);
        addMoreMenu(menu);
    }
    
    private void addColumnsMenu(PopupMenu menu)
    {
    	menu.addSeparator();
    	PopupMenu columnsMenu = new PopupMenu(Messages.RefinedResultViewer_Columns);
        menu.add(columnsMenu);
    	
        addFilterMenu(columnsMenu);
        addSortByMenu(columnsMenu);
        /* temporarily removed because of dependency on 3.5, see comments 3-5 in bug 307031 */
//        addConfigureColumnsMenu(columnsMenu); 
        
    }

    private void addFilterMenu(PopupMenu menu)
    {

        PopupMenu filterMenu = new PopupMenu(Messages.RefinedResultViewer_EditFilter);
        menu.add(filterMenu);

        for (int ii = 0; ii < columns.length; ii++)
        {
            final int columnIndex = ii;

            Action action = new Action(columns[ii].getText())
            {
                @Override
                public void run()
                {
                    Item item = adapter.getItem(null, 0);
                    Filter filter = result.getFilter()[columnIndex];
                    activateEditor(item, filter, columnIndex);
                }
            };
            filterMenu.add(action);
        }
    }
    
    /* temporarily removed because of dependency on 3.5, see comments 3-5 in bug 307031 */
    private void addConfigureColumnsMenu(PopupMenu menu)
    {
        Action columnsAction = new Action(Messages.RefinedResultViewer_ConfigureColumns) 
        {
        	@Override
        	public void run()
        	{
        		configureColumns();
        	}
        };

        menu.add(columnsAction);
    }
    
    private void addSortByMenu(PopupMenu menu)
    {
        PopupMenu sortByMenu = new PopupMenu(Messages.RefinedResultViewer_Sort_By);
        menu.add(sortByMenu);
        
        for (int ii = 0; ii < columns.length; ii++)
        {
            final int columnIndex = ii;

            Action action = new Action(columns[ii].getText())
            {
                @Override
                public void run()
                {
                	resort(columns[columnIndex]);
                }
            };
            sortByMenu.add(action);
        }
    }

    private void addMoreMenu(PopupMenu menu)
    {
        Item[] selection = adapter.getSelection();
        if (selection.length != 1)
            return;

        if (selection[0].getData() != null)
            return;

        ControlItem ctrl = null;

        final Item parent = adapter.getParentItem(selection[0]);
        if (parent == null)
        {
            if (adapter.indexOf(selection[0]) != 0)
                ctrl = (ControlItem) control.getData(Key.CONTROL);
        }
        else
        {
            ctrl = (ControlItem) parent.getData(Key.CONTROL);
        }

        if (ctrl != null && ctrl.totals != null //
                        && ctrl.totals.getVisibleItems() < ctrl.totals.getNumberOfItems())
        {
            menu.addSeparator();

            boolean isRest = ctrl.totals.getNumberOfItems() - ctrl.totals.getVisibleItems() <= LIMIT;

            if (!isRest)
            {
                Action action = new Action(Messages.RefinedResultViewer_Next25)
                {
                    @Override
                    public void run()
                    {
                        doRevealChildren(parent, LIMIT);
                    }

                };
                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
                menu.add(action);
            }

            Action action = new Action(Messages.RefinedResultViewer_CustomExpand)
            {
                @Override
                public void run()
                {
                    IInputValidator inputValidator = new IInputValidator()
                    {

                        public String isValid(String newText)
                        {
                            if (newText == null || newText.length() == 0)
                                return " "; //$NON-NLS-1$
                            try
                            {
                                if (Integer.parseInt(newText) > 0)
                                    return null;
                            }
                            catch (NumberFormatException e)
                            {}
                            return Messages.RefinedResultViewer_notValidNumber;

                        }

                    };

                    InputDialog inputDialog = new InputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                                    Messages.RefinedResultViewer_ExpandToLimit, //
                                    Messages.RefinedResultViewer_EnterNumber, null, inputValidator);

                    if (inputDialog.open() == 1) // if canceled
                        return;
                    int number = new Integer(inputDialog.getValue()).intValue();
                    doRevealChildren(parent, number);
                }

            };
            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
            menu.add(action);

            action = new Action(Messages.RefinedResultViewer_ExpandAll)
            {
                @Override
                public void run()
                {
                    doRevealChildren(parent, Integer.MAX_VALUE);
                }

            };
            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
            menu.add(action);
        }
    }

    private final void doRevealChildren(Item parent, int number)
    {
        if (parent != null && parent.isDisposed())
            return;

        ControlItem ctrl = (ControlItem) (parent == null ? control.getData(Key.CONTROL) : parent.getData(Key.CONTROL));
        if (ctrl == null || ctrl.totals == null)
            return;
        int visible = number == Integer.MAX_VALUE ? ctrl.totals.getNumberOfItems() : //
                        Math.min(ctrl.totals.getVisibleItems() + number, ctrl.totals.getNumberOfItems());

        if (visible - ctrl.totals.getVisibleItems() > 5000)
        {
            MessageBox box = new MessageBox(control.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
            box.setMessage(MessageUtil.format(Messages.RefinedResultViewer_BlockingWarning, //
                            (visible - ctrl.totals.getVisibleItems())));
            if (box.open() != SWT.OK)
                return;
        }

        ctrl.totals.setVisibleItems(visible);
        control.getParent().setRedraw(false);

        try
        {
            widgetRevealChildren(parent, ctrl.totals);
        }
        finally
        {
            control.getParent().setRedraw(true);
        }
    }

    protected abstract void widgetRevealChildren(Item parent, TotalsRow totalsData);

    private void activateEditor(final Item item, final Filter filter, final int columnIndex)
    {
        boolean showBorder = false;
        final Composite composite = new Composite(control, SWT.NONE);
        final Text text = new Text(composite, SWT.NONE);
        final int inset = showBorder ? 1 : 0;

        // Once the item goes e.g. on sort, remove the filter to avoid operating
        // on the disposed item.
        final DisposeListener disposeListener = new DisposeListener()
        {
            public void widgetDisposed(DisposeEvent e)
            {
                composite.dispose();
            }
        };
        item.addDisposeListener(disposeListener);

        composite.addListener(SWT.Resize, new Listener()
        {
            public void handleEvent(Event e)
            {
                Rectangle rect = composite.getClientArea();
                text.setBounds(rect.x + inset, rect.y + inset, rect.width - inset * 2, rect.height - inset * 2);
            }
        });

        Listener textListener = new Listener()
        {
            public void handleEvent(final Event e)
            {
                switch (e.type)
                {
                    case SWT.FocusOut:
                        updateCriteria(filter, columnIndex, text.getText());
                        item.removeDisposeListener(disposeListener);
                        composite.dispose();
                        break;

                    case SWT.Verify:
                        Rectangle cell = adapter.getBounds(item, columnIndex);
                        Rectangle image = adapter.getImageBounds(item, columnIndex);

                        controlEditor.minimumHeight = cell.height;
                        controlEditor.minimumWidth = cell.width - image.width;
                        controlEditor.layout();
                        break;

                    case SWT.Traverse:
                        switch (e.detail)
                        {
                            case SWT.TRAVERSE_RETURN:
                                // $JL-SWITCH$ fall through
                                updateCriteria(filter, columnIndex, text.getText());
                            case SWT.TRAVERSE_ESCAPE:
                                item.removeDisposeListener(disposeListener);
                                composite.dispose();
                                e.doit = false;
                        }
                        break;
                }
            }

            private void updateCriteria(final Filter filter, final int columnIndex, String text)
            {
                boolean changed = false;
                try
                {
                    changed = filter.setCriteria(text);

                }
                catch (IllegalArgumentException e)
                {
                    ErrorHelper.showErrorMessage(e);
                }

                if (changed)
                {
                    applyFilterData(item, columnIndex, filter);
                    refresh(false);
                }
            }
        };

        text.addListener(SWT.FocusOut, textListener);
        text.addListener(SWT.Traverse, textListener);
        text.addListener(SWT.Verify, textListener);

        adapter.setEditor(composite, item, columnIndex);

        text.setText(filter.getCriteria() != null ? filter.getCriteria() : "");//$NON-NLS-1$
        text.selectAll();
        text.setFocus();

    }

    // //////////////////////////////////////////////////////////////
    // retained size calculation for all/selection
    // //////////////////////////////////////////////////////////////

    public void showDerivedDataColumn(ContextProvider provider, DerivedOperation operation)
    {
        prepareColumns(provider, operation);
    }

    protected void prepareColumns(ContextProvider provider, DerivedOperation operation)
    {
        DerivedColumn derivedColumn = context.getContextDerivedData().lookup(operation);

        Column queryColumn = result.getColumnFor(provider, derivedColumn);
        if (queryColumn == null)
        {
            queryColumn = result.addDerivedDataColumn(provider, derivedColumn);

            Item column = adapter.createColumn(queryColumn, this.columns.length, new ColumnSelectionListener());

            Item[] copy = new Item[columns.length + 1];
            System.arraycopy(columns, 0, copy, 0, columns.length);
            copy[columns.length] = column;
            columns = copy;

            applyFilterData(adapter.getItem(null, 0));
        }
    }

    protected void doCalculateDerivedValuesForAll(ContextProvider provider, DerivedOperation operation)
    {
        prepareColumns(provider, operation);

        boolean jobFound = false;
        for (DerivedDataJobDefinition job : jobs)
        {
            if (job.getContextProvider().hasSameTarget(provider))
            {
                jobFound = true;
                job.setOperation(operation);
                break;
            }
        }

        if (!jobFound)
            jobs.add(new DerivedDataJobDefinition(provider, operation));

        ControlItem ctrl = (ControlItem) control.getData(Key.CONTROL);
        new DerivedDataJob.OnFullList(this, provider, operation, ctrl.children, null, ctrl).schedule();
    }

    protected void doCalculateDerivedValuesForSelection(ContextProvider provider, DerivedOperation operation)
    {
        prepareColumns(provider, operation);

        Item[] items = adapter.getSelection();
        if (items.length == 0)
            return;

        List<Item> widgetItems = new ArrayList<Item>();
        List<Object> subjectItems = new ArrayList<Object>();

        for (Item tItem : items)
        {
            Object subject = tItem.getData();
            if (subject != null)
            {
                widgetItems.add(tItem);
                subjectItems.add(subject);
            }
        }

        if (widgetItems.size() > 0)
        {
            new DerivedDataJob.OnSelection(this, provider, operation, subjectItems, widgetItems).schedule();
        }
    }

    // //////////////////////////////////////////////////////////////
    // manipulation
    // //////////////////////////////////////////////////////////////

    public RefinedStructuredResult getResult()
    {
        return result;
    }

    public QueryResult getQueryResult()
    {
        return queryResult;
    }

    public final Control getControl()
    {
        return control;
    }

    public final IStructuredSelection getSelection()
    {
        Item[] items = adapter.getSelection();
        if (items.length == 0)
            return StructuredSelection.EMPTY;

        List<Object> selection = new ArrayList<Object>(items.length);
        for (int ii = 0; ii < items.length; ii++)
        {
            Object row = items[ii].getData();
            if (row != null)
                selection.add(row);
        }

        return new StructuredSelection(selection);
    }

    public final void setFocus()
    {
        control.setFocus();
    }

    protected final void resort()
    {
        List<?> children = ((ControlItem) control.getData(Key.CONTROL)).children;
        if (children != null)
        {
            new SortingJob(this, children).schedule();
        }
    }
    
    protected final void resort(Item column)
    {
        Column queryColumn = (Column) column.getData();

        boolean isSorted = column == adapter.getSortColumn();

        int direction = SWT.UP;

        if (isSorted)
            direction = adapter.getSortDirection() == SWT.UP ? SWT.DOWN : SWT.UP;
        else
            direction = queryColumn.isNumeric() ? SWT.DOWN : SWT.UP;

        control.getParent().setRedraw(false);

        try
        {
            adapter.setSortColumn(column);
            adapter.setSortDirection(direction);

            result.setSortOrder(queryColumn, Column.SortDirection.of(direction));

            resort();
        }
        finally
        {
            control.getParent().setRedraw(true);
        }
    }

    protected abstract void refresh(boolean expandAndSelect);

    protected abstract void doUpdateChildren(Item parentItem, ControlItem ctrl);

    public void dispose()
    {
        control.dispose();
        resourceManager.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private final class ColumnSelectionListener implements SelectionListener
    {
        public void widgetDefaultSelected(SelectionEvent e)
        {}

        public void widgetSelected(SelectionEvent e)
        {
            Item treeColumn = (Item) e.widget;
            resort(treeColumn);
        }
    }

    // //////////////////////////////////////////////////////////////
    // jobs
    // //////////////////////////////////////////////////////////////

    protected static class RetrieveChildrenJob extends AbstractPaneJob implements ISchedulingRule
    {
        private RefinedResultViewer viewer;
        private ControlItem ctrl;
        private Item parentItem;
        private Object parent;

        protected RetrieveChildrenJob(RefinedResultViewer viewer, ControlItem ctrl, Item parentItem, Object parent)
        {
            super(Messages.RefinedResultViewer_RetrieveViewElements, viewer.pane);

            this.viewer = viewer;
            this.ctrl = ctrl;
            this.parentItem = parentItem;
            this.parent = parent;

            setUser(true);
            setRule(this);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            try
            {
                loadElements();
                updateDisplay();
                calculateTotals(monitor);

                for (RefinedStructuredResult.DerivedDataJobDefinition job : viewer.jobs)
                {
                    new DerivedDataJob.OnFullList(viewer, job.getContextProvider(), job.getOperation(), ctrl.children,
                                    parentItem, ctrl).schedule();
                }

                return Status.OK_STATUS;
            }
            catch (RuntimeException e)
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (viewer.control.isDisposed())
                            return;

                        viewer.control.getParent().setRedraw(false);
                        try
                        {
                            if (parentItem != null)
                            {
                                parentItem.setData(Key.CONTROL, null);
                                viewer.adapter.setItemCount(parentItem, 1);
                                viewer.adapter.setExpanded(parentItem, false);
                            }
                            else
                            {
                                viewer.refresh(false);
                            }
                        }
                        finally
                        {
                            viewer.control.getParent().setRedraw(true);
                        }
                    }
                });

                if (e instanceof IProgressListener.OperationCanceledException)
                    return Status.CANCEL_STATUS;
                else
                    return ErrorHelper.createErrorStatus(e);
            }
        }

        private void calculateTotals(IProgressMonitor monitor)
        {
            if (monitor.isCanceled())
                return;

            boolean hasChildren = ctrl.totals.getNumberOfItems() > 0 || ctrl.totals.getFilteredItems() > 0;
            if (hasChildren && ctrl.children.size() > 1)
            {
                viewer.result.calculateTotals(ctrl.children, ctrl.totals, new ProgressMonitorWrapper(monitor));

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (viewer.control.isDisposed())
                            return;

                        viewer.control.getParent().setRedraw(false);
                        try
                        {
                            if (parentItem == null) // root elements
                            {
                                int index = viewer.adapter.getItemCount(null) - 1;
                                Item item = viewer.adapter.getItem(null, index);
                                updateItem(item, (ControlItem) viewer.control.getData(Key.CONTROL));
                            }
                            else
                            {
                                if (parentItem.isDisposed())
                                    return;

                                int index = viewer.adapter.getItemCount(parentItem) - 1;
                                Item item = viewer.adapter.getItem(parentItem, index);
                                updateItem(item, (ControlItem) parentItem.getData(Key.CONTROL));
                            }
                        }
                        finally
                        {
                            viewer.control.getParent().setRedraw(true);
                        }
                    }

                    private void updateItem(Item item, ControlItem ctrl)
                    {
                        if (item.isDisposed())
                            return;

                        viewer.applyTotals(item, ctrl.totals);
                    }
                });
            }
        }

        private void updateDisplay()
        {
            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
            {
                public void run()
                {
                    if (viewer.control.isDisposed())
                        return;

                    viewer.control.getParent().setRedraw(false);
                    try
                    {
                        viewer.doUpdateChildren(parentItem, ctrl);
                    }
                    finally
                    {
                        viewer.control.getParent().setRedraw(true);
                    }
                }
            });
        }

        private void loadElements()
        {
            if (ctrl == null)
                ctrl = new ControlItem(false, 0);

            ctrl.children = viewer.getElements(parent);
            ctrl.totals = viewer.result.buildTotalsRow(ctrl.children);

            if (parent != null)
            {
                ctrl.totals.setVisibleItems(Math.min(LIMIT, ctrl.totals.getNumberOfItems()));
            }
            else
            {
                if (viewer.rootTotalsRow != null
                                && viewer.rootTotalsRow.getVisibleItems() > ctrl.totals.getVisibleItems())
                {
                    ctrl.totals.setVisibleItems(Math.min(ctrl.totals.getNumberOfItems(), //
                                    Math.max(viewer.rootTotalsRow.getVisibleItems(), viewer.visibleItemsEstimate)));
                }
                else
                {
                    ctrl.totals.setVisibleItems(Math.min(viewer.visibleItemsEstimate, ctrl.totals.getNumberOfItems()));
                }
            }

            if (parent == null)
                viewer.rootTotalsRow = ctrl.totals;
        }

        public boolean contains(ISchedulingRule rule)
        {
            return rule.getClass() == getClass();
        }

        public boolean isConflicting(ISchedulingRule rule)
        {
            return rule.getClass() == getClass();
        }
    }

    private static class SortingJob extends AbstractPaneJob
    {
        RefinedResultViewer viewer;
        List<?> list;

        private SortingJob(RefinedResultViewer viewer, List<?> list)
        {
            super(Messages.RefinedResultViewer_Sorting, viewer.pane);
            this.viewer = viewer;
            this.list = list;

            setUser(true);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            viewer.result.sort(list);

            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    if (viewer.control.isDisposed())
                        return;

                    try
                    {
                        viewer.control.getParent().setRedraw(false);
                        viewer.doUpdateChildren(null, (ControlItem) viewer.control.getData(Key.CONTROL));
                    }
                    finally
                    {
                        viewer.control.getParent().setRedraw(true);
                    }
                }
            });

            return Status.OK_STATUS;
        }

    }

}
