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
package org.eclipse.mat.ui.internal.views;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.ui.SnapshotHistoryService.Entry;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;


public class SnapshotHistoryView extends ViewPart implements
                org.eclipse.mat.ui.SnapshotHistoryService.IChangeListener
{

    static class DeleteSnapshotDialog extends MessageDialog
    {
        private boolean deleteInFileSystem = false;

        /**
         * Control testing mode. In testing mode, it returns true to delete
         * contents and does not pop up the dialog.
         */
        private boolean fIsTesting = false;

        private Button radio1;

        private Button radio2;

        public DeleteSnapshotDialog(Shell parentShell, Path[] paths)
        {
            super(parentShell, "Confirm Deletion of Heap Dump", null, // accept the
                            // default window
                            // icon
                            getMessage(paths), MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL,
                                            IDialogConstants.NO_LABEL }, 0); // yes
        }

        private static String getMessage(Path[] paths)
        {
            String message;
            if (paths.length == 1)
            {
                message = "Are you sure you want to delete heap dump '" + paths[0].toOSString() + "'?";
            }
            else if (paths.length > 1)
            {
                message = "Are you sure you want to delete these " + paths.length + " heap dumps?";
            }
            else
            {
                message = "";
            }
            return message;
        }

        protected Control createCustomArea(Composite parent)
        {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout());
            radio1 = new Button(composite, SWT.RADIO);
            radio1.addSelectionListener(selectionListener);
            String text1;

            text1 = "Also delete in file system (including index files)";

            radio1.setText(text1);
            radio1.setFont(parent.getFont());

            radio2 = new Button(composite, SWT.RADIO);
            radio2.addSelectionListener(selectionListener);
            String text2 = "Delete only from history";
            radio2.setText(text2);
            radio2.setFont(parent.getFont());

            // set initial state
            radio1.setSelection(deleteInFileSystem);
            radio2.setSelection(!deleteInFileSystem);

            return composite;
        }

        private SelectionListener selectionListener = new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                Button button = (Button) e.widget;
                if (button.getSelection())
                {
                    deleteInFileSystem = (button == radio1);
                }
            }
        };

        boolean getDeleteInFileSystem()
        {
            return deleteInFileSystem;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.jface.window.Window#open()
         */
        public int open()
        {
            // Override Window#open() to allow for non-interactive testing.
            if (fIsTesting)
            {
                deleteInFileSystem = true;
                return Window.OK;
            }
            return super.open();
        }

        /**
         * Set this delete dialog into testing mode. It won't pop up, and it
         * returns true for deleteContent.
         * 
         * @param t
         *            the testing mode
         */
        void setTestingMode(boolean t)
        {
            fIsTesting = t;
        }
    }

    private Table table;

    private Action actionOpenHeapDump;

    private Action actionRemoveHeapDumpFromHistory;

    private Action actionDeleteHeapDump;

    private Action actionOpenHeapDumpInFileSystem;

    public SnapshotHistoryView()
    {
        super();

    }

    @Override
    public void createPartControl(Composite parent)
    {
        this.table = new Table(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        TableColumn tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setText("Visited Heap Dumps"); //$NON-NLS-1$
        tableColumn.setWidth(400);
        table.setHeaderVisible(true);

        table.addMouseListener(new MouseAdapter()
        {

            @Override
            public void mouseDoubleClick(MouseEvent event)
            {
                TableItem[] selection = (TableItem[]) table.getSelection();
                openHeapDump(selection);
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
                    actionRemoveHeapDumpFromHistory.run();
                }
            }
        });

        SnapshotHistoryService.getInstance().addChangeListener(this);
    }

    private void fillTable()
    {
        List<SnapshotHistoryService.Entry> lastHeaps = SnapshotHistoryService.getInstance().getVisitedEntries();

        for (SnapshotHistoryService.Entry entry : lastHeaps)
        {
            TableItem tableItem = new TableItem(table, 0);
            tableItem.setText(entry.getFilePath());
            tableItem.setData(entry);

            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            tableItem.setImage(MemoryAnalyserPlugin.getDefault().getImage(
                            registry.getImageDescriptor(entry.getFilePath())));
        }
    }

    private void makeActions()
    {
        actionOpenHeapDump = new Action()
        {
            public void run()
            {
                TableItem[] selection = (TableItem[]) table.getSelection();
                openHeapDump(selection);
            }
        };
        actionOpenHeapDump.setText("Open Heap Dump");
        actionOpenHeapDump.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.OPEN_SNAPSHOT));

        actionRemoveHeapDumpFromHistory = new Action()
        {
            public void run()
            {
                TableItem[] selection = (TableItem[]) table.getSelection();

                // as table items are disposed, copy the path before
                List<Path> toDelete = new ArrayList<Path>(selection.length);

                for (TableItem item : selection)
                    toDelete.add(new Path(((SnapshotHistoryService.Entry) item.getData()).getFilePath()));

                for (Path path : toDelete)
                    SnapshotHistoryService.getInstance().removePath(path);
            }
        };
        actionRemoveHeapDumpFromHistory.setText("Remove from History");
        actionRemoveHeapDumpFromHistory.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
                        .getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        actionDeleteHeapDump = new Action()
        {

            @Override
            public void run()
            {
                TableItem[] selection = (TableItem[]) table.getSelection();

                // as table items are disposed, copy the path before
                List<Path> toDelete = new ArrayList<Path>(selection.length);

                for (TableItem item : selection)
                    toDelete.add(new Path(((SnapshotHistoryService.Entry) item.getData()).getFilePath()));

                DeleteSnapshotDialog deleteSnapshotDialog = new DeleteSnapshotDialog(table.getShell(), toDelete
                                .toArray(new Path[toDelete.size()]));
                // MessageBox messageBox = new MessageBox(table.getShell(),
                // SWT.ICON_QUESTION | SWT.YES | SWT.NO);
                //
                // if (toDelete.size() == 1)
                // {
                // String fileName = new Path(((SnapshotHistoryService.Entry)
                // selection[0].getData()).getFilePath())
                // .lastSegment();
                // messageBox.setMessage("Do you really want to delete " +
                // fileName);
                // }
                // else if (toDelete.size() > 1)
                // messageBox.setMessage("Do you really want to delete these " +
                // toDelete.size() + " heap dumps?");
                // messageBox.setText("Confirm Delete");
                // int response = messageBox.open();

                int response = deleteSnapshotDialog.open();

                if (response == IDialogConstants.OK_ID)
                {
                    if (deleteSnapshotDialog.getDeleteInFileSystem())
                    {
                        for (Path path : toDelete)
                        {
                            getSite().getPage().closeEditor(
                                            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                                                            .findEditor(new PathEditorInput(path)), false);
                            File file = path.toFile();
                            file.delete();
                            SnapshotHistoryService.getInstance().removePath(path);
                            deleteIndexes(path.removeFileExtension());
                        }
                    }
                    else
                    {
                        for (Path path : toDelete)
                            SnapshotHistoryService.getInstance().removePath(path);
                    }
                }
            }

        };
        actionDeleteHeapDump.setText("Delete Heap Dump");
        actionDeleteHeapDump.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                        org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        actionOpenHeapDumpInFileSystem = new Action()
        {

            @Override
            public void run()
            {
                TableItem[] selection = (TableItem[]) table.getSelection();

                String heapDumpPathString = ((SnapshotHistoryService.Entry) selection[0].getData()).getFilePath();
                IPath path = new Path(heapDumpPathString);
                File file = path.toFile();

                if (file.exists())
                {
                    // snapshot history view contains files only, additional
                    // checks are not needed
                    String osPath = path.toOSString();
                    String osName = System.getProperty("os.name").toLowerCase();
                    if (osName.indexOf("windows") != -1)
                    {
                        String command = "explorer.exe /SELECT," + "\"" + osPath + "\"";
                        try
                        {
                            Runtime.getRuntime().exec(command);
                        }
                        catch (IOException ex)
                        {
                            ErrorHelper.showErrorMessage(ex);
                        }
                    }
                    else if (osName.indexOf("mac") != -1)
                    {
                        executeCommandForceDir("open", osPath, file);
                    }
                    else if (osName.indexOf("linux") != -1)
                    {
                        String desktop = System.getProperty("sun.desktop");
                        if (desktop == null)
                        {
                            desktop = "";
                        }
                        desktop = desktop.toLowerCase();
                        if (desktop.indexOf("gnome") != -1)
                        {
                            executeCommandForceDir("gnome-open", osPath, file);
                        }
                        else if (desktop.indexOf("konqueror") != -1 || desktop.indexOf("kde") != -1)
                        {
                            executeCommandForceDir("konqueror", osPath, file);
                        }
                        else
                        {
                            displayMessage("Sorry, I do not know how to open the file manager for your Linux desktop \""
                                            + desktop + "\".\n" + "I will try to use konqueror.");
                            executeCommandForceDir("konqueror", osPath, file);
                        }
                    }
                    else
                    {
                        displayMessage("Sorry, this action cannot be accomplished for operating system: " + osName);
                    }
                }
                else
                {
                    // should not happened, as history contains only existing
                    // files
                }
            }

            private void executeCommandForceDir(String baseCommand, String osPath, File file)
            {
                String forceDirectoryPath = osPath;
                if (file.isFile())
                {
                    try
                    {
                        forceDirectoryPath = file.getParentFile().getCanonicalPath();
                    }
                    catch (IOException ex)
                    {
                        MemoryAnalyserPlugin.log(ex);
                    }
                }
                String args[] = { baseCommand, forceDirectoryPath };
                try
                {
                    Runtime.getRuntime().exec(args);
                }
                catch (IOException ex)
                {
                    MemoryAnalyserPlugin.log(ex);
                }
            }

            private void displayMessage(String message)
            {
                MessageDialog.openInformation(table.getParent().getShell(), "Explore File System", message);
            }
        };
        actionOpenHeapDumpInFileSystem.setText("Explore in File System");        
        actionOpenHeapDumpInFileSystem.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(ISharedImages.EXPLORE));

    }

    private void deleteIndexes(IPath path)
    {
        checkIndex(path.addFileExtension("a2s.index"));
        checkIndex(path.addFileExtension("domIn.index"));
        checkIndex(path.addFileExtension("domOut.index"));
        checkIndex(path.addFileExtension("i2s.index"));
        checkIndex(path.addFileExtension("idx.index"));
        checkIndex(path.addFileExtension("inbound.index"));
        checkIndex(path.addFileExtension("index"));
        checkIndex(path.addFileExtension("notes.txt"));
        checkIndex(path.addFileExtension("o2c.index"));
        checkIndex(path.addFileExtension("o2p.index"));
        checkIndex(path.addFileExtension("o2ret.index"));
        checkIndex(path.addFileExtension("outbound.index"));
    }

    private void checkIndex(IPath path)
    {
        File indexFile = path.toFile();
        if (indexFile.exists())
            indexFile.delete();
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
        TableItem[] selection = (TableItem[]) table.getSelection();

        if (selection.length >= 1)
        {
            manager.add(actionOpenHeapDump);
            manager.add(actionOpenHeapDumpInFileSystem);            
            manager.add(actionDeleteHeapDump);
        }

        if (selection.length == 1)
        {
            actionOpenHeapDump.setEnabled(true);
            actionOpenHeapDumpInFileSystem.setEnabled(true); 
            }
        else if (selection.length > 1)
        {
            actionOpenHeapDump.setEnabled(false);            
        }
    }

    @Override
    public void dispose()
    {
        super.dispose();

        SnapshotHistoryService.getInstance().removeChangeListener(this);
    }

    class Outline extends SnapshotOutlinePage implements SelectionListener
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

    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getAdapter(Class required)
    {
        if (IContentOutlinePage.class.equals(required)) { return new Outline(); }
        return super.getAdapter(required);
    }

    @Override
    public void setFocus()
    {

    }

    public void onSnapshotsChanged(final List<Entry> visitedSnapshots)
    {
        if (this.table.isDisposed())
            return;

        this.table.getDisplay().syncExec(new Runnable()
        {
            public void run()
            {
                table.removeAll();
                for (SnapshotHistoryService.Entry entry : visitedSnapshots)
                {
                    TableItem item = new TableItem(table, 0);
                    item.setText(entry.getFilePath());
                    item.setData(entry);
                    IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
                    item.setImage(MemoryAnalyserPlugin.getDefault().getImage(
                                    registry.getImageDescriptor(entry.getFilePath())));
                }
            }
        });
    }

    private void openHeapDump(TableItem[] selection)
    {
        if (selection.length == 1)
        {
            String osPath = ((SnapshotHistoryService.Entry) selection[0].getData()).getFilePath();
            Path path = new Path(osPath);
            // IFile[] heapFile =
            // ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(path);

            if (path.toFile().exists())
            {
                try
                {
                    IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
                    IEditorDescriptor descriptor = registry.getDefaultEditor(osPath);
                    IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                                    new PathEditorInput(path), descriptor.getId(), true);
                }
                catch (PartInitException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
            {
                MessageDialog.openError(this.table.getParent().getShell(), "Heap Dump does not exist anymore",
                                "The selected heap dump does not exist anymore. It will be deleted from the history list.\n\nSelected Heap Dump:\n"
                                                + path.toOSString());
                SnapshotHistoryService.getInstance().removePath(path);
            }
        }
    }
}
