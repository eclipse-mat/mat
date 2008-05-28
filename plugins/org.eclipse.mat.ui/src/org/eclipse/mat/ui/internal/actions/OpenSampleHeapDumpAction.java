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
package org.eclipse.mat.ui.internal.actions;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.help.ILiveHelpAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.cheatsheets.ICheatSheetAction;
import org.eclipse.ui.cheatsheets.ICheatSheetManager;
import org.eclipse.ui.ide.IDE;
import org.osgi.framework.Bundle;

public class OpenSampleHeapDumpAction extends Action implements ICheatSheetAction, ILiveHelpAction
{

    private String heapDumpLocation;

    public void run(String[] params, ICheatSheetManager manager)
    {
        try
        {
            String absolutePath = getSnapshotPath(params[0]);

            String query = params[1];
            if (query != null && query.equals("oql"))
            {
                query = query + " \"" + params[2] + "\"";
            }
            openEditor(absolutePath, query, params[2]);
        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }
        catch (IOException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }

    }

    private String getSnapshotPath(String snapshotPath) throws IOException
    {
        int p = snapshotPath.indexOf('/');
        String pluginId = snapshotPath.substring(0, p);
        String path = snapshotPath.substring(p + 1);

        Bundle bundle = Platform.getBundle(pluginId);
        URL url = FileLocator.find(bundle, new Path(path), null);
        // convert to absolute path here. Otherwise eclipse will interpret
        // it as relative to projects
        String absolutePath = (new File(FileLocator.resolve(url).getFile())).getAbsolutePath();
        return absolutePath;
    }

    public static final void openEditor(final String snapshotPath, final String editorId, final Object arguments)
                    throws PartInitException
    {

        IEditorPart editorPart = doOpenEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow(), snapshotPath);

        if (editorId != null && editorPart instanceof HeapEditor)
        {
            IEditorInput editorInput = ((HeapEditor) editorPart).getPaneEditorInput();

            if (editorInput instanceof ISnapshotEditorInput && !((ISnapshotEditorInput) editorInput).hasSnapshot())
            {
                // if the editor is not yet open (i.e. action is invoked
                // via context menu from the console), addNewPage should
                // be called _after_ snapshot is loaded. This is
                // necessary, because most pane implementations rely on
                // a loaded snapshot

                final ISnapshotEditorInput snapshotInput = (ISnapshotEditorInput) editorInput;
                final MultiPaneEditor finalEditorPart = (MultiPaneEditor) editorPart;

                snapshotInput.addChangeListener(new ISnapshotEditorInput.IChangeListener()
                {

                    public void onBaselineLoaded(ISnapshot snapshot)
                    {}

                    public void onSnapshotLoaded(ISnapshot snapshot)
                    {
                        finalEditorPart.addNewPage(editorId, arguments);
                        snapshotInput.removeChangeListener(this);
                    }

                });
            }
            else
            {
                try
                {
                    HeapEditor heapEditor = (HeapEditor) editorPart;
                    QueryExecution.executeCommandLine(heapEditor, null, editorId);
                }
                catch (SnapshotException exp)
                {
                    ErrorHelper.logThrowableAndShowMessage(exp);
                }
            }

        }

    }

    @Override
    public void run()
    {
        IWorkbench wb = PlatformUI.getWorkbench();
        final IWorkbenchWindow window = (wb.getActiveWorkbenchWindow() == null) ? wb.getWorkbenchWindows()[0] : wb
                        .getActiveWorkbenchWindow();
        if (window == null)
            return;
        Display display = window.getShell().getDisplay();
        if (display == null)
            return;

        // Active help does not run in the SWT thread.
        // Therefore we must encapsulate all GUI accesses into
        // a syncExec() method.
        display.syncExec(new Runnable()
        {
            public void run()
            {
                // Bring the workbench window into the foreground
                Shell shell = window.getShell();
                shell.setMinimized(false);
                shell.forceActive();

                try
                {
                    String absolutePath = getSnapshotPath(heapDumpLocation);
                    doOpenEditor(window, absolutePath);
                }
                catch (IOException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }
                catch (PartInitException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }
            }
        });
    }

    public void setInitializationString(String data)
    {
        this.heapDumpLocation = data;
    }

    private static IEditorPart doOpenEditor(IWorkbenchWindow window, String absolutePath) throws PartInitException
    {
        final IPath path = new Path(absolutePath);
        IFile[] heapFile = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(path);

        if (heapFile != null && heapFile.length > 0)
        {
            return IDE.openEditor(window.getActivePage(), heapFile[0], true);
        }
        else
        {
            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            IEditorDescriptor descriptor = registry.getDefaultEditor(absolutePath);
            if (descriptor != null)
            {
                return IDE.openEditor(window.getActivePage(), new PathEditorInput(path), descriptor.getId(), true);
            }
            else
            {
                MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                "Error opening editor", MessageFormat.format("No editor available to open {0}",
                                                new Object[] { absolutePath }));
            }
        }
        return null;
    }
}
