/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.accessibility.AccessibleCompositeAdapter;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.IPolicy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class QueryBrowserPopup extends PopupDialog
{
    public interface Element
    {
        String getLabel();

        String getUsage();

        QueryDescriptor getQuery();

        ImageDescriptor getImageDescriptor();

        void execute(MultiPaneEditor editor) throws SnapshotException;
    }

    private static final int INITIAL_COUNT_PER_PROVIDER = 4;

    private LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

    private List<QueryBrowserProvider> providers;
    private MultiPaneEditor editor;
    private IPolicy policy;

    private Text filterText;
    private QueryContextHelp helpText;
    private Table table;
    private TextLayout textLayout;

    private boolean resized = false;

    public QueryBrowserPopup(MultiPaneEditor editor)
    {
        this(editor, false);
    }

    public QueryBrowserPopup(MultiPaneEditor editor, boolean onlyHistory)
    {
        this(editor, onlyHistory, new Policy());
    }
    
    public QueryBrowserPopup(MultiPaneEditor editor, boolean onlyHistory, org.eclipse.mat.ui.util.IPolicy policy)
    {
        super(editor.getEditorSite().getShell(), SWT.RESIZE, true, true, true, true, null,
                        Messages.QueryBrowserPopup_StartTyping);

        this.editor = editor;
        this.policy = policy;

        QueryBrowserPopup.this.providers = new ArrayList<QueryBrowserProvider>();
        providers.add(new QueryHistoryProvider(editor.getQueryContext(), policy));

        if (!onlyHistory)
        {
            addCategories(QueryRegistry.instance().getRootCategory(), policy);
            providers.add(new QueryRegistryProvider(editor.getQueryContext(), QueryRegistry.instance().getRootCategory(), policy));
        }

        create();
    }

    private void addCategories(CategoryDescriptor category, IPolicy policy)
    {
        for (CategoryDescriptor c : category.getSubCategories())
        {
            providers.add(new QueryRegistryProvider(editor.getQueryContext(), c, policy));
            addCategories(c, policy);
        }
    }

    protected Control createTitleControl(Composite parent)
    {
        filterText = new Text(parent, SWT.NONE);

        GC gc = new GC(parent);
        gc.setFont(parent.getFont());
        FontMetrics fontMetrics = gc.getFontMetrics();
        gc.dispose();

        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).hint(SWT.DEFAULT,
                        Dialog.convertHeightInCharsToPixels(fontMetrics, 1)).applyTo(filterText);

        filterText.addKeyListener(new KeyListener()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == 0x0D)
                {
                    if (e.stateMask != 0)
                        handleSelection(true);
                    else if (filterText.getText().length() == 0)
                        handleSelection(false);
                    else
                        executeFilterText();

                    return;
                }
                else if (e.keyCode == SWT.ARROW_DOWN)
                {
                    int index = table.getSelectionIndex();
                    if (index != -1 && table.getItemCount() > index + 1)
                    {
                        table.setSelection(index + 1);
                        updateHelp();
                    }
                    table.setFocus();
                }
                else if (e.keyCode == SWT.ARROW_UP)
                {
                    int index = table.getSelectionIndex();
                    if (index != -1 && index >= 1)
                    {
                        table.setSelection(index - 1);
                        updateHelp();
                        table.setFocus();
                    }
                }
                else if (e.character == 0x1B) // ESC
                    close();
            }

            public void keyReleased(KeyEvent e)
            {}
        });

        filterText.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent e)
            {
                String text = ((Text) e.widget).getText().toLowerCase();
                refresh(text);
            }
        });

        return filterText;
    }

    protected Control createDialogArea(Composite parent)
    {
        Composite composite = (Composite) super.createDialogArea(parent);
        boolean isWin32 = "win32".equals(SWT.getPlatform()); //$NON-NLS-1$
        GridLayoutFactory.fillDefaults().extendedMargins(isWin32 ? 0 : 3, 3, 2, 2).applyTo(composite);

        Composite tableComposite = new Composite(composite, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        tableComposite.setLayout(tableColumnLayout);
        table = new Table(tableComposite, SWT.SINGLE | SWT.FULL_SELECTION);
        textLayout = new TextLayout(table.getDisplay());
        textLayout.setOrientation(getDefaultOrientation());
        Font boldFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).setStyle(SWT.BOLD));
        textLayout.setFont(table.getFont());
        textLayout.setText(Messages.QueryBrowserPopup_Categories);
        textLayout.setFont(boldFont);
        AccessibleCompositeAdapter.access(table);

        tableColumnLayout.setColumnData(new TableColumn(table, SWT.NONE), new ColumnWeightData(100, 100));
        table.getShell().addControlListener(new ControlAdapter()
        {
            public void controlResized(ControlEvent e)
            {
                if (!resized)
                {
                    resized = true;
                    e.display.timerExec(100, new Runnable()
                    {
                        public void run()
                        {
                            if (getShell() != null && !getShell().isDisposed())
                            {
                                refresh(filterText.getText().toLowerCase());
                            }
                            resized = false;
                        }
                    });
                }
            }
        });

        table.addKeyListener(new KeyListener()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == SWT.ARROW_UP && table.getSelectionIndex() == 0)
                {
                    filterText.setFocus();
                }
                else if (e.character == SWT.ESC)
                {
                    close();
                }
                else if (e.keyCode == 0x0D && e.stateMask != 0)
                {
                    handleSelection(true);
                }
            }

            public void keyReleased(KeyEvent e)
            {
            // do nothing
            }
        });

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent e)
            {

                if (table.getSelectionCount() < 1)
                    return;

                if (e.button != 1 && e.button != 3)
                    return;

                if (table.equals(e.getSource()))
                {
                    Object o = table.getItem(new Point(e.x, e.y));
                    TableItem selection = table.getSelection()[0];
                    if (selection.equals(o))
                    {
                        if (e.button != 1)
                        {
                            handleSelection(true);
                        }
                    }
                }
            }
        });

        table.addSelectionListener(new SelectionListener()
        {
            public void widgetSelected(SelectionEvent e)
            {
                updateHelp();
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                handleSelection(false);
            }
        });

        final TextStyle boldStyle = new TextStyle(boldFont, null, null);
        Listener listener = new Listener()
        {
            public void handleEvent(Event event)
            {
                QueryBrowserItem entry = (QueryBrowserItem) event.item.getData();
                if (entry != null)
                {
                    switch (event.type)
                    {
                        case SWT.MeasureItem:
                            entry.measure(event, textLayout, resourceManager, boldStyle);
                            break;
                        case SWT.PaintItem:
                            entry.paint(event, textLayout, resourceManager, boldStyle);
                            break;
                        case SWT.EraseItem:
                            entry.erase(event);
                            break;
                    }
                }
                else
                {
                    switch (event.type)
                    {
                        case SWT.MeasureItem:
                            event.height = Math.max(event.height, 16 + 2);
                            event.width = 16;
                            break;
                        case SWT.PaintItem:
                            break;
                        case SWT.EraseItem:
                            break;
                    }
                }
            }
        };
        table.addListener(SWT.MeasureItem, listener);
        table.addListener(SWT.EraseItem, listener);
        table.addListener(SWT.PaintItem, listener);

        // unless one table item is created (and measured) the wrong item height
        // is returned. Therefore more elements are displayed than space is
        // available and - oops - ugly scroll bars appear
        new TableItem(table, SWT.NONE);

        return composite;
    }

    private int computeNumberOfItems()
    {
        int height = table.getClientArea().height;
        int lineWidth = table.getLinesVisible() ? table.getGridLineWidth() : 0;
        return (height - lineWidth) / (table.getItemHeight() + lineWidth);
    }

    private void refresh(String filter)
    {
        int numItems = computeNumberOfItems();

        List<QueryBrowserItem> entries = computeMatchingEntries(filter, numItems);

        refreshTable(entries);

        if (table.getItemCount() > 0)
        {
            table.setSelection(0);
            updateHelp();
        }

        if (filter.length() == 0)
            setInfoText(Messages.QueryBrowserPopup_StartTyping);
        else
            setInfoText(Messages.QueryBrowserPopup_PressCtrlEnter);
    }

    protected Control getFocusControl()
    {
        return filterText;
    }

    public boolean close()
    {
        if (textLayout != null && !textLayout.isDisposed())
        {
            textLayout.dispose();
        }
        if (helpText != null)
        {
            helpText.close();
            helpText = null;
        }
        if (resourceManager != null)
        {
            resourceManager.dispose();
            resourceManager = null;
        }
        return super.close();
    }

    protected Point getInitialSize()
    {
        if (!getPersistBounds())
            return new Point(450, 400);
        return super.getInitialSize();
    }

    protected Point getInitialLocation(Point initialSize)
    {
        if (!getPersistBounds())
        {
            Point size = new Point(400, 400);
            Rectangle parentBounds = getParentShell().getBounds();
            int x = parentBounds.x + parentBounds.width / 2 - size.x / 2;
            int y = parentBounds.y + parentBounds.height / 2 - size.y / 2;
            return new Point(x, y);
        }
        return super.getInitialLocation(initialSize);
    }

    protected IDialogSettings getDialogSettings()
    {
        final IDialogSettings workbenchDialogSettings = MemoryAnalyserPlugin.getDefault().getDialogSettings();
        IDialogSettings result = workbenchDialogSettings.getSection(QueryBrowserPopup.class.getName());
        if (result == null)
        {
            result = workbenchDialogSettings.addNewSection(QueryBrowserPopup.class.getName());
        }
        return result;
    }

    @Override
    protected void fillDialogMenu(IMenuManager dialogMenu)
    {
        dialogMenu.add(new Action(Messages.MultiPaneEditor_Close)
        {
            @Override
            public void run()
            {
                close();
            }
        });
        dialogMenu.add(new Separator());
        super.fillDialogMenu(dialogMenu);
    }

    private void executeFilterText()
    {
        try
        {
            String cmdLine = filterText.getText();

            close();

            IQueryContext context = editor.getQueryContext();
            ArgumentSet argumentSet = CommandLine.parse(context, cmdLine);
            boolean reproducable = true;
            if (!argumentSet.isExecutable() && context.available(ISnapshot.class, null))
            {
                // Fill in some missing arguments from the policy
                ISnapshot snapshot = (ISnapshot) context.get(ISnapshot.class, null);
                QueryDescriptor query = argumentSet.getQueryDescriptor();
                String before = argumentSet.toString();
                policy.fillInObjectArguments(snapshot, query, argumentSet);
                String after = argumentSet.toString();
                // See if the arguments have changed
                reproducable = before.equals(after);
            }
            QueryExecution.execute(editor, null, null, argumentSet, false, reproducable);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.showErrorMessage(e);
        }
    }

    private void handleSelection(boolean doUpdateFilterText)
    {
        if (table.getSelectionCount() != 1)
        {
            close();
            return;
        }

        QueryBrowserItem queryBrowserItem = ((QueryBrowserItem) table.getItem(table.getSelectionIndex()).getData());
        Element selectedElement = queryBrowserItem.element;

        if (selectedElement == null)
        {
            // Category, so select just this category
            String name = queryBrowserItem.provider.getName();
            filterText.setText(name);
            filterText.setSelection(0);
            filterText.setFocus();
            return;
        }

        if (doUpdateFilterText)
        {
            filterText.setText(selectedElement.getUsage());
            filterText.setSelection(0);
            filterText.setFocus();
        }
        else
        {
            close();

            if (!editor.isDisposed())
            {
                try
                {
                    selectedElement.execute(editor);
                }
                catch (SnapshotException e)
                {
                    ErrorHelper.showErrorMessage(e);
                }
            }
        }
    }

    private void updateHelp()
    {
        if (table.getSelectionCount() > 0)
        {
            Element selectedElement = ((QueryBrowserItem) table.getItem(table.getSelectionIndex()).getData()).element;
            QueryDescriptor query = selectedElement != null ? selectedElement.getQuery() : null;

            if (query != null)
            {
                // At least display the usage even if no help is available.
                if (helpText == null || helpText.getQuery() != query)
                {
                    if (helpText != null)
                        helpText.close();

                    Rectangle myBounds = getShell().getBounds();
                    Rectangle helpBounds = new Rectangle(myBounds.x, myBounds.y + myBounds.height, myBounds.width,
                                    SWT.DEFAULT);
                    helpText = new QueryContextHelp(getShell(), query, editor.getQueryContext(), helpBounds);
                    helpText.open();
                }

            }
            else
            {
                if (helpText != null)
                {
                    helpText.close();
                    helpText = null;
                }
            }
        }
        else
        {
            if (helpText != null)
            {
                helpText.close();
                helpText = null;
            }
        }
    }

    private void refreshTable(List<QueryBrowserItem> entries)
    {
        if (table.getItemCount() > entries.size() && table.getItemCount() - entries.size() > 20)
        {
            table.removeAll();
        }
        TableItem[] items = table.getItems();

        int index = 0;

        for (QueryBrowserItem entry : entries)
        {
            TableItem item;
            if (index < items.length)
            {
                item = items[index];
                table.clear(index);
            }
            else
            {
                item = new TableItem(table, SWT.NONE);
            }
            item.setData(entry);

            if (entry.element != null)
                item.setText(0, entry.element.getLabel());
            else
                item.setText(0, entry.provider.getName());
            index++;
        }

        if (index < items.length)
        {
            table.remove(index, items.length - 1);
        }
    }

    private List<QueryBrowserItem> computeMatchingEntries(String filter, int maxCount)
    {
        // first: collect entries on a category level (distribute search
        // results...)

        List<List<QueryBrowserItem>> entries = new ArrayList<List<QueryBrowserItem>>(providers.size());
        // For all except the initial page, list all the queries and let the receiver scroll
        boolean noLimits = filter.length() > 0;
        // Allow for extra entry to select all
        if (!noLimits)
            maxCount--;
        if (filter.equals(QueryAllProvider.ALL.toLowerCase()))
            filter = ""; //$NON-NLS-1$

        int[] indexPerCategory = new int[providers.size()];
        int countPerCategory = Math.min(maxCount / 4, INITIAL_COUNT_PER_PROVIDER);
        int countTotal = 0;

        boolean done = false;

        while ((countTotal < maxCount || noLimits) && !done)
        {
            done = true;
            for (int ii = 0; ii < providers.size() && (countTotal < maxCount || noLimits); ii++)
            {
                List<QueryBrowserItem> e = null;

                if (ii == entries.size())
                {
                    entries.add(e = new ArrayList<QueryBrowserItem>());
                    countTotal++; // categories are rendered on one line each
                }
                else
                {
                    e = entries.get(ii);
                }

                int count = 0;
                QueryBrowserProvider provider = providers.get(ii);

                Element[] elements = provider.getElementsSorted();
                int j = indexPerCategory[ii];
                while (j < elements.length && (count < countPerCategory && countTotal < maxCount || noLimits))
                {
                    Element element = elements[j];
                    QueryBrowserItem entry = null;
                    if (filter.length() == 0)
                    {
                        entry = new QueryBrowserItem(element, provider, 0, 0);
                    }
                    else
                    {
                        String sortLabel = element.getLabel();
                        int index = sortLabel.toLowerCase().indexOf(filter);
                        if (index != -1)
                        {
                            entry = new QueryBrowserItem(element, provider, index, index + filter.length() - 1);
                        }
                        else
                        {
                            // test whether category or description or usage match
                            index = provider.getName().toLowerCase().indexOf(filter);
                            if (index == -1 && element.getQuery() != null)
                            {
                                String help = element.getQuery().getHelp();
                                if (help != null)
                                    index = help.toLowerCase().indexOf(filter);
                                if (index == -1)
                                {
                                    String usage = element.getUsage();
                                    index = usage.toLowerCase().indexOf(filter);
                                }
                            }

                            if (index != -1)
                                entry = new QueryBrowserItem(element, provider, 0, 0);
                        }
                    }

                    if (entry != null)
                    {
                        e.add(entry);
                        count++;
                        countTotal++;
                    }
                    j++;
                }
                indexPerCategory[ii] = j;
                if (j < elements.length)
                {
                    done = false;
                }
            }

            countPerCategory = 1;
        }

        // second: convert 'em to a flat list for easy display
        List<QueryBrowserItem> answer = new ArrayList<QueryBrowserItem>();

        for (List<QueryBrowserItem> items : entries)
        {
            if (!items.isEmpty())
            {
                QueryBrowserItem firstElement = items.get(0);
                answer.add(new QueryBrowserItem(null, firstElement.provider, 0, 0));
                answer.addAll(items);
                answer.get(answer.size() - 1).lastInCategory = true;
            }
        }

        // Add the option to display all queries
        if (!noLimits && maxCount >= 0)
            answer.add(new QueryBrowserItem(null, new QueryAllProvider(), 0, 0));

        return answer;
    }

}
