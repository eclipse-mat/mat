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
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.ui.SnapshotHistoryService.Entry;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.snapshot.views.SnapshotOutlinePage;
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
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
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
            super(parentShell, //
                            "Confirm Deletion", //
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
            deleteRadio.setText("Also delete in file system (including index files)");
            deleteRadio.setFont(parent.getFont());

            Button radio = new Button(composite, SWT.RADIO);
            radio.addSelectionListener(selectionListener);
            radio.setText("Delete only from history");
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

    @Override
    public void createPartControl(Composite parent)
    {
        this.table = new Table(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        TableColumn tableColumn = new TableColumn(table, SWT.LEFT);
        tableColumn.setText("Recently Used Files"); //$NON-NLS-1$
        tableColumn.setWidth(400);
        table.setHeaderVisible(true);

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

            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            tableItem.setImage(MemoryAnalyserPlugin.getDefault().getImage(
                            registry.getImageDescriptor(entry.getFilePath())));
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
        actionOpen.setText("Open");
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
        actionRemoveFromList.setText("Remove from List");
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
                List<File> toDelete = new ArrayList<File>(selection.length);

                for (TableItem item : selection)
                    toDelete.add(new File(((SnapshotHistoryService.Entry) item.getData()).getFilePath()));

                String dialogTitle;
                if (toDelete.size() > 1)
                    dialogTitle = MessageFormat.format("Are you sure you want to delete these {0,number} file?",
                                    toDelete.size());
                else
                    dialogTitle = MessageFormat.format("Are you sure you want to delete ''{0}''?", //
                                    toDelete.get(0).getAbsolutePath());

                DeleteSnapshotDialog deleteSnapshotDialog = new DeleteSnapshotDialog(table.getShell(), dialogTitle);

                int response = deleteSnapshotDialog.open();
                if (response != IDialogConstants.OK_ID)
                    return;

                for (File path : toDelete)
                    SnapshotHistoryService.getInstance().removePath(new Path(path.getAbsolutePath()));

                if (deleteSnapshotDialog.getDeleteInFileSystem())
                {
                    for (File path : toDelete)
                    {
                        IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                                        .findEditor(new PathEditorInput(new Path(path.getAbsolutePath())));
                        if (editor != null)
                            getSite().getPage().closeEditor(editor, true);

                        path.delete();
                        deleteIndexes(path, null);
                    }
                }
            }

        };
        actionDelete.setText("Delete File");
        actionDelete.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                        org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        actionOpenFileInFileSystem = new Action()
        {

            @Override
            public void run()
            {
                TableItem[] selection = table.getSelection();

                String filename = ((SnapshotHistoryService.Entry) selection[0].getData()).getFilePath();
                IPath path = new Path(filename);
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
                        execute("open", osPath, file);
                    }
                    else if (osName.indexOf("linux") != -1)
                    {
                        String desktop = System.getProperty("sun.desktop");
                        desktop = desktop == null ? "" : desktop.toLowerCase();

                        if (desktop.indexOf("gnome") != -1)
                        {
                            execute("gnome-open", osPath, file);
                        }
                        else if (desktop.indexOf("konqueror") != -1 || desktop.indexOf("kde") != -1)
                        {
                            execute("konqueror", osPath, file);
                        }
                        else
                        {
                            displayMessage(MessageFormat.format("I do not know how to open the file manager"
                                            + " for your Linux desktop ''{0}''. I will try to use gnome", desktop));
                            execute("gnome-open", osPath, file);
                        }
                    }
                    else
                    {
                        displayMessage(MessageFormat.format("Sorry, operation not implementation for OS: {0}", osName));
                    }
                }
                else
                {
                    displayMessage(MessageFormat.format("File {0} does not exist (anymore).", file.getAbsolutePath()));
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
                MessageDialog.openInformation(table.getParent().getShell(), "Explore File System", message);
            }
        };
        actionOpenFileInFileSystem.setText("Explore in File System");
        actionOpenFileInFileSystem.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(ISharedImages.EXPLORE));

        actionDeleteIndeces = new Action("Delete Index Files")
        {
            @Override
            public void run()
            {
                List<File> problems = new ArrayList<File>();

                for (TableItem item : table.getSelection())
                {
                    File snapshot = new File(((SnapshotHistoryService.Entry) item.getData()).getFilePath());
                    deleteIndexes(snapshot, problems);
                }

                if (!problems.isEmpty())
                {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Error deleting the following files:");
                    for (File f : problems)
                        msg.append("\n\t").append(f.getAbsolutePath());

                    MessageBox box = new MessageBox(table.getShell(), SWT.OK | SWT.ICON_ERROR);
                    box.setMessage(msg.toString());
                    box.open();
                }
            }
        };
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

    @SuppressWarnings("unchecked")
    @Override
    public Object getAdapter(Class required)
    {
        if (IContentOutlinePage.class.equals(required))
            return new Outline();
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
                for (SnapshotHistoryService.Entry entry : visited)
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

    private void openFile(TableItem[] selection)
    {
        if (selection.length == 1)
        {
            SnapshotHistoryService.Entry entry = (SnapshotHistoryService.Entry) selection[0].getData();
            Path path = new Path(entry.getFilePath());

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
                MessageDialog.openError(this.table.getParent().getShell(), "File does not exist anymore", MessageFormat
                                .format("The selected file does not exist anymore. "
                                                + "It will be deleted from the history list."
                                                + "\n\nSelected File:\n{0}", path.toOSString()));
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
        final Pattern pattern = Pattern.compile("\\.(.*\\.)?index$");

        String[] indexFiles = directory.list(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.startsWith(prefix) && pattern.matcher(name.substring(prefix.length())).matches();
            }
        });

        for (String indexFile : indexFiles)
        {
            File f = new File(directory, indexFile);
            if (f.exists())
                if (!f.delete() && problems != null)
                    problems.add(f);
        }
    }
}
