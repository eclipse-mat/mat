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
package org.eclipse.mat.ui.snapshot.views.inspector;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
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
import org.eclipse.mat.ui.snapshot.ImageHelper;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.mat.ui.snapshot.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;


public class InspectorView extends ViewPart implements IPartListener, ISelectionChangedListener
{
    private HeapEditor editor;
    /* package */ISnapshot snapshot;

    private Composite top;
    private TableViewer objectDetails;
    private TableViewer objectFields;
    private Action syncAction;

    private Menu fieldsContextMenu;
    private Menu detailsContextMenu;

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

    private static class DetailsLabelProvider extends LabelProvider
    {

        @Override
        public String getText(Object element)
        {
            if (element instanceof InfoItem)
                return ((InfoItem) element).getText();
            else if (element instanceof ObjectNode)
                return ((ObjectNode) element).getLabel();
            else if (element instanceof GCRootInfo[])
                return "GC root: " + GCRootInfo.getTypeSetAsString((GCRootInfo[]) element);
            else
                return "";
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
            if (newInput instanceof Collection)
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

    static class InspectorLayout extends Layout
    {

        @Override
        protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache)
        {
            Point extent = new Point(300, SWT.DEFAULT);
            if (wHint != SWT.DEFAULT)
                extent.x = wHint;
            if (hHint != SWT.DEFAULT)
                extent.y = hHint;
            return extent;
        }

        @Override
        protected void layout(Composite composite, boolean flushCache)
        {
            Control[] children = composite.getChildren();
            if (children.length != 2)
                throw new RuntimeException("This is a specialized layout. Please adapt.");

            Rectangle clientArea = composite.getClientArea();
            Table t = (Table) children[0];

            // on win32, item height is reported to low the first time around
            int itemHeight = t.getItemHeight();
            if (itemHeight < 17)
                itemHeight = 17;

            int scrollbarHeight = 2;
            if (!"win32".equals(Platform.getOS()))
                scrollbarHeight = t.getHorizontalBar().getSize().y;

            int detailsHeight = 9 * itemHeight + scrollbarHeight;

            // see Table#checkStyle -> H_SCROLL and V_SCROLL are always set,
            // hence even under Mac OS X the scroll bars are always visible

            layoutDetails(clientArea, detailsHeight, children[0]);
            layoutFields(clientArea, detailsHeight, children[1]);
        }

        private void layoutFields(Rectangle clientArea, int detailsHeight, Control child)
        {
            Table t = (Table) child;

            int width = clientArea.width;
            width -= t.getColumn(0).getWidth();
            width -= t.getColumn(1).getWidth();

            Point preferredSize = t.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            if (preferredSize.y > clientArea.height - detailsHeight - t.getHeaderHeight())
            {
                Point vBarSize = t.getVerticalBar().getSize();
                width -= vBarSize.x;
            }

            width = Math.max(width, 250);

            // resize
            Point oldSize = t.getSize();
            if (oldSize.x > clientArea.width)
            {
                t.getColumn(2).setWidth(width);
                child.setBounds(clientArea.x, clientArea.y + detailsHeight, clientArea.width, clientArea.height
                                - detailsHeight);
                child.setSize(clientArea.width, clientArea.height - detailsHeight);
            }
            else
            {
                child.setBounds(clientArea.x, clientArea.y + detailsHeight, clientArea.width, clientArea.height
                                - detailsHeight);
                child.setSize(clientArea.width, clientArea.height - detailsHeight);
                t.getColumn(2).setWidth(width);
            }
        }

        private void layoutDetails(Rectangle clientArea, int detailsHeight, Control child)
        {
            Table t = (Table) child;
            TableColumn col = t.getColumn(0);

            Point oldSize = t.getSize();
            int scrollbarWidth = 0;
            if (!"win32".equals(Platform.getOS()))
                scrollbarWidth = t.getVerticalBar().getSize().x;

            if (oldSize.x > clientArea.width)
            {
                col.setWidth(clientArea.width - scrollbarWidth);
                child.setBounds(clientArea.x, clientArea.y, clientArea.width, detailsHeight);
                child.setSize(clientArea.width, detailsHeight);
            }
            else
            {
                child.setBounds(clientArea.x, clientArea.y, clientArea.width, detailsHeight);
                child.setSize(clientArea.width, detailsHeight);
                col.setWidth(clientArea.width - scrollbarWidth);
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // view construction
    // //////////////////////////////////////////////////////////////

    @Override
    public void createPartControl(final Composite parent)
    {
        top = new Composite(parent, SWT.TOP);
        top.setLayout(new InspectorLayout());

        createDetailsTable(top);
        createFieldsTable(top);

        // add page listener
        getSite().getPage().addPartListener(this);

        makeActions();
        hookContextMenu();
        hookDoubleClickAction();

        showBootstrapPart();
    }

    private void createDetailsTable(Composite composite)
    {
        objectDetails = new TableViewer(composite, SWT.FULL_SELECTION);

        TableColumn column = new TableColumn(objectDetails.getTable(), SWT.LEFT);
        column.setWidth(380);

        objectDetails.getTable().setHeaderVisible(false);
        objectDetails.getTable().setLinesVisible(false);
        objectDetails.getTable().setSize(380, 120);
        objectDetails.setLabelProvider(new DetailsLabelProvider());
        objectDetails.setContentProvider(new TableContentProvider());
    }

    private void createFieldsTable(Composite composite)
    {
        objectFields = new TableViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);

        objectFields.setContentProvider(new FieldsContentProvider());
        objectFields.setLabelProvider(new FieldsLabelProvider(this, objectFields.getTable().getFont()));

        getViewSite().getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), new Action()
        {

            @Override
            public void run()
            {
                Copy.copyToClipboard(objectFields.getControl());
            }

        });

        TableColumn tableColumn = new TableColumn(objectFields.getTable(), SWT.LEFT);
        tableColumn.setText("Type");
        tableColumn.setWidth(50);

        tableColumn = new TableColumn(objectFields.getTable(), SWT.LEFT);
        tableColumn.setWidth(80);
        tableColumn.setText("Name");

        tableColumn = new TableColumn(objectFields.getTable(), SWT.LEFT);
        tableColumn.setWidth(250);
        tableColumn.setText("Value");

        objectFields.getTable().setHeaderVisible(true);
    }

    private void makeActions()
    {
        syncAction = new Action(null, IAction.AS_CHECK_BOX)
        {
            @Override
            public void run()
            {
                if (this.isChecked())
                {
                    if (!keepInSync)
                    {
                        showBootstrapPart();
                        if (editor != null)
                            updateOnSelection(editor.getSelection());
                    }

                    keepInSync = true;
                }
                else
                {
                    keepInSync = false;
                }
            }
        };
        syncAction.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SYNCED));
        syncAction.setDisabledImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SYNCED_DISABLED));
        syncAction.setText("Link with Snapshot");
        syncAction.setChecked(true);
    }

    private void hookContextMenu()
    {
        // fields
        fieldsContextMenu = new Menu(objectFields.getControl());
        fieldsContextMenu.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuShown(MenuEvent e)
            {
                MenuItem[] items = fieldsContextMenu.getItems();
                for (int ii = 0; ii < items.length; ii++)
                    items[ii].dispose();

                PopupMenu popup = new PopupMenu();
                fillContextMenu(popup, objectFields);
                popup.addToMenu(editor.getEditorSite().getActionBars().getStatusLineManager(), fieldsContextMenu);
            }

        });
        objectFields.getControl().setMenu(fieldsContextMenu);

        // object details
        detailsContextMenu = new Menu(objectDetails.getControl());
        detailsContextMenu.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuShown(MenuEvent e)
            {
                MenuItem[] items = detailsContextMenu.getItems();
                for (int ii = 0; ii < items.length; ii++)
                    items[ii].dispose();

                PopupMenu popup = new PopupMenu();
                fillContextMenu(popup, objectDetails);
                popup.addToMenu(editor.getEditorSite().getActionBars().getStatusLineManager(), detailsContextMenu);
            }

        });
        objectDetails.getControl().setMenu(detailsContextMenu);
    }

    private void hookDoubleClickAction()
    {}

    private void fillContextMenu(PopupMenu manager, TableViewer viewer)
    {
        IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();

        if (editor != null)
        {
            InspectorContextProvider contextProvider = new InspectorContextProvider(snapshot);
            final Object firstElement = contextProvider.getContext(selection.getFirstElement());

            IStructuredSelection editorSelection = (IStructuredSelection) editor.getSelection();
            final Object editorElement = editorSelection.getFirstElement();
            
            boolean isObject = firstElement instanceof IContextObject && editorElement instanceof IContextObject
                            && ((IContextObject) firstElement).getObjectId() != ((IContextObject) editorElement)
                                            .getObjectId();

            if (isObject)
            {
                manager.add(new Action("Go Into")
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
            contextMenu.addContextActions(manager, selection);

            manager.addSeparator();
            manager.add(syncAction);
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

        if (fieldsContextMenu != null && !fieldsContextMenu.isDisposed())
            fieldsContextMenu.dispose();

        if (detailsContextMenu != null && !detailsContextMenu.isDisposed())
            detailsContextMenu.dispose();

        getSite().getPage().removePartListener(this);
        super.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // view lifecycle
    // //////////////////////////////////////////////////////////////

    @Override
    public void setFocus()
    {
        objectFields.getTable().setFocus();
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
                syncAction.setChecked(true);
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

            Job job = new Job("Update Object Details")
            {

                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    try
                    {
                        if (snapshot == null)
                            return Status.OK_STATUS;

                        final IObject object = snapshot.getObject(objectId);

                        Object current = objectFields.getInput();
                        if (current instanceof BaseNode && ((BaseNode) current).objectId == object.getObjectId()) { return Status.OK_STATUS; }

                        // prepare object info
                        final List<Object> classInfos = prepareClassInfo(object);

                        // prepare field info
                        final LazyFields<?> fields = prepareFieldsInfo(object);

                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                        {
                            public void run()
                            {
                                objectDetails.setInput(classInfos);
                                objectFields.setInput(fields);
                            }
                        });

                        return Status.OK_STATUS;
                    }
                    catch (SnapshotException e)
                    {
                        return new Status(IStatus.ERROR, MemoryAnalyserPlugin.PLUGIN_ID, "Error updating Inspector", e);
                    }
                }

                private LazyFields<?> prepareFieldsInfo(final IObject object)
                {
                    LazyFields<?> fields = null;
                    if (object instanceof IClass)
                        fields = new LazyFields.Class((IClass) object);
                    else if (object instanceof IInstance)
                        fields = new LazyFields.Instance((IInstance) object);
                    else if (object instanceof IPrimitiveArray)
                        fields = new LazyFields.PrimitiveArray((IPrimitiveArray) object);
                    else if (object instanceof IObjectArray)
                        fields = new LazyFields.ObjectArray((IObjectArray) object);
                    else
                        fields = LazyFields.EMPTY;

                    return fields;
                }

                private List<Object> prepareClassInfo(final IObject object) throws SnapshotException
                {
                    List<Object> details = new ArrayList<Object>();

                    details.add(new InfoItem(object.getObjectId(), MemoryAnalyserPlugin
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.ID), "0x"
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
                                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PACKAGE), ""));
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
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SIZE), MessageFormat.format(
                                    "{0,number} (shallow size)", object.getUsedHeapSize())));
                    details.add(new InfoItem(MemoryAnalyserPlugin
                                    .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SIZE), MessageFormat.format(
                                    "{0,number} (retained size)", object.getRetainedHeapSize())));

                    GCRootInfo[] gc = object.getGCRootInfo();
                    details.add(gc != null ? (Object) gc : new InfoItem(MemoryAnalyserPlugin
                                    .getImageDescriptor(ImageHelper.Decorations.GC_ROOT), "no GC root"));
                    return details;
                }

            };
            job.schedule();
        }
    }

    private void clearInput()
    {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                // fix: add one (dummy) row to each table so that the
                // ColumnViewer does not cache the last (real) row subject
                // which in turn often has a reference to the snapshot

                if (objectDetails.getContentProvider() != null)
                {
                    objectDetails.setInput(new Object[] { new Object() });
                }

                if (objectFields.getContentProvider() != null)
                {
                    objectFields.setInput(LazyFields.EMPTY);
                }

                if (!detailsContextMenu.isDisposed())
                    disposeItems(detailsContextMenu);
                if (!fieldsContextMenu.isDisposed())
                    disposeItems(fieldsContextMenu);
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
