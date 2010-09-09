/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Chris Grindstaff
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.views.inspector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.snapshot.ImageHelper;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.mat.ui.snapshot.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;

public class InspectorView extends ViewPart implements IPartListener, ISelectionChangedListener
{
    private HeapEditor editor;
    /* package */ISnapshot snapshot;

    private Composite top;
    private Composite visualViewer;
    private TableViewer topTableViewer;
    private CTabFolder tabFolder;
    private TableViewer attributesTable;
    private TableViewer staticsTable;
    private TreeViewer classHierarchyTree;
    private boolean pinSelection = false;
    private Font font;

    private List<Menu> contextMenus = new ArrayList<Menu>();

    boolean keepInSync = true;

    /* package */static class BaseNode
    {
        int objectId;

        public BaseNode(int objectId)
        {
            this.objectId = objectId;
        }
    }

    private class ObjectNode extends BaseNode
    {
        String label;
        int imageType;

        public ObjectNode(IObject object)
        {
            super(object.getObjectId());
            this.label = object.getTechnicalName();
            this.imageType = ImageHelper.getType(object);
        }

        public String getLabel()
        {
            return label;
        }

        public int getImageType()
        {
            return imageType;
        }
    }

    private static class TopTableLabelProvider extends LabelProvider
    {

        @Override
        public String getText(Object element)
        {
            if (element instanceof InfoItem)
                return ((InfoItem) element).getText();
            else if (element instanceof ObjectNode)
                return ((ObjectNode) element).getLabel();
            else if (element instanceof GCRootInfo[])
                return Messages.InspectorView_GCroot + GCRootInfo.getTypeSetAsString((GCRootInfo[]) element);
            else
                return "";//$NON-NLS-1$
        }

        @Override
        public Image getImage(Object element)
        {
            if (element instanceof InfoItem)
                return MemoryAnalyserPlugin.getDefault().getImage(((InfoItem) element).getDescriptor());
            else if (element instanceof ObjectNode)
                return ImageHelper.getImage(((ObjectNode) element).getImageType());
            else if (element instanceof GCRootInfo[])
                return MemoryAnalyserPlugin.getImage(ImageHelper.Decorations.GC_ROOT);
            else
                return null;
        }

    }

    private static final class TableContentProvider implements IStructuredContentProvider
    {
        Object[] elements;

        public Object[] getElements(Object inputElement)
        {
            return elements;
        }

        public void dispose()
        {}

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
        {
            if (newInput instanceof Collection<?>)
            {
                this.elements = ((Collection<?>) newInput).toArray();
            }
            else
            {
                this.elements = (Object[]) newInput;
            }
        }
    }

    public class InfoItem extends BaseNode
    {
        private ImageDescriptor descriptor;
        private String text;

        public InfoItem(ImageDescriptor descriptor, String text)
        {
            this(-1, descriptor, text);
        }

        public InfoItem(int objectId, ImageDescriptor descriptor, String text)
        {
            super(objectId);
            this.descriptor = descriptor;
            this.text = text;
        }

        public ImageDescriptor getDescriptor()
        {
            return descriptor;
        }

        public String getText()
        {
            return text;
        }
    }

    private class MenuListener extends MenuAdapter
    {
        private Menu menu;
        private StructuredViewer viewer;

        public MenuListener(Menu menu, StructuredViewer viewer)
        {
            this.menu = menu;
            this.viewer = viewer;
        }

        @Override
        public void menuShown(MenuEvent e)
        {
            MenuItem[] items = menu.getItems();
            for (int ii = 0; ii < items.length; ii++)
                items[ii].dispose();

            PopupMenu popup = new PopupMenu();
            fillContextMenu(popup, viewer);
            if (editor != null)
            {
                popup.addToMenu(editor.getEditorSite().getActionBars().getStatusLineManager(), menu);
            }
        }

    }

    private class HierarchyTreeContentProvider implements ITreeContentProvider
    {
        LinkedList<IClass> supers;

        public Object[] getChildren(Object element)
        {
            int index = supers.indexOf(element);
            if (index >= 0 && index + 1 < supers.size())
                return new Object[] { supers.get(index + 1) };
            return ((IClass) element).getSubclasses().toArray();
        }

        public IClass getParent(Object element)
        {
            return ((IClass) element).getSuperClass();
        }

        public boolean hasChildren(Object element)
        {
            return !((IClass) element).getSubclasses().isEmpty();

        }

        public Object[] getElements(Object inputElement)
        {
            if (supers.isEmpty())
                return new Object[0];

            return new Object[] { supers.get(0) };
        }

        public void dispose()
        {}

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
        {
            supers = new LinkedList<IClass>();
            if (newInput instanceof IClass[])
            {
                IClass[] input = (IClass[]) newInput;

                supers = new LinkedList<IClass>();
                supers.add(input[0]);

                while (input[0].hasSuperClass())
                {
                    input[0] = input[0].getSuperClass();
                    supers.addFirst(input[0]);
                }
            }
        }
    }

    private class HierarchyLabelProvider extends LabelProvider implements IFontProvider
    {
        private int classId;

        public HierarchyLabelProvider(int classId)
        {
            super();
            this.classId = classId;
        }

        @Override
        public Image getImage(Object element)
        {
            return (element instanceof IClass) ? ImageHelper.getImage(ImageHelper.Type.CLASS) : null;
        }

        @Override
        public String getText(Object element)
        {
            return (element instanceof IClass) ? ((IClass) element).getName() : "";//$NON-NLS-1$
        }

        public Font getFont(Object element)
        {
            if (element instanceof IClass && ((IClass) element).getObjectId() == classId)
                return font;
            return null;
        }

    }

    // //////////////////////////////////////////////////////////////
    // view construction
    // //////////////////////////////////////////////////////////////

    @Override
    public void createPartControl(final Composite parent)
    {
        SashForm form = new SashForm(parent, SWT.VERTICAL);
        GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 0).spacing(1, 1).applyTo(form);

        top = new Composite(form, SWT.TOP);
        GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 0).spacing(1, 1).applyTo(top);

        IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
        mgr.add(createSyncAction());

        FontDescriptor fontDescriptor = FontDescriptor.createFrom(JFaceResources.getDefaultFont());
        fontDescriptor = fontDescriptor.setStyle(SWT.BOLD);
        this.font = fontDescriptor.createFont(top.getDisplay());

        createTopTable(top);
        createTabFolder(top);
        createVisualViewer(form);
        form.setWeights(new int[] { 95, 5 });

        // add page listener
        getSite().getPage().addPartListener(this);

        hookContextMenu();
        showBootstrapPart();
    }

    private void createVisualViewer(Composite parent)
    {
        visualViewer = new Composite(parent, SWT.TOP);
        visualViewer.addPaintListener(new PaintListener()
        {
            public void paintControl(PaintEvent paintEvent)
            {
                Object toShow = visualViewer.getData("toShow");//$NON-NLS-1$
                if (toShow == null)
                    return;

                if (toShow instanceof Image)
                {
                    paintEvent.gc.drawImage((Image) toShow, 0, 0);
                }
                else if (toShow instanceof RGB)
                {
                    Color color = new Color(paintEvent.display, (RGB) toShow);
                    paintEvent.gc.setBackground(color);
                    paintEvent.gc.fillRectangle(0, 0, visualViewer.getSize().x, visualViewer.getSize().y);
                    color.dispose();
                }
            }
        });
        visualViewer.setVisible(false);
    }

    private Action createSyncAction()
    {
        Action syncAction = new Action(null, IAction.AS_CHECK_BOX)
        {

            @Override
            public void run()
            {
                if (!keepInSync)
                {
                    showBootstrapPart();
                    if (editor != null)
                        updateOnSelection(editor.getSelection());
                    keepInSync = true;
                }
                else
                {
                    keepInSync = false;
                }
                this.setChecked(!keepInSync);
            }

        };
        syncAction.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SYNCED));
        syncAction.setToolTipText(Messages.InspectorView_LinkWithSnapshot);

        return syncAction;

    }

    private void createTopTable(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        topTableViewer = new TableViewer(composite, SWT.FULL_SELECTION);

        Table table = topTableViewer.getTable();
        TableColumnLayout columnLayout = new TableColumnLayout();
        composite.setLayout(columnLayout);

        TableColumn column = new TableColumn(table, SWT.LEFT);
        columnLayout.setColumnData(column, new ColumnWeightData(100, 10));

        // on win32, item height is reported to low the first time around
        int itemHeight = table.getItemHeight() + 1;
        if (itemHeight < 17)
            itemHeight = 17;

        int scrollbarHeight = 2;
        if (!"win32".equals(Platform.getOS()))//$NON-NLS-1$
            scrollbarHeight = table.getHorizontalBar().getSize().y;

        int detailsHeight = 9 * itemHeight + scrollbarHeight;

        table.setHeaderVisible(false);
        table.setLinesVisible(false);
        topTableViewer.setLabelProvider(new TopTableLabelProvider());
        topTableViewer.setContentProvider(new TableContentProvider());

        GridDataFactory.fillDefaults().hint(SWT.DEFAULT, detailsHeight)//
                        .grab(true, false).applyTo(composite);

    }

    private void createTabFolder(Composite parent)
    {
        tabFolder = new CTabFolder(parent, SWT.TOP | SWT.FLAT);
        GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).applyTo(tabFolder);

        ToolBar toolBar = new ToolBar(tabFolder, SWT.HORIZONTAL | SWT.FLAT);
        tabFolder.setTopRight(toolBar);
        // set the height of the tab to display the tool bar correctly
        tabFolder.setTabHeight(Math.max(toolBar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y, tabFolder.getTabHeight()));
        final ToolItem pinItem = new ToolItem(toolBar, SWT.CHECK);
        pinItem.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.PINNED));
        pinItem.setToolTipText(Messages.InspectorView_PinTab);
        pinItem.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                pinSelection = !pinSelection;
                pinItem.setSelection(pinSelection);
            }
        });

        toolBar.pack();

        final CTabItem staticsTab = new CTabItem(tabFolder, SWT.NULL);
        staticsTab.setText(Messages.InspectorView_Statics);
        staticsTable = createTable(tabFolder);
        staticsTab.setControl(staticsTable.getTable().getParent());

        CTabItem instancesTab = new CTabItem(tabFolder, SWT.NULL);
        instancesTab.setText(Messages.InspectorView_Attributes);
        attributesTable = createTable(tabFolder);
        instancesTab.setControl(attributesTable.getTable().getParent());

        CTabItem classHierarchyTab = new CTabItem(tabFolder, SWT.NULL);
        classHierarchyTab.setText(Messages.InspectorView_ClassHierarchy);
        classHierarchyTree = createHierarchyTree(tabFolder);
        classHierarchyTab.setControl(classHierarchyTree.getTree().getParent());

        tabFolder.setSelection(0);
    }

    private TreeViewer createHierarchyTree(CTabFolder parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        TreeViewer classHierarchyTree = new TreeViewer(composite, SWT.FULL_SELECTION);
        classHierarchyTree.setContentProvider(new HierarchyTreeContentProvider());
        classHierarchyTree.setLabelProvider(new HierarchyLabelProvider(-1));

        Tree tree = classHierarchyTree.getTree();
        TreeColumnLayout columnLayout = new TreeColumnLayout();
        composite.setLayout(columnLayout);

        TreeColumn column = new TreeColumn(tree, SWT.LEFT);
        columnLayout.setColumnData(column, new ColumnWeightData(100, 10));

        return classHierarchyTree;
    }

    private TableViewer createTable(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        TableColumnLayout columnLayout = new TableColumnLayout();
        composite.setLayout(columnLayout);
        GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).applyTo(composite);

        final TableViewer viewer = new TableViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        Table table = viewer.getTable();
        viewer.setContentProvider(new FieldsContentProvider());
        viewer.setLabelProvider(new FieldsLabelProvider(this, table.getFont()));

        getViewSite().getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), new Action()
        {
            @Override
            public void run()
            {
                Copy.copyToClipboard(viewer.getControl());
            }
        });

        TableColumn tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setText(Messages.InspectorView_Type);
        tableColumn.setWidth(50);
        columnLayout.setColumnData(tableColumn, new ColumnWeightData(10, 50, false));

        tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setWidth(80);
        tableColumn.setText(Messages.InspectorView_Name);
        columnLayout.setColumnData(tableColumn, new ColumnWeightData(30, 80));

        tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setWidth(250);
        tableColumn.setText(Messages.InspectorView_Value);
        columnLayout.setColumnData(tableColumn, new ColumnWeightData(60, 250, true));

        table.setHeaderVisible(true);

        return viewer;
    }

    private void hookContextMenu()
    {
        createMenu(staticsTable);
        createMenu(attributesTable);
        createMenu(topTableViewer);
        createMenu(classHierarchyTree);
    }

    private void createMenu(StructuredViewer viewer)
    {
        Menu menu = new Menu(viewer.getControl());
        menu.addMenuListener(new MenuListener(menu, viewer));
        viewer.getControl().setMenu(menu);
        contextMenus.add(menu);
    }

    private void fillContextMenu(PopupMenu manager, StructuredViewer viewer)
    {
        IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();

        if (editor != null)
        {
            InspectorContextProvider contextProvider = new InspectorContextProvider(snapshot);
            final Object firstElement = contextProvider.getContext(selection.getFirstElement());

            IStructuredSelection editorSelection = (IStructuredSelection) editor.getSelection();
            final Object editorElement = editorSelection.getFirstElement();

            boolean isObject = firstElement instanceof IContextObject
                            && editorElement instanceof IContextObject
                            && ((IContextObject) firstElement).getObjectId() != ((IContextObject) editorElement)
                                            .getObjectId();

            if (isObject)
            {
                manager.add(new Action(Messages.InspectorView_GoInto)
                {
                    @Override
                    public void run()
                    {
                        updateOnSelection(new StructuredSelection(firstElement));
                    }
                });
                manager.addSeparator();
            }

            QueryContextMenu contextMenu = new QueryContextMenu(editor, contextProvider);
            contextMenu.addContextActions(manager, selection, viewer.getControl());
        }
    }

    private void showBootstrapPart()
    {
        IWorkbenchPage page = getSite().getPage();
        if (page != null)
            partActivated(page.getActiveEditor());
    }

    protected boolean isImportant(IWorkbenchPart part)
    {
        return part instanceof HeapEditor;
    }

    @Override
    public void dispose()
    {
        if (this.editor != null)
        {
            this.editor.removeSelectionChangedListener(this);
            this.editor = null;
        }

        for (Menu menu : contextMenus)
        {
            if (menu != null && !menu.isDisposed())
            {
                menu.dispose();
                menu = null;
            }
        }

        if (font != null)
            font.dispose();

        getSite().getPage().removePartListener(this);
        super.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // view life-cycle
    // //////////////////////////////////////////////////////////////

    @Override
    public void setFocus()
    {
        tabFolder.getSelection().getControl().setFocus();
    }

    public void partActivated(IWorkbenchPart part)
    {
        if (!isImportant(part)) { return; }

        if (!keepInSync)
            return;

        HeapEditor heapEditor = (HeapEditor) part;

        if (this.editor != heapEditor)
        {
            if (this.editor != null)
            {
                this.editor.removeSelectionChangedListener(this);
            }

            this.editor = heapEditor;

            final ISnapshotEditorInput input = heapEditor.getSnapshotInput();
            if (input.hasSnapshot())
            {
                this.snapshot = input.getSnapshot();
            }
            else
            {
                this.snapshot = null;

                // snapshot is not yet available -> register to be informed once
                // the snapshot has been fully loaded
                input.addChangeListener(new ISnapshotEditorInput.IChangeListener()
                {

                    public void onBaselineLoaded(ISnapshot snapshot)
                    {}

                    public void onSnapshotLoaded(ISnapshot snapshot)
                    {
                        if (InspectorView.this.snapshot == null)
                            InspectorView.this.snapshot = snapshot;

                        input.removeChangeListener(this);
                    }

                });
            }

            this.editor.addSelectionChangedListener(this);

            updateOnSelection(this.editor.getSelection());
        }
    }

    public void partBroughtToTop(IWorkbenchPart part)
    {
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part)
    {
        if (!isImportant(part)) { return; }

        HeapEditor heapEditor = (HeapEditor) part;

        if (this.editor == heapEditor)
        {
            this.editor.removeSelectionChangedListener(this);

            clearInput();

            this.snapshot = null;
            this.editor = null;

            if (!keepInSync)
            {
                keepInSync = true;
                showBootstrapPart();
            }
        }
    }

    public void partDeactivated(IWorkbenchPart part)
    {}

    public void partOpened(IWorkbenchPart part)
    {}

    public void selectionChanged(SelectionChangedEvent event)
    {
        if (keepInSync)
        {
            ISelection selection = event.getSelection();
            updateOnSelection(selection);
        }
    }

    private void updateOnSelection(ISelection selection)
    {
        IContextObject objectSet = null;

        if (selection instanceof IStructuredSelection)
        {
            Object object = ((IStructuredSelection) selection).getFirstElement();
            if (object instanceof IContextObject)
                objectSet = (IContextObject) object;
        }

        if (objectSet == null || objectSet.getObjectId() < 0)
        {
            clearInput();
        }
        else
        {
            final int objectId = objectSet.getObjectId();

            // do not update if the selection has not changed (double click)
            Object data = topTableViewer.getData("input"); //$NON-NLS-1$
            if (data != null)
            {
                int current = ((Integer) data).intValue();
                if (current == objectId)
                    return;
            }

            Job job = new Job(Messages.InspectorView_UpdateObjectDetails)
            {

                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    try
                    {
                        if (snapshot == null)
                            return Status.OK_STATUS;

                        final IObject object = snapshot.getObject(objectId);

                        // prepare object info
                        final List<Object> classInfos = prepareClassInfo(object);

                        // prepare static fields info
                        final LazyFields<?> staticFields = prepareStaticFields(object);

                        // prepare attributes
                        final LazyFields<?> attributeFields = prepareAttributes(object);

                        // update visual viewer
                        final Object toShow = prepareVisualInfo(object);

                        topTableViewer.getControl().getDisplay().asyncExec(new Runnable()
                        {
                            public void run()
                            {
                                topTableViewer.setInput(classInfos);
                                topTableViewer.setData("input", objectId);//$NON-NLS-1$
                                staticsTable.setInput(staticFields);
                                attributesTable.setInput(attributeFields);
                                updateVisualViewer(toShow);

                                IClass input = object instanceof IClass ? (IClass) object : object.getClazz();

                                try
                                {
                                    classHierarchyTree.getTree().setRedraw(false);
                                    classHierarchyTree.setInput(null);
                                    classHierarchyTree
                                                    .setLabelProvider(new HierarchyLabelProvider(input.getObjectId()));
                                    classHierarchyTree.setInput(new IClass[] { input });
                                    classHierarchyTree.expandAll();
                                }
                                finally
                                {
                                    classHierarchyTree.getTree().setRedraw(true);
                                }

                                if (!pinSelection)// no tab pinned
                                {
                                    int selectionIndex = tabFolder.getSelectionIndex();
                                    if (selectionIndex <= 1)
                                    {
                                        int newSelectionIndex = (object instanceof IClass) ? 0 : 1;
                                        if (selectionIndex != newSelectionIndex)
                                            tabFolder.setSelection(newSelectionIndex);
                                    }
                                }

                            }

                            private void updateVisualViewer(Object toShow)
                            {
                                Object previous = visualViewer.getData("toShow");//$NON-NLS-1$
                                if (previous instanceof Image)
                                    ((Image) previous).dispose();

                                if (toShow != null)
                                {
                                    if (toShow instanceof ImageData)
                                    {
                                        Image image = new Image(visualViewer.getDisplay(), (ImageData) toShow);
                                        visualViewer.setData("toShow", image);//$NON-NLS-1$
                                    }
                                    else if (toShow instanceof RGB)
                                    {
                                        visualViewer.setData("toShow", toShow);//$NON-NLS-1$
                                    }
                                    visualViewer.redraw();
                                }
                                visualViewer.setVisible(toShow != null);
                                visualViewer.getParent().layout();
                            }
                        });

                        return Status.OK_STATUS;
                    }
                    catch (SnapshotException e)
                    {
                        return new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID,
                                        Messages.InspectorView_ErrorUpdatingInspector, e);
                    }
                }

                private Object prepareVisualInfo(IObject object) throws SnapshotException
                {
                    String kind = object.getClazz().getName();

                    if ("org.eclipse.swt.graphics.RGB".equals(kind))//$NON-NLS-1$
                    {
                        Integer red = (Integer) object.resolveValue("red");//$NON-NLS-1$
                        Integer green = (Integer) object.resolveValue("green");//$NON-NLS-1$
                        Integer blue = (Integer) object.resolveValue("blue");//$NON-NLS-1$

                        if (red == null || green == null || blue == null)
                            return null;

                        return new RGB(red, green, blue);
                    }
                    else if ("org.eclipse.swt.graphics.ImageData".equals(kind))//$NON-NLS-1$
                    {
                        IPrimitiveArray data = (IPrimitiveArray) object.resolveValue("data");//$NON-NLS-1$
                        Integer width = (Integer) object.resolveValue("width");//$NON-NLS-1$
                        Integer height = (Integer) object.resolveValue("height");//$NON-NLS-1$
                        Integer depth = (Integer) object.resolveValue("depth");//$NON-NLS-1$
                        Integer scanlinePad = (Integer) object.resolveValue("scanlinePad");//$NON-NLS-1$
                        Integer transparentPixel = (Integer) object.resolveValue("transparentPixel");//$NON-NLS-1$

                        if (data == null || width == null || height == null || depth == null || scanlinePad == null
                                        || transparentPixel == null)
                            return null;

                        PaletteData paletteData = makePaletteData((IInstance) object.resolveValue("palette"));//$NON-NLS-1$
                        if (paletteData == null)
                            return null;

                        byte[] dataArray = (byte[]) data.getValueArray();
                        byte[] dataCopy = new byte[dataArray.length];
                        System.arraycopy(dataArray, 0, dataCopy, 0, dataArray.length);

                        ImageData imageData = new ImageData(width, height, depth, paletteData, scanlinePad, dataCopy);
                        imageData.transparentPixel = transparentPixel;

                        IPrimitiveArray alphaBytes = (IPrimitiveArray) object.resolveValue("alphaData");//$NON-NLS-1$
                        if (alphaBytes != null)
                        {
                            byte[] alphaDataArray = (byte[]) alphaBytes.getValueArray();
                            byte[] alphaDataCopy = new byte[alphaDataArray.length];
                            System.arraycopy(alphaDataArray, 0, alphaDataCopy, 0, alphaDataArray.length);
                            imageData.alphaData = alphaDataCopy;
                        }

                        return imageData;
                    }

                    return null;
                }

                private LazyFields<?> prepareAttributes(final IObject object)
                {
                    LazyFields<?> fields = null;
                    if (object instanceof IInstance)
                        fields = new LazyFields.Instance((IInstance) object);
                    else if (object instanceof IPrimitiveArray)
                        fields = new LazyFields.PrimitiveArray((IPrimitiveArray) object);
                    else if (object instanceof IObjectArray)
                        fields = new LazyFields.ObjectArray((IObjectArray) object);
                    else if (object instanceof IClass)
                        fields = new LazyFields.Class((IClass) object, true, false);
                    else
                        fields = LazyFields.EMPTY;

                    return fields;
                }

                private LazyFields<?> prepareStaticFields(final IObject object)
                {
                    LazyFields<?> fields = null;
                    if (object instanceof IClass)
                        fields = new LazyFields.Class((IClass) object, false, false);
                    else if (object instanceof IInstance)
                        fields = new LazyFields.Class(object.getClazz(), false, true);
                    else
                        fields = LazyFields.EMPTY;

                    return fields;
                }

                private List<Object> prepareClassInfo(final IObject object) throws SnapshotException
                {
                    List<Object> details = new ArrayList<Object>();

                    details.add(new InfoItem(object.getObjectId(), MemoryAnalyserPlugin
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.ID), "0x"//$NON-NLS-1$
                                    + Long.toHexString(object.getObjectAddress())));

                    String className = object instanceof IClass ? ((IClass) object).getName() : object.getClazz()
                                    .getName();

                    int p = className.lastIndexOf('.');
                    if (p < 0) // primitive
                    {
                        InfoItem item = new InfoItem(object.getObjectId(), ImageHelper.getImageDescriptor(ImageHelper
                                        .getType(object)), className);
                        details.add(item);
                        details.add(new InfoItem(MemoryAnalyserPlugin
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PACKAGE), ""));//$NON-NLS-1$
                    }
                    else
                    {
                        details.add(new InfoItem(object.getObjectId(), ImageHelper.getImageDescriptor(ImageHelper
                                        .getType(object)), className.substring(p + 1)));
                        details.add(new InfoItem(MemoryAnalyserPlugin
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PACKAGE), className
                                        .substring(0, p)));
                    }

                    details.add(new ObjectNode(object.getClazz()));

                    IClass superClass = object instanceof IClass ? ((IClass) object).getSuperClass() : object
                                    .getClazz().getSuperClass();

                    if (superClass != null)
                    {
                        details.add(new InfoItem(superClass.getObjectId(), MemoryAnalyserPlugin
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SUPERCLASS), superClass
                                        .getName()));
                    }

                    if (object instanceof IClass)
                        details.add(new ObjectNode(snapshot.getObject(((IClass) object).getClassLoaderId())));
                    else
                        details.add(new ObjectNode(snapshot.getObject(object.getClazz().getClassLoaderId())));

                    details.add(new InfoItem(MemoryAnalyserPlugin
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SIZE), MessageUtil.format(
                                    Messages.InspectorView_shallowSize, object.getUsedHeapSize())));
                    details.add(new InfoItem(MemoryAnalyserPlugin
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SIZE), MessageUtil.format(
                                    Messages.InspectorView_retainedSize, object.getRetainedHeapSize())));

                    GCRootInfo[] gc = object.getGCRootInfo();
                    details.add(gc != null ? (Object) gc : new InfoItem(MemoryAnalyserPlugin
                                    .getImageDescriptor(ImageHelper.Decorations.GC_ROOT),
                                    Messages.InspectorView_noGCRoot));
                    return details;
                }

                private PaletteData makePaletteData(IInstance palette) throws SnapshotException
                {
                    Boolean isDirect = (Boolean) palette.resolveValue("isDirect");//$NON-NLS-1$
                    if (isDirect == null)
                        return null;

                    if (isDirect)
                    {
                        Integer redMask = (Integer) palette.resolveValue("redMask");//$NON-NLS-1$
                        Integer greenMask = (Integer) palette.resolveValue("greenMask");//$NON-NLS-1$
                        Integer blueMask = (Integer) palette.resolveValue("blueMask");//$NON-NLS-1$

                        if (redMask == null || greenMask == null || blueMask == null)
                            return null;

                        return new PaletteData(redMask, greenMask, blueMask);
                    }
                    else
                    {
                        IObjectArray array = (IObjectArray) palette.resolveValue("colors");//$NON-NLS-1$
                        if (array == null)
                            return null;

                        RGB[] rgbs = new RGB[array.getLength()];
                        long[] refs = array.getReferenceArray();
                        for (int ii = 0; ii < refs.length; ii++)
                        {
                            int id = snapshot.mapAddressToId(refs[ii]);
                            IObject obj = snapshot.getObject(id);
                            if (obj == null)
                                return null;

                            Integer red = (Integer) obj.resolveValue("red");//$NON-NLS-1$
                            Integer green = (Integer) obj.resolveValue("green");//$NON-NLS-1$
                            Integer blue = (Integer) obj.resolveValue("blue");//$NON-NLS-1$

                            if (red == null || green == null || blue == null)
                                return null;

                            rgbs[ii] = new RGB(red, green, blue);
                        }

                        return new PaletteData(rgbs);
                    }
                }
            };
            job.schedule();
        }
    }

    private void clearInput()
    {
        topTableViewer.getControl().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                // fix: add one (dummy) row to each table so that the
                // ColumnViewer does not cache the last (real) row subject
                // which in turn often has a reference to the snapshot

                if (topTableViewer.getContentProvider() != null)
                {
                    topTableViewer.setInput(new Object[] { new Object() });
                    topTableViewer.setData("input", null); //$NON-NLS-1$
                }

                if (staticsTable.getContentProvider() != null)
                {
                    staticsTable.setInput(LazyFields.EMPTY);
                }

                if (attributesTable.getContentProvider() != null)
                {
                    attributesTable.setInput(LazyFields.EMPTY);
                }

                if (classHierarchyTree.getContentProvider() != null)
                {
                    classHierarchyTree.setInput(new Object[] { new Object() });
                }

                for (Menu menu : contextMenus)
                {
                    if (!menu.isDisposed())
                        disposeItems(menu);
                }
            }
        });
    }

    private void disposeItems(Menu menu)
    {
        MenuItem[] items = menu.getItems();
        for (MenuItem menuItem : items)
        {
            if (!menuItem.isDisposed())
                menuItem.dispose();
        }
    }

}
