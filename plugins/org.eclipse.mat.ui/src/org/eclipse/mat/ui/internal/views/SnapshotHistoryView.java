/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - opening history with multiple snapshots from a file
 *    Code from org.eclipse.ui.internal.ide.handlers.ShowInSystemExplorerHandler.java
 *******************************************************************************/
package org.eclipse.mat.ui.internal.views;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.util.Util;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.SnapshotHistoryService.Entry;
import org.eclipse.mat.ui.accessibility.AccessibleCompositeAdapter;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.snapshot.views.SnapshotOutlinePage;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class SnapshotHistoryView extends ViewPart implements org.eclipse.mat.ui.SnapshotHistoryService.IChangeListener
{

    private class Outline extends SnapshotOutlinePage implements SelectionListener
    {
        SnapshotHistoryService.Entry current;

        public Outline()
        {
            table.addSelectionListener(this);
        }

        public void widgetDefaultSelected(SelectionEvent e)
        {
            widgetSelected(e);
        }

        public void widgetSelected(SelectionEvent e)
        {
            SnapshotHistoryService.Entry newEntry = (SnapshotHistoryService.Entry) ((TableItem) e.item).getData();

            if (newEntry != null)
            {
                if (!newEntry.equals(current))
                {
                    current = newEntry;
                }
                updateSnapshotInput();
            }
            else
            {
                current = null;
                updateSnapshotInput();
            }
        }

        @Override
        protected SnapshotInfo getBaseline()
        {
            return null;
        }

        @Override
        protected SnapshotInfo getSnapshot()
        {
            return current != null && current.getInfo() instanceof SnapshotInfo ? (SnapshotInfo) current.getInfo()
                            : null;
        }

        @Override
        protected IPath getSnapshotPath()
        {
            return current != null ? new Path(current.getFilePath()) : null;
        }

        @Override
        public void dispose()
        {
            table.removeSelectionListener(this);
            super.dispose();
        }

    }

    private static class DeleteSnapshotDialog extends MessageDialog
    {
        private boolean deleteInFileSystem = false;
        private Button deleteRadio;

        public DeleteSnapshotDialog(Shell parentShell, String dialogTitle)
        {
            super(parentShell, Messages.SnapshotHistoryView_ConfirmDeletion, //
                            null, // accept the default window icon
                            dialogTitle, //
                            MessageDialog.QUESTION, //
                            new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL }, //
                            0); // yes
        }

        protected Control createCustomArea(Composite parent)
        {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout());

            deleteRadio = new Button(composite, SWT.RADIO);
            deleteRadio.addSelectionListener(selectionListener);
            deleteRadio.setText(Messages.SnapshotHistoryView_DeleteInFileSystem);
            deleteRadio.setFont(parent.getFont());

            Button radio = new Button(composite, SWT.RADIO);
            radio.addSelectionListener(selectionListener);
            radio.setText(Messages.SnapshotHistoryView_DeleteFromHistory);
            radio.setFont(parent.getFont());

            // set initial state
            deleteRadio.setSelection(deleteInFileSystem);
            radio.setSelection(!deleteInFileSystem);

            return composite;
        }

        private SelectionListener selectionListener = new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                Button button = (Button) e.widget;
                if (button.getSelection())
                    deleteInFileSystem = (button == deleteRadio);
            }
        };

        boolean getDeleteInFileSystem()
        {
            return deleteInFileSystem;
        }
    }

    private Table table;

    private Action actionOpen;
    private Action actionRemoveFromList;
    private Action actionDelete;
    private Action actionOpenFileInFileSystem;
    private Action actionDeleteIndeces;
    private Action actionCopy;
    private LocalResourceManager resourceManager;
    private Font italicFont;

    @Override
    public void createPartControl(Composite parent)
    {
        this.table = new Table(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        TableColumn tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setText(Messages.SnapshotHistoryView_RecentlyUsedFiles);
        tableColumn.setWidth(400);
        table.setHeaderVisible(true);
        AccessibleCompositeAdapter.access(table);
        resourceManager = new LocalResourceManager(JFaceResources.getResources(), table);
        italicFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).setStyle(SWT.ITALIC));

        // Expand the column to the full width
        table.addControlListener(new ControlAdapter()
        {
            public void controlResized(ControlEvent e)
            {
                tableColumn.setWidth(table.getClientArea().width);
            }
        });

        table.addMouseListener(new MouseAdapter()
        {

            @Override
            public void mouseDoubleClick(MouseEvent event)
            {
                TableItem[] selection = table.getSelection();
                openFile(selection);
            }

        });

        fillTable();
        makeActions();
        hookContextMenu();

        table.addKeyListener(new KeyAdapter()
        {

            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.character == 0x007F)
                {
                    actionRemoveFromList.run();
                }
            }
        });
        
		// let snapshots be opened with the Enter key
		table.addTraverseListener(new TraverseListener() 
		{
			public void keyTraversed(TraverseEvent e)
			{
				if (e.detail == SWT.TRAVERSE_RETURN)
				{
					actionOpen.run();
				}
			}
		});

        SnapshotHistoryService.getInstance().addChangeListener(this);
    }

    private void fillTable()
    {
        List<SnapshotHistoryService.Entry> lastFiles = SnapshotHistoryService.getInstance().getVisitedEntries();

        for (SnapshotHistoryService.Entry entry : lastFiles)
        {
            TableItem tableItem = new TableItem(table, 0);
            tableItem.setText(entry.getFilePath());
            tableItem.setData(entry);
            File index = null;
            if (entry.getInfo() instanceof SnapshotInfo)
            {
                // Find the prefix path directly
                SnapshotInfo ifo = (SnapshotInfo)entry.getInfo();
                String prefix = ifo.getPrefix();
                if (prefix != null)
                {
                    index = new File(prefix + "index"); //$NON-NLS-1$
                }
            }
            if (index == null || !index.exists())
                tableItem.setFont(italicFont);

            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            IContentType type;
            try (InputStream is = new FileInputStream(entry.getFilePath()))
            {
                type = Platform.getContentTypeManager().findContentTypeFor(is, entry.getFilePath());
            }
            catch (IOException e)
            {
                type = null;
            }
            ImageDescriptor imageDescriptor = registry.getImageDescriptor(entry.getFilePath(), type);
            if (index == null || !index.exists())
                imageDescriptor = ImageDescriptor.createWithFlags(imageDescriptor, SWT.IMAGE_DISABLE);
            tableItem.setImage(MemoryAnalyserPlugin.getDefault().getImage(imageDescriptor));
        }
    }

    private void makeActions()
    {
        actionOpen = new Action()
        {
            public void run()
            {
                TableItem[] selection = table.getSelection();
                openFile(selection);
            }
        };
        actionOpen.setText(Messages.SnapshotHistoryView_Open);
        actionOpen.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.OPEN_SNAPSHOT));

        actionRemoveFromList = new Action()
        {
            public void run()
            {
                TableItem[] selection = table.getSelection();

                // as table items are disposed, copy the path before
                List<Path> toDelete = new ArrayList<Path>(selection.length);

                for (TableItem item : selection)
                    toDelete.add(new Path(((SnapshotHistoryService.Entry) item.getData()).getFilePath()));

                for (Path path : toDelete)
                    SnapshotHistoryService.getInstance().removePath(path);
            }
        };
        actionRemoveFromList.setText(Messages.SnapshotHistoryView_RemoveFromList);
        actionRemoveFromList.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                        org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        actionDelete = new Action()
        {

            @Override
            public void run()
            {
                TableItem[] selection = table.getSelection();
                if (selection.length == 0)
                    return;

                // as table items are disposed, copy the path before
                List<SnapshotHistoryService.Entry> toDelete = new ArrayList<SnapshotHistoryService.Entry>(selection.length);

                for (TableItem item : selection)
                    toDelete.add((SnapshotHistoryService.Entry) item.getData());

                String dialogTitle;
                if (toDelete.size() > 1)
                    dialogTitle = MessageUtil
                                    .format(Messages.SnapshotHistoryView_AreYouSure4ManyFiles, toDelete.size());
                else
                    dialogTitle = MessageUtil.format(Messages.SnapshotHistoryView_AreYouSure4OneFile, //
                                    new File(toDelete.get(0).getFilePath()).getAbsolutePath());

                DeleteSnapshotDialog deleteSnapshotDialog = new DeleteSnapshotDialog(table.getShell(), dialogTitle);

                int response = deleteSnapshotDialog.open();
                if (response != IDialogConstants.OK_ID)
                    return;

                if (deleteSnapshotDialog.getDeleteInFileSystem())
                {
                    List<File> problems = new ArrayList<File>();
                    for (SnapshotHistoryService.Entry entry : toDelete)
                    {
                        File path = new File(entry.getFilePath());
                        IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                                        .findEditor(new PathEditorInput(new Path(path.getAbsolutePath())));
                        if (editor != null)
                            getSite().getPage().closeEditor(editor, true);

                        File index = path;
                        if (entry.getInfo() instanceof SnapshotInfo)
                        {
                            // Find the prefix path directly
                            SnapshotInfo ifo = (SnapshotInfo)entry.getInfo();
                            String prefix = ifo.getPrefix();
                            if (prefix != null)
                            {
                                index = new File(prefix + "index"); //$NON-NLS-1$
                                editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                                                .findEditor(new PathEditorInput(new Path(index.getAbsolutePath())));
                                if (editor != null)
                                    getSite().getPage().closeEditor(editor, true);
                            }
                        }
                        if (!path.delete())
                        {
                            problems.add(path);
                        }
                        deleteIndexes(index, problems);
                        if (!path.exists())
                        {
                            // Only remove the entry if we managed to delete it.
                            // Otherwise, the user can retry later, or remove just the entry.
                            SnapshotHistoryService.getInstance().removePath(new Path(path.getAbsolutePath()));
                        }
                        if (!index.exists())
                        {
                            // Perhaps the history was for an index file, or the index file also listed in the history
                            SnapshotHistoryService.getInstance().removePath(new Path(index.getAbsolutePath()));
                        }
                    }
                    if (!problems.isEmpty())
                    {
                        showProblems(problems);
                    }
                    else
                    {
                        MessageBox box = new MessageBox(table.getShell(), SWT.OK | SWT.ICON_INFORMATION);
                        box.setMessage(Messages.SnapshotHistoryView_DeleteInFileSystemSuccess);
                        box.open();
                    }
                }
                else
                {
                    for (SnapshotHistoryService.Entry entry : toDelete)
                    {
                        File path = new File(entry.getFilePath());
                        SnapshotHistoryService.getInstance().removePath(new Path(path.getAbsolutePath()));
                    }
                }
            }

        };
        actionDelete.setText(Messages.SnapshotHistoryView_DeleteFile);
        actionDelete.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                        org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        actionOpenFileInFileSystem = new Action()
        {
            private static final String VARIABLE_RESOURCE = "${selected_resource_loc}"; //$NON-NLS-1$
            private static final String VARIABLE_RESOURCE_URI = "${selected_resource_uri}"; //$NON-NLS-1$
            private static final String VARIABLE_FOLDER = "${selected_resource_parent_loc}"; //$NON-NLS-1$

            @Override
            public void run()
            {
                TableItem[] selection = table.getSelection();

                String filename = ((SnapshotHistoryService.Entry) selection[0].getData()).getFilePath();
                IPath path = new Path(filename);
                File file = path.toFile();

                if (file.exists())
                {
                    try
                    {
                        // snapshot history view contains files only, additional
                        // checks are not needed
                        String launchCmd = formShowInSystemExplorerCommand(file);
                        if ("".equals(launchCmd)) //$NON-NLS-1$
                        {
                            String osName = System.getProperty("os.name","");//$NON-NLS-1$ //$NON-NLS-2$
                            displayMessage(MessageUtil.format(Messages.SnapshotHistoryView_OperationNotImplemented,
                                            osName));
                            return;
                        }
                        Job job = new Job(Messages.SnapshotHistoryView_ExploreFileSystem)
                        {
                            @Override
                            protected IStatus run(IProgressMonitor monitor)
                            {
                                try
                                {
                                    File dir = file.getParentFile();
                                    if (dir != null )
                                        dir = dir.getCanonicalFile();
                                    Process p;
                                    if (Util.isLinux() || Util.isMac())
                                    {
                                        p = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", launchCmd }, null, dir); //$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    else
                                    {
                                        p = Runtime.getRuntime().exec(launchCmd, null, dir);
                                    }
                                    int r = p.waitFor();
                                    if (r != 0)
                                    {
                                        CharBuffer cb = CharBuffer.allocate(4096);
                                        cb.append(launchCmd);
                                        int pos = cb.position();
                                        cb.append('\n');
                                        p.inputReader().read(cb);
                                        cb.append('\n');
                                        p.errorReader().read(cb);
                                        /*
                                         * Windows returns 1 from explorer.exe even when no problem,
                                         * so look for some error text.
                                         */
                                        if (!Util.isWindows() || cb.position() > pos + 2)
                                        {
                                            cb.flip();
                                            return ErrorHelper.createErrorStatus(cb.toString());
                                        }
                                    }
                                }
                                catch (IOException | InterruptedException ex)
                                {
                                    return ErrorHelper.createErrorStatus(ex);
                                }
                                return Status.OK_STATUS;
                            }

                        };
                        job.schedule();
                    }
                    catch (IOException ex)
                    {
                        ErrorHelper.showErrorMessage(ex);
                    }
                }
                else
                {
                    displayMessage(MessageUtil.format(Messages.SnapshotHistoryView_FileDoesNotExist, file
                                    .getAbsolutePath()));
                }
            }

            private void execute(String baseCommand, String osPath, File file)
            {
                try
                {
                    String forceDirectoryPath = osPath;
                    if (file.isFile())
                        forceDirectoryPath = file.getParentFile().getCanonicalPath();

                    Runtime.getRuntime().exec(new String[] { baseCommand, forceDirectoryPath });
                }
                catch (IOException ex)
                {
                    MemoryAnalyserPlugin.log(ex);
                }
            }

            private void displayMessage(String message)
            {
                MessageDialog.openInformation(table.getParent().getShell(),
                                Messages.SnapshotHistoryView_ExploreFileSystem, message);
            }

            /**
             * Prepare command for launching system explorer to show a path
             *
             * @param path
             *            the path to show
             * @return the command that shows the path
             */
            private String formShowInSystemExplorerCommand(File path) throws IOException
            {
                String command = Platform.getPreferencesService().getString("org.eclipse.ui.ide", "SYSTEM_EXPLORER", getShowInSystemExplorerCommand(), //$NON-NLS-1$ //$NON-NLS-2$
                                null);

                command = Util.replaceAll(command, VARIABLE_RESOURCE, quotePath(path.getCanonicalPath()));
                command = Util.replaceAll(command, VARIABLE_RESOURCE_URI, path.getCanonicalFile().toURI().toString());
                File parent = path.getParentFile();
                if (parent != null)
                {
                    command = Util.replaceAll(command, VARIABLE_FOLDER, quotePath(parent.getCanonicalPath()));
                }
                return command;
            }

            private String quotePath(String path)
            {
                if (Util.isLinux() || Util.isMac())
                {
                    // Quote for usage inside "", man sh, topic QUOTING:
                    path = path.replaceAll("[\"$`]", "\\\\$0"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                // Windows: Can't quote, since explorer.exe has a very special
                // command line parsing strategy.
                return path;
            }

            /**
             * The default command for launching the system explorer on this
             * platform.
             *
             * @return The default command which launches the system explorer on
             *         this system, or an empty string if no default exists
             */
            public String getShowInSystemExplorerCommand()
            {
                if (Util.isGtk())
                {
                    return "dbus-send --print-reply --dest=org.freedesktop.FileManager1 /org/freedesktop/FileManager1 org.freedesktop.FileManager1.ShowItems array:string:\"${selected_resource_uri}\" string:\"\""; //$NON-NLS-1$
                }
                else if (Util.isWindows())
                {
                    return "explorer /E,/select=${selected_resource_loc}"; //$NON-NLS-1$
                }
                else if (Util.isMac())
                {
                    return "open -R \"${selected_resource_loc}\""; //$NON-NLS-1$
                }

                // if all else fails, return empty default
                return ""; //$NON-NLS-1$
            }
        };
        actionOpenFileInFileSystem.setText(Messages.SnapshotHistoryView_ExploreInFileSystem);
        actionOpenFileInFileSystem.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.EXPLORE));

        actionDeleteIndeces = new Action(Messages.SnapshotHistoryView_DeleteIndexFiles)
        {
            @Override
            public void run()
            {
                List<File> problems = new ArrayList<File>();

                for (TableItem item : table.getSelection())
                {
                    SnapshotHistoryService.Entry entry = (SnapshotHistoryService.Entry) item.getData();
                    File snapshot = new File(entry.getFilePath());
                    File index = snapshot;
                    if (entry.getInfo() instanceof SnapshotInfo)
                    {
                        // Find the prefix path directly
                        SnapshotInfo ifo = (SnapshotInfo)entry.getInfo();
                        String prefix = ifo.getPrefix();
                        if (prefix != null)
                        {
                            index = new File(prefix + "index"); //$NON-NLS-1$
                            IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                                            .findEditor(new PathEditorInput(new Path(index.getAbsolutePath())));
                            if (editor != null)
                                getSite().getPage().closeEditor(editor, true);
                        }
                    }
                    deleteIndexes(index, problems);
                    if (!index.exists())
                    {
                        item.setFont(italicFont);
                        ImageDescriptor imageDescriptor = ImageDescriptor.createFromImage(item.getImage());
                        imageDescriptor = ImageDescriptor.createWithFlags(imageDescriptor, SWT.IMAGE_DISABLE);
                        item.setImage(MemoryAnalyserPlugin.getDefault().getImage(imageDescriptor));
                        // Perhaps the history was for an index file, or the index file also listed in the history
                        SnapshotHistoryService.getInstance().removePath(new Path(index.getAbsolutePath()));
                    }
                }

                if (!problems.isEmpty())
                {
                    showProblems(problems);
                }
                else
                {
                    MessageBox box = new MessageBox(table.getShell(), SWT.OK | SWT.ICON_INFORMATION);
                    box.setMessage(Messages.SnapshotHistoryView_DeleteIndexFilesSuccess);
                    box.open();
                }
            }
        };
        actionDeleteIndeces.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.REMOVE_ALL));

        actionCopy = new Action()
        {
            public void run()
            {
                StringBuilder selectedItems = new StringBuilder();
                for (TableItem selected : table.getSelection())
                {
                    if (selectedItems.length() > 0)
                    {
                        selectedItems.append(' ');
                    }
                    String path = selected.getText();
                    if (path.indexOf(' ') != -1)
                    {
                        path = '\"' + path + '\"';
                    }
                    selectedItems.append(path);
                }
                if (selectedItems.length() > 0)
                {
                    Copy.copyToClipboard(selectedItems.toString(), table.getDisplay());
                }
            }
        };
        actionCopy.setText(Messages.SnapshotHistoryView_CopyFilename);
        actionCopy.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.COPY));
        getViewSite().getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), actionCopy);
        // No paste action, try to avoid org.eclipse.core.commands.NotHandledException
        getViewSite().getActionBars().setGlobalActionHandler(ActionFactory.PASTE.getId(), null);
        getViewSite().getActionBars().updateActionBars();
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                editorContextMenuAboutToShow(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(table);
        this.table.setMenu(menu);
    }

    private void editorContextMenuAboutToShow(IMenuManager manager)
    {
        TableItem[] selection = table.getSelection();
        if (selection.length == 0)
            return;

        actionOpen.setEnabled(selection.length == 1);
        manager.add(actionOpen);

        actionOpenFileInFileSystem.setEnabled(selection.length == 1);
        manager.add(actionOpenFileInFileSystem);

        manager.add(actionCopy);

        manager.add(actionDelete);
        manager.add(actionDeleteIndeces);
    }

    @Override
    public void dispose()
    {
        super.dispose();
        SnapshotHistoryService.getInstance().removeChangeListener(this);
    }

    @Override
    public void setFocus()
    {
        table.setFocus();
    }

    @Override
    public <T> T getAdapter(Class<T> required)
    {
        if (IContentOutlinePage.class.equals(required))
            return required.cast(new Outline());
        return super.getAdapter(required);
    }

    public void onFileHistoryChange(final List<Entry> visited)
    {
        if (this.table.isDisposed())
            return;

        this.table.getDisplay().syncExec(new Runnable()
        {
            public void run()
            {
                table.removeAll();
                fillTable();
            }
        });
    }

    private void openFile(TableItem[] selection)
    {
        if (selection.length == 1)
        {
            SnapshotHistoryService.Entry entry = (SnapshotHistoryService.Entry) selection[0].getData();
            Path path = new Path(entry.getFilePath());
            Serializable ss = entry.getInfo();
            if (ss instanceof SnapshotInfo)
            {
                SnapshotInfo info = (SnapshotInfo) ss;
                if (info.getProperty("$runtimeId") != null) //$NON-NLS-1$
                {
                    String prefix = info.getPrefix();
                    String index = prefix + "index"; //$NON-NLS-1$
                    Path path2 = new Path(index);
                    if (path2.toFile().exists())
                        path = path2;
                }
            }

            if (path.toFile().exists())
            {
                try
                {
                    IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                                    new PathEditorInput(path), entry.getEditorId(), true);
                }
                catch (PartInitException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
            {
                MessageDialog.openError(this.table.getParent().getShell(),
                                Messages.SnapshotHistoryView_FileDoesNotExistAnymore, MessageUtil.format(
                                                Messages.SnapshotHistoryView_SelectedFileDoesNotExist, path
                                                                .toOSString()));
                SnapshotHistoryService.getInstance().removePath(path);
            }
        }
    }

    private void deleteIndexes(File snapshot, List<File> problems)
    {
        File directory = snapshot.getParentFile();
        String name = snapshot.getName();

        int lastDot = name.lastIndexOf('.');
        final String prefix = lastDot >= 0 ? name.substring(0, lastDot) : name;
        // Delete threads file as well as indexes
        final Pattern pattern = Pattern.compile("\\.(([A-Za-z0-9]{1,20}\\.)?index|threads)$");//$NON-NLS-1$

        String[] indexFiles = directory.list(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.startsWith(prefix) && pattern.matcher(name.substring(prefix.length())).matches();
            }
        });

        if (indexFiles != null)
        {
            for (String indexFile : indexFiles)
            {
                File f = new File(directory, indexFile);
                if (f.exists())
                    if (!f.delete() && problems != null)
                        problems.add(f);
            }
        }
    }

    private void showProblems(List<File> problems)
    {
        StringBuilder msg = new StringBuilder();
        msg.append(Messages.SnapshotHistoryView_ErrorDeletingFiles);
        for (File f : problems)
            msg.append("\n\t").append(f.getAbsolutePath());//$NON-NLS-1$

        MessageBox box = new MessageBox(table.getShell(), SWT.OK | SWT.ICON_ERROR);
        box.setMessage(msg.toString());
        box.open();
    }
}
