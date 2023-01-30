/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - accessibility related fixes 
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IContributionManagerOverrides;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.accessibility.AccessibleToolbarAdapter;
import org.eclipse.mat.ui.snapshot.ImageHelper.ImageImageDescriptor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.NavigatorState;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IWorkbenchPartOrientation;

public class MultiPaneEditor extends EditorPart implements IResourceChangeListener
{
    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    public static final int PROP_ACTION_BAR = 0x1000001;
    public static final int PROP_FOLDER_IMAGE = 0x1000002;

    private LRUList<AbstractEditorPane> nestedPanes = new LRUList<AbstractEditorPane>();

    private ToolBarManager toolbarMgr;
    private ToolBarManager toolbarMgrHelp;
    private CTabFolder container;

    private List<IMultiPaneEditorContributor> contributors = new ArrayList<IMultiPaneEditorContributor>();
    private Menu menu;
    private NavigatorState navigatorState;

    private File resource;
    private IQueryContext queryContext;

    // //////////////////////////////////////////////////////////////
    // multi pane editor creation & disposal
    // //////////////////////////////////////////////////////////////

    public final void createPartControl(final Composite parent)
    {
        Job job = createInitializationJob();

        if (job == null)
            createPaneArea(parent);
        else
            createJobPane(parent, job);

        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
    }

    private void createJobPane(final Composite parent, final Job job)
    {
        FormToolkit toolkit = new FormToolkit(parent.getDisplay());

        final Form form = toolkit.createForm(parent);
        form.setText(Messages.MultiPaneEditor_Opening);
        final RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginLeft = 10;
        form.getBody().setLayout(layout);

        final Text text = new Text(form.getBody(), SWT.MULTI | SWT.WRAP);
        text.setText(job.getName());

        final Button cancel = new Button(form.getBody(), SWT.FLAT);
        cancel.setText(Messages.MultiPaneEditor_Cancel);
        cancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                cancel.setEnabled(false);
                job.cancel();
            }

        });

        job.addJobChangeListener(new JobChangeAdapter()
        {
            @Override
            public void done(final IJobChangeEvent event)
            {
                if (parent.isDisposed())
                    return;
                parent.getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (parent.isDisposed())
                            return;
                        switch (event.getResult().getSeverity())
                        {
                            case IStatus.OK:
                                form.dispose();
                                createPaneArea(parent);
                                parent.layout();
                                break;
                            case IStatus.CANCEL:
                                IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
                                for (int ii = 0; ii < pages.length; ii++)
                                {
                                    IEditorPart editorPart = pages[ii].findEditor(getEditorInput());
                                    if (editorPart != null)
                                        pages[ii].closeEditor(editorPart, true);
                                }
                                break;
                            case IStatus.ERROR:
                            case IStatus.INFO:
                            case IStatus.WARNING:
                            default:
                                cancel.setEnabled(false);
                                form.setText(Messages.MultiPaneEditor_Failed_to_open);
                                RowData rd = new RowData(form.getBody().getClientArea().width - layout.marginLeft - layout.marginRight, SWT.DEFAULT);
                                text.setLayoutData(rd);
                                text.setText(event.getResult().getMessage());
                                text.requestLayout();
                                break;
                        }
                    }
                });
            }

        });
        job.setUser(true);
        // Parsing / opening a heap dump, so long
        job.setPriority(Job.LONG);
        job.schedule();
    }

    protected Job createInitializationJob()
    {
        return null;
    }

    private final void createPaneArea(Composite parent)
    {
        // create composite
        Composite composite = new Composite(parent, SWT.TOP);
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(composite);

        // create tool bar
        ToolBar toolbar = new ToolBar(composite, SWT.FLAT);
        // Add custom AccessibleAdapter, passing in associated ToolBar.
        toolbar.getAccessible().addAccessibleListener(new AccessibleToolbarAdapter(toolbar) );
        GridDataFactory.fillDefaults().grab(true, false).indent(0, 2).applyTo(toolbar);
        toolbarMgr = new ToolBarManager(toolbar);

        // create tool bar
        toolbar = new ToolBar(composite, SWT.FLAT);
        // Add custom AccessibleAdapter, passing in associated ToolBar.
        toolbar.getAccessible().addAccessibleListener(new AccessibleToolbarAdapter(toolbar) );
        GridDataFactory.fillDefaults().grab(false, false).indent(0, 2).applyTo(toolbar);
        toolbarMgrHelp = new ToolBarManager(toolbar);

        // create folder
        container = new CTabFolder(composite, SWT.TOP | SWT.BORDER | SWT.FLAT);

        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).indent(0, 0).applyTo(container);

        container.setUnselectedImageVisible(true);
        container.setUnselectedCloseVisible(false);

        container.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int newPageIndex = container.indexOf((CTabItem) e.item);
                pageChange(newPageIndex);
            }
        });

        container.addCTabFolder2Listener(new CTabFolder2Listener()
        {

            public void close(CTabFolderEvent event)
            {
                removePage(container.indexOf((CTabItem) event.item));
                updateToolbar();
            }

            public void maximize(CTabFolderEvent event)
            {}

            public void minimize(CTabFolderEvent event)
            {}

            public void restore(CTabFolderEvent event)
            {}

            public void showList(CTabFolderEvent event)
            {
                List<Action> actions = new ArrayList<Action>();

                CTabItem[] items = container.getItems();
                for (int ii = 0; ii < items.length; ii++)
                {
                    final int pageIndex = ii;

                    Action action = new Action()
                    {
                        @Override
                        public void run()
                        {
                            setActivePage(pageIndex);
                            pageChange(pageIndex);
                        }
                    };

                    action.setText(items[ii].isShowing() ? items[ii].getText() : items[ii].getText() + "*");//$NON-NLS-1$
                    action.setToolTipText(items[ii].getToolTipText());
                    if (items[ii].getImage() != null)
                        action.setImageDescriptor(new ImageImageDescriptor(items[ii].getImage()));

                    actions.add(action);
                }

                Collections.sort(actions, new Comparator<Action>()
                {
                    public int compare(Action a1, Action a2)
                    {
                        return a1.getText().compareToIgnoreCase(a2.getText());
                    }
                });

                PopupMenu popupMenu = new PopupMenu();
                for (Action action : actions)
                    popupMenu.add(action);

                if (menu != null && !menu.isDisposed())
                    menu.dispose();

                menu = popupMenu.createMenu(getEditorSite().getActionBars().getStatusLineManager(), container);
                menu.setVisible(true);

                event.doit = false;

            }
        });

        container.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                nestedPanes.touch((AbstractEditorPane) e.item.getData());
            }

        });

        container.addListener(SWT.MouseDown, new Listener()
        {

            public void handleEvent(Event event)
            {
                if (event.button != 3)
                    return;

                CTabItem item = container.getItem(new Point(event.x, event.y));
                if (item == null)
                    return;

                showPopupMenuFor(item);
            }

        });

        // For keyboard access to the pop-up menu etc.
        IContextService contextService = getSite().getService(IContextService.class);
        contextService.activateContext("org.eclipse.mat.ui.editor"); //$NON-NLS-1$

        // create pages
        createContributors();
        createInitialPanes();

        // set the active page (page 0 by default),
        // unless it has already been done
        if (getActivePage() == -1 && container.getItemCount() > 0)
            pageChange(0);
    }

    /**
     * read pane configuration and create initial set of pages
     */
    protected void createInitialPanes()
    {}

    /**
     * read configured contributors and create
     */
    private void createContributors()
    {
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint(MemoryAnalyserPlugin.PLUGIN_ID + ".editorContributions"); //$NON-NLS-1$
        if (point != null)
        {
            Map<Integer, List<IMultiPaneEditorContributor>> seq2contributors = new HashMap<Integer, List<IMultiPaneEditorContributor>>();

            IExtension[] extensions = point.getExtensions();
            for (int i = 0; i < extensions.length; i++)
            {
                IConfigurationElement confElements[] = extensions[i].getConfigurationElements();
                for (int jj = 0; jj < confElements.length; jj++)
                {
                    try
                    {
                        String editorClass = confElements[jj].getAttribute("editorClass");//$NON-NLS-1$
                        if (editorClass == null)
                            continue;

                        // test if the contributor applies to this editor
                        boolean isApplicable = false;
                        Class<?> subject = this.getClass();
                        while (subject != EditorPart.class && !isApplicable)
                        {
                            isApplicable = editorClass.equals(subject.getName());
                            subject = subject.getSuperclass();
                        }

                        if (!isApplicable)
                            continue;

                        // sequenceNr
                        String sequenceNrStr = confElements[jj].getAttribute("sequenceNr");//$NON-NLS-1$
                        int sequenceNr = sequenceNrStr != null && sequenceNrStr.length() > 0 ? Integer
                                        .parseInt(sequenceNrStr) : Integer.MAX_VALUE;

                        // instantiate editor
                        IMultiPaneEditorContributor contributor = (IMultiPaneEditorContributor) confElements[jj]
                                        .createExecutableExtension("class");//$NON-NLS-1$

                        List<IMultiPaneEditorContributor> list = seq2contributors.get(sequenceNr);
                        if (list == null)
                            seq2contributors.put(sequenceNr, list = new ArrayList<IMultiPaneEditorContributor>());
                        list.add(contributor);

                    }
                    catch (CoreException e)
                    {
                        MemoryAnalyserPlugin.log(e);
                    }
                }
            }

            // sort and store in contributors list
            List<Integer> keys = new ArrayList<Integer>(seq2contributors.keySet());
            Collections.sort(keys);
            for (Integer key : keys)
            {
                List<IMultiPaneEditorContributor> contributors = seq2contributors.get(key);
                for (IMultiPaneEditorContributor contributor : contributors)
                {
                    contributor.init(this);
                    this.contributors.add(contributor);
                }

            }
        }

    }

    public void dispose()
    {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);

        for (AbstractEditorPane editor : nestedPanes)
            disposePart(editor);
        nestedPanes.clear();

        for (IMultiPaneEditorContributor contributor : contributors)
            contributor.dispose();
        contributors.clear();

        if (toolbarMgr != null)
            toolbarMgr.dispose();
        toolbarMgr = null;

        if (toolbarMgrHelp != null)
            toolbarMgrHelp.dispose();
        toolbarMgrHelp = null;

        if (menu != null && !menu.isDisposed())
            menu.dispose();

        super.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // public methods to manipulate available panes
    // //////////////////////////////////////////////////////////////

    public IEditorInput getPaneEditorInput()
    {
        return getEditorInput();
    }

    /**
     * Create and add a new pane if only if a pane with that id does not exist.
     */
    public void addNewPage(String paneId, Object argument, boolean isSingelton)
    {
        addNewPage(paneId, argument, isSingelton, true, null, null);
    }

    /**
     * Create and add a new pane if only if a pane with that id does not exist.
     */
    public void addNewPage(String paneId, Object argument, boolean isSingelton, boolean doFocus)
    {
        addNewPage(paneId, argument, isSingelton, doFocus, null, null);
    }

    /**
     * Create and add a new pane to the folder.
     */
    public void addNewPage(String id, Object argument)
    {
        addNewPage(id, argument, false, true, null, null);
    }

    public void addNewPage(AbstractEditorPane pane, Object argument, String title, Image image)
    {
        try
        {
            int index = addPage(pane, getPaneEditorInput(), true);

            CTabItem item = getItem(index);
            item.setText(title != null ? title : pane.getTitle());
            item.setImage(image != null ? image : pane.getTitleImage());

            pane.initWithArgument(argument);

            setActivePage(index);
            pageChange(index);
        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

    public void addNewPage(String paneId, Object argument, boolean isSingelton, boolean doFocus, String title,
                    Image image)
    {
        PaneConfiguration cfg = EditorPaneRegistry.instance().forPane(paneId);
        if (cfg == null)
            return;

        if (isSingelton)
        {
            int indexCount = getPageCount();
            for (int i = 0; i < indexCount; i++)
            {
                AbstractEditorPane editor = getEditor(i);
                if (editor.configuration != null && editor.configuration.getId().equals(paneId))
                {
                    editor.initWithArgument(argument);
                    setActivePage(i);
                    return;
                }
            }
        }

        doAddNewPage(cfg, argument, title, image, doFocus);
    }

    private void doAddNewPage(PaneConfiguration editor, Object argument, String title, Image image, boolean doFocus)
    {
        try
        {
            AbstractEditorPane part = editor.build();

            PaneState state;
            if (part instanceof CompositeHeapEditorPane)
                state = new PaneState(PaneType.COMPOSITE_PARENT, null, editor.getId(), false);
            else
                state = new PaneState(PaneType.EDITOR, null, editor.getId(), argument == null);
            state.setImage(part.getTitleImage());
            part.setPaneState(state);

            int index = addPage(part, getPaneEditorInput(), true);
            CTabItem item = getItem(index);
            item.setText(title != null ? title : part.getTitle());
            item.setImage(image != null ? image : part.getTitleImage());

            part.initWithArgument(argument);

            if (doFocus)
            {
                setActivePage(index);
                pageChange(index);
            }
        }
        catch (CoreException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
    }

    private int addPage(AbstractEditorPane editor, IEditorInput input, boolean isClosable) throws PartInitException
    {
        int index = getPageCount();
        addPage(index, editor, input, isClosable);
        return index;
    }

    private void addPage(int index, AbstractEditorPane pane, IEditorInput input, boolean isClosable)
                    throws PartInitException
    {
        IEditorSite site = new MultiPaneEditorSite(this, pane);
        pane.init(site, input);
        Composite parent2 = new Composite(this.container, getOrientation(pane));
        parent2.setLayout(new FillLayout());
        pane.createPartControl(parent2);

        final CTabItem item = new CTabItem(container, (isClosable ? SWT.CLOSE : SWT.NONE), index);
        item.setData(pane);
        item.setControl(parent2);

        nestedPanes.add(pane);

        pane.addPropertyListener(new IPropertyListener()
        {
            public void propertyChanged(Object source, int propertyId)
            {
                MultiPaneEditor.this.handlePropertyChange(item, propertyId);
            }
        });

        navigatorState.paneAdded(pane.getPaneState());
    }

    private void handlePropertyChange(CTabItem item, int propertyId)
    {
        if (propertyId == IWorkbenchPart.PROP_TITLE)
        {
            IEditorPart editor = (IEditorPart) item.getData();
            item.setText(editor.getTitle());
            String tooltip = editor.getTitleToolTip();
            if (tooltip != null)
                item.setToolTipText(tooltip);
        }
        else if (propertyId == PROP_ACTION_BAR)
        {
            updateToolbar();
        }
        else if (propertyId == PROP_FOLDER_IMAGE)
        {
            IEditorPart editor = (IEditorPart) item.getData();
            item.setImage(editor.getTitleImage());
        }
        else
        {
            firePropertyChange(propertyId);
        }
    }

    private int getOrientation(IEditorPart editor)
    {
        if (editor instanceof IWorkbenchPartOrientation)
            return ((IWorkbenchPartOrientation) editor).getOrientation();
        return getOrientation();
    }

    protected void disposePart(final IWorkbenchPart part)
    {
        SafeRunner.run(new SafeRunnable()
        {
            public void run()
            {
                part.dispose();
            }

            public void handleException(Throwable e)
            {
            // Exception has already being logged by Core. Do nothing.
            }
        });
    }

    private void removePage(int pageIndex)
    {
        if (pageIndex < 0 || pageIndex >= getPageCount())
            throw new IndexOutOfBoundsException();

        CTabItem item = getItem(pageIndex);
        AbstractEditorPane pane = getEditor(pageIndex);
        Control paneComposite = item.getControl();

        nestedPanes.remove(pane);
        // first: activate previous pane
        AbstractEditorPane lastPane = nestedPanes.peek();

        if (pane.getPaneState() != null)
            this.navigatorState.paneRemoved(pane.getPaneState());

        // dispose item before disposing editor, in case there's an exception in
        // editor's dispose
        item.dispose();
        disposePart(pane);

        if (paneComposite != null)
            paneComposite.dispose();

        if (lastPane != null)
        {
            CTabItem[] items = container.getItems();
            for (int ii = 0; ii < items.length; ii++)
            {
                if (items[ii].getData() == lastPane)
                {
                    if (getActivePage() != ii)
                    {
                        setActivePage(ii);
                        pageChange(ii);
                    }
                    break;
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // EditorPart implementations
    // //////////////////////////////////////////////////////////////

    public void init(IEditorSite site, IEditorInput input) throws PartInitException
    {
        setSite(site);
        setInput(input);
        site.setSelectionProvider(new MultiPaneEditorSelectionProvider(this));
        navigatorState = new NavigatorState();

        if (input instanceof IFileEditorInput)
        {
            IFile file = ((IFileEditorInput) input).getFile();
            this.resource = file.getLocation().toFile();
            this.setPartName(((IFileEditorInput) input).getName());
        }
        else if (input instanceof IPathEditorInput)
        {
            IPath path = ((IPathEditorInput) input).getPath();
            this.resource = path.toFile();
            this.setPartName(((IPathEditorInput)input).getName());
        }
        else if (input instanceof IURIEditorInput)
        {
            URI uri = ((IURIEditorInput) input).getURI();

            if ("file".equals(uri.getScheme())) //$NON-NLS-1$
            {
                IPath path = new Path(uri.getPath());
                this.resource = path.toFile();
                this.setPartName(path.lastSegment());
            }
            else
            {
                throw new PartInitException(MessageUtil.format(Messages.MultiPaneEditor_UnsupportedScheme, uri
                                .toASCIIString()));
            }
        }
        else
        {
            throw new PartInitException(MessageUtil.format(Messages.MultiPaneEditor_UnsupportedEditorInput, input
                            .getClass().getName()));
        }

    }

    public NavigatorState getNavigatorState()
    {
        return navigatorState;
    }

    public boolean isDirty()
    {
        // use nestedEditors to avoid SWT requests
        for (AbstractEditorPane editor : nestedPanes)
            if (editor.isDirty()) { return true; }
        return false;
    }

    public boolean isSaveAsAllowed()
    {
        return false;
    }

    public void doSave(IProgressMonitor monitor)
    {}

    public void doSaveAs()
    {}

    public void setFocus()
    {
        setFocus(getActivePage());
    }

    private void setFocus(int pageIndex)
    {
        if (pageIndex < 0 || pageIndex >= getPageCount())
            return;

        IEditorPart editor = getEditor(pageIndex);
        if (editor != null)
            editor.setFocus();
    }

    // //////////////////////////////////////////////////////////////
    // IResourceChangeListener listeners
    // //////////////////////////////////////////////////////////////

    public void resourceChanged(final IResourceChangeEvent event)
    {
        if (event.getType() == IResourceChangeEvent.PRE_CLOSE)
        {
            container.getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
                    for (int i = 0; i < pages.length; i++)
                    {
                        if (((FileEditorInput) getEditorInput()).getFile().getProject().equals(event.getResource()))
                        {
                            IEditorPart editorPart = pages[i].findEditor(getEditorInput());
                            pages[i].closeEditor(editorPart, true);
                        }
                    }
                }
            });
        }
    }

    // //////////////////////////////////////////////////////////////
    // pane management
    // //////////////////////////////////////////////////////////////

    protected void pageChange(int newPageIndex)
    {
        Control control = getControl(newPageIndex);
        if (control != null)
        {
            control.setVisible(true);
        }

        AbstractEditorPane pane = getEditor(newPageIndex);

        pane.setFocus();

        updateToolbar();

        IEditorActionBarContributor contributor = getEditorSite().getActionBarContributor();
        if (contributor != null && contributor instanceof MultiPaneEditorContributor)
        {
            ((MultiPaneEditorContributor) contributor).setActivePage(pane);
        }
    }

    protected void showPopupMenuFor(final CTabItem item)
    {
        showPopupMenuFor(item, false);
    }

    /**
     * Show the tab pop-up menu to allow tabs to be closed, moved, etc.
     * @param item
     * @param setLocation Whether to show the menu at the tab, not at the pointer
     */
    protected void showPopupMenuFor(final CTabItem item, boolean setLocation)
    {
        Display display = item.getDisplay();

        Menu menu = new Menu(item.getControl());
        buildPopupMenuFor(item, setLocation, menu);

        // show menu
        menu.setVisible(true);
        while (!menu.isDisposed() && menu.isVisible())
        {
            if (!display.readAndDispatch())
                display.sleep();
        }
        menu.dispose();
    }

    protected void buildPopupMenuFor(final CTabItem item, boolean setLocation, final Menu menu)
    {
        for (MenuItem menuItem : menu.getItems())
        {
            // Clean up if asked to rebuild menu
            menuItem.dispose();
        }
        if (setLocation)
        {   
            Display display = item.getDisplay();
            Rectangle bounds = item.getBounds();
            Rectangle p = display.map(item.getParent(), null, bounds);
            menu.setLocation(p.x, p.y + p.height);
        }

        // close
        MenuItem menuItem = new MenuItem(menu, SWT.PUSH);
        menuItem.setText(Messages.MultiPaneEditor_Close);
        menuItem.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                MultiPaneEditor.this.removePage(container.getSelectionIndex());
                MultiPaneEditor.this.updateToolbar();
            }
        });

        // close others
        if (container.getItemCount() > 1)
        {
            menuItem = new MenuItem(menu, SWT.PUSH);
            menuItem.setText(Messages.MultiPaneEditor_CloseOthers);
            menuItem.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int index = 0;

                    while (index < container.getItemCount())
                    {

                        CTabItem tabItem = container.getItem(index);
                        if (tabItem == item)
                        {
                            index++;
                        }
                        else
                        {
                            MultiPaneEditor.this.removePage(index);
                        }
                    }
                }
            });
        }

        // close tabs to the left
        if (itemsToLeftRight(item, true))
        {
            menuItem = new MenuItem(menu, SWT.PUSH);
            menuItem.setText(Messages.MultiPaneEditor_CloseToLeft);
            menuItem.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int index = 0;

                    while (index < container.getItemCount())
                    {

                        CTabItem tabItem = container.getItem(index);
                        if (tabItem == item)
                        {
                            break;
                        }
                        else
                        {
                            MultiPaneEditor.this.removePage(index);
                        }
                    }
                }
            });
        }

        // close tabs to the right
        if (itemsToLeftRight(item, false))
        {
            menuItem = new MenuItem(menu, SWT.PUSH);
            menuItem.setText(Messages.MultiPaneEditor_CloseToRight);
            menuItem.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int index = 0;
                    boolean close = false;
                    while (index < container.getItemCount())
                    {

                        CTabItem tabItem = container.getItem(index);
                        if (tabItem == item)
                        {
                            close = true;
                            ++index;
                        }
                        else
                        {
                            if (close)
                                MultiPaneEditor.this.removePage(index);
                            else
                                ++index;
                        }
                    }
                }
            });
        }

        new MenuItem(menu, SWT.SEPARATOR);
        if (itemsToLeftRight(item, true))
        {
            menuItem = new MenuItem(menu, SWT.PUSH);
            menuItem.setText(Messages.MultiPaneEditor_MoveTabLeft);
            menuItem.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int index = 0;

                    while (index < container.getItemCount())
                    {

                        CTabItem tabItem = container.getItem(index);
                        if (tabItem == item)
                        {
                            if (index > 0)
                            {
                                swapTabs(index, index - 1);
                                buildPopupMenuFor(item, true, menu);
                                menu.setVisible(true);
                                break;
                            }
                        }
                        else
                        {
                            index++;
                        }
                    }
                }
            });
        }
        if (itemsToLeftRight(item, false))
        {
            menuItem = new MenuItem(menu, SWT.PUSH);
            menuItem.setText(Messages.MultiPaneEditor_MoveTabRight);
            menuItem.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int index = 0;

                    while (index < container.getItemCount())
                    {

                        CTabItem tabItem = container.getItem(index);
                        if (tabItem == item)
                        {
                            if (index < container.getItemCount() - 1)
                            {
                                swapTabs(index, index + 1);
                                buildPopupMenuFor(item, true, menu);
                                menu.setVisible(true);
                                break;
                            }
                        }
                        else
                        {
                            index++;
                        }
                    }
                }
            });
        }

        new MenuItem(menu, SWT.SEPARATOR);
        // close all
        menuItem = new MenuItem(menu, SWT.PUSH);
        menuItem.setText(Messages.MultiPaneEditor_CloseAll);
        menuItem.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int index = 0;

                while (index < container.getItemCount())
                {
                    MultiPaneEditor.this.removePage(index);
                }

                MultiPaneEditor.this.updateToolbar();
            }
        });
    }

    private boolean itemsToLeftRight(CTabItem item, boolean left) {
        int index = 0;
        while (index < container.getItemCount())
        {

            CTabItem tabItem = container.getItem(index);
            if (tabItem == item)
            {
                if (left)
                    return index > 0;
                else
                    return index < container.getItemCount() - 1;
            }
            ++index;
        }
        return index > 0;
    }

    private void swapTabs(int index, int to)
    {
        CTabItem tabItem = container.getItem(to);
        Control ctrl = tabItem.getControl();
        Object data = tabItem.getData();
        int style = tabItem.getStyle();
        String title = tabItem.getText();
        String tooltip = tabItem.getToolTipText();
        Image image = tabItem.getImage();
        int sel = container.getSelectionIndex();
        tabItem.setControl(null);
        tabItem.setData(null);
        tabItem.setImage(null);
        tabItem.dispose();
        CTabItem item = new CTabItem(container, style, index);
        item.setControl(ctrl);
        item.setData(data);
        item.setImage(image);
        item.setText(title);
        item.setToolTipText(tooltip);
        if (sel == to && container.getSelectionIndex() != index)
        {
            /*
             * The target tab was active, but we destroyed and recreated it, 
             * so make it active again (including toolbar items).
             */
            setActivePage(index);
            pageChange(index);
        }
    }

    public class TabMenuAction extends Action
    {
        @Override
        public void run()
        {
            CTabItem item = container.getSelection();
            if (item == null)
                return;

            showPopupMenuFor(item, true);
        }
    }

    public static class Handler extends AbstractHandler
    {

        public Handler()
        {}

        public Object execute(ExecutionEvent executionEvent)
        {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

            IWorkbenchPage page = window.getActivePage();
            if (page == null)
                return null;

            IEditorPart activeEditor = page.getActiveEditor();
            if (!(activeEditor instanceof MultiPaneEditor))
                return null;
            MultiPaneEditor mpe = (MultiPaneEditor)activeEditor;
            mpe.new TabMenuAction().run();
            return null;
        }
    }

    public void updateToolbar()
    {
        toolbarMgr.removeAll();
        toolbarMgrHelp.removeAll();

        ToolbarMgr mgr = new ToolbarMgr(toolbarMgr, toolbarMgrHelp);

        for (IMultiPaneEditorContributor contributor : this.contributors)
            contributor.contributeToToolbar(mgr);

        AbstractEditorPane activeEditor = getActiveEditor();
        if (activeEditor != null)
        {
            mgr.add(new Separator());
            activeEditor.contributeToToolBar(mgr);
        }

        toolbarMgr.update(false);
        toolbarMgrHelp.update(false);

        if (activeEditor != null)
            activeEditor.getEditorSite().getActionBars().updateActionBars();
    }

    // //////////////////////////////////////////////////////////////
    // private accessors
    // //////////////////////////////////////////////////////////////

    public ToolBarManager getToolBarManager()
    {
        return toolbarMgr;
    }

    public AbstractEditorPane getActiveEditor()
    {
        int index = getActivePage();
        if (index != -1)
            return getEditor(index);
        return null;
    }

    private int getActivePage()
    {
        if (container != null && !container.isDisposed())
            return container.getSelectionIndex();
        return -1;
    }

    public void bringPageToTop(PaneState state)
    {
        for (CTabItem item : container.getItems())
        {
            if (((AbstractEditorPane) item.getData()).getPaneState() == state)
            {
                setActivePage(container.indexOf(item));
                break;
            }
        }
    }

    public void initWithAnotherArgument(PaneState parent, PaneState child)
    {
        AbstractEditorPane pane = getEditor(parent);
        if (pane != null)
            pane.initWithArgument(child);
    }

    public void closePage(PaneState state)
    {
        for (CTabItem item : container.getItems())
        {
            if (((AbstractEditorPane) item.getData()).getPaneState() == state)
            {
                removePage(container.indexOf(item));
                break;
            }
        }
    }

    private Control getControl(int pageIndex)
    {
        return getItem(pageIndex).getControl();
    }

    protected AbstractEditorPane getEditor(int pageIndex)
    {
        Item item = getItem(pageIndex);
        return item != null ? (AbstractEditorPane) item.getData() : null;
    }

    public AbstractEditorPane getEditor(PaneState state)
    {
        for (CTabItem item : container.getItems())
        {
            AbstractEditorPane pane = (AbstractEditorPane) item.getData();
            if (pane.getPaneState() == state)
                return pane;
        }

        return null;
    }

    private CTabItem getItem(int pageIndex)
    {
        return container.getItem(pageIndex);
    }

    private int getPageCount()
    {
        // May not have been created yet, or may have been disposed.
        if (container != null && !container.isDisposed())
            return container.getItemCount();
        return 0;
    }

    private void setActivePage(int pageIndex)
    {
        if (pageIndex < 0 || pageIndex >= getPageCount())
            throw new IndexOutOfBoundsException();
        container.setSelection(pageIndex);
    }

    public boolean isDisposed()
    {
        return container.isDisposed();
    }

    // //////////////////////////////////////////////////////////////
    // query context
    // //////////////////////////////////////////////////////////////

    public File getResourceFile()
    {
        return resource;
    }

    public IQueryContext getQueryContext()
    {
        return queryContext;
    }

    protected void setQueryContext(IQueryContext queryContext)
    {
        this.queryContext = queryContext;
    }

    // //////////////////////////////////////////////////////////////
    // toolbar manager (pass on manager to the views, split later)
    // //////////////////////////////////////////////////////////////

    private static class ToolbarMgr implements IToolBarManager
    {
        private IToolBarManager delegate;
        private IToolBarManager help;

        public ToolbarMgr(IToolBarManager delegate, IToolBarManager help)
        {
            this.delegate = delegate;
            this.help = help;
        }

        public void add(IAction action)
        {
            delegate.add(action);
        }

        public void add(IContributionItem item)
        {
            delegate.add(item);
        }

        public void appendToGroup(String groupName, IAction action)
        {
            if ("help".equals(groupName))//$NON-NLS-1$
                help.add(action);
            else
                delegate.appendToGroup(groupName, action);
        }

        public void appendToGroup(String groupName, IContributionItem item)
        {
            if ("help".equals(groupName))//$NON-NLS-1$
                help.add(item);
            else
                delegate.appendToGroup(groupName, item);
        }

        public IContributionItem find(String id)
        {
            return delegate.find(id);
        }

        public IContributionItem[] getItems()
        {
            return delegate.getItems();
        }

        public IContributionManagerOverrides getOverrides()
        {
            return delegate.getOverrides();
        }

        public void insertAfter(String id, IAction action)
        {
            delegate.insertAfter(id, action);
        }

        public void insertAfter(String id, IContributionItem item)
        {
            delegate.insertAfter(id, item);
        }

        public void insertBefore(String id, IAction action)
        {
            delegate.insertBefore(id, action);
        }

        public void insertBefore(String id, IContributionItem item)
        {
            delegate.insertBefore(id, item);
        }

        public boolean isDirty()
        {
            return delegate.isDirty();
        }

        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }

        public void markDirty()
        {
            delegate.markDirty();
        }

        public void prependToGroup(String groupName, IAction action)
        {
            if ("help".equals(groupName))//$NON-NLS-1$
            {
                if (help.isEmpty())
                    help.add(action);
                else
                    help.insertBefore(help.getItems()[0].getId(), action);
            }
            else
            {
                delegate.prependToGroup(groupName, action);
            }
        }

        public void prependToGroup(String groupName, IContributionItem item)
        {
            if ("help".equals(groupName))//$NON-NLS-1$
            {
                if (help.isEmpty())
                    help.add(item);
                else
                    help.insertBefore(help.getItems()[0].getId(), item);
            }
            else
            {
                delegate.prependToGroup(groupName, item);
            }
        }

        public IContributionItem remove(IContributionItem item)
        {
            return delegate.remove(item);
        }

        public IContributionItem remove(String id)
        {
            return delegate.remove(id);
        }

        public void removeAll()
        {
            delegate.removeAll();
        }

        public void update(boolean force)
        {
            delegate.update(force);
        }

    }
}
