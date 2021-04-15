/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - remove FindBugs warnings
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.actions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.help.ILiveHelpAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.mat.ui.snapshot.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.util.MessageUtil;
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
            String absolutePath = getPathOfExtractedHeapDump(params[0]);

            String query = params[1];
            if (query != null && query.equals("oql"))//$NON-NLS-1$
            {
                query = query + " \"" + params[2] + "\"";//$NON-NLS-1$//$NON-NLS-2$
            }
            openEditor(absolutePath, query, params[2]);
            notifyResult(true);
        }
        catch (PartInitException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
            notifyResult(false);
        }
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
            if (PlatformUI.getWorkbench().getIntroManager().getIntro() != null)
            {
                // if this action was called with open welcome page - set it to
                // standby mode.
                PlatformUI.getWorkbench().getIntroManager().setIntroStandby(
                                PlatformUI.getWorkbench().getIntroManager().getIntro(), true);
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
                    String extractedPath = getPathOfExtractedHeapDump(heapDumpLocation);
                    doOpenEditor(window, extractedPath);
                }
                catch (PartInitException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }
            }

        });
    }

    private String getPathOfExtractedHeapDump(String snapshotPath)
    {
        int p = snapshotPath.indexOf('/');
        String pluginId = snapshotPath.substring(0, p);
        String path = snapshotPath.substring(p + 1);

        int lastIndexOf = snapshotPath.lastIndexOf('/');
        String localPath = snapshotPath.substring(0, lastIndexOf);
        String fileName = snapshotPath.substring(lastIndexOf + 1);

        IPath pluginPath = MemoryAnalyserPlugin.getDefault().getStateLocation();
        File targetDir = pluginPath.append(localPath).toFile();
        File extractedFile = new File(targetDir, fileName);
        try
        {
            if (!extractedFile.getCanonicalPath().startsWith(pluginPath.toFile().getCanonicalPath() + File.separator))
            {
                throw new IllegalArgumentException(snapshotPath);
            }
            if (extractedFile.exists())
                return extractedFile.getAbsolutePath();

            if (!targetDir.mkdirs() && !targetDir.isDirectory())
            {
                throw new FileNotFoundException(targetDir.getPath());
            }

            Bundle bundle = Platform.getBundle(pluginId);
            URL url = bundle.getResource(path);
            if (url == null) throw new FileNotFoundException(path);
            InputStream in = url.openStream();
            try
            {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(extractedFile));
                try
                {
                    byte[] buffer = new byte[2048];
                    for (;;)
                    {
                        int nBytes = in.read(buffer);
                        if (nBytes <= 0)
                            break;
                        out.write(buffer, 0, nBytes);
                    }
                    out.flush();
                }
                catch (IOException e)
                {
                    ErrorHelper.logThrowableAndShowMessage(e);
                }
                finally
                {
                    out.close();
                }
            }
            finally
            {
                in.close();
            }
        }
        catch (IOException e)
        {
            ErrorHelper.logThrowableAndShowMessage(e);
        }

        return extractedFile.getAbsolutePath();
    }

    public void setInitializationString(String data)
    {
        this.heapDumpLocation = data;
    }

    private static IEditorPart doOpenEditor(IWorkbenchWindow window, String absolutePath) throws PartInitException
    {
        final IPath path = new Path(absolutePath);
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IFileStore fileStore = EFS.getLocalFileSystem().getStore(path);
        IFile[] heapFile= root.findFilesForLocationURI(fileStore.toURI());

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
                                Messages.OpenSampleHeapDumpAction_ErrorOpeningEditor, MessageUtil.format(
                                                Messages.OpenSampleHeapDumpAction_NoEditorToOpen,
                                                new Object[] { absolutePath }));
            }
        }
        return null;
    }

    public static class Handler extends AbstractHandler
    {

        public Handler()
        {}

        public Object execute(ExecutionEvent executionEvent) throws ExecutionException
        {
            OpenSampleHeapDumpAction act = new OpenSampleHeapDumpAction();
            String param1 = executionEvent.getParameter("org.eclipse.mat.ui.actions.openSampleHeapDump.heapDump"); //$NON-NLS-1$
            String param2 = executionEvent.getParameter("org.eclipse.mat.ui.actions.openSampleHeapDump.command"); //$NON-NLS-1$
            String param3 = executionEvent.getParameter("org.eclipse.mat.ui.actions.openSampleHeapDump.oqlParms"); //$NON-NLS-1$
            act.run(new String[] {param1, param2, param3}, null);
            return null;
        }
    }
}
