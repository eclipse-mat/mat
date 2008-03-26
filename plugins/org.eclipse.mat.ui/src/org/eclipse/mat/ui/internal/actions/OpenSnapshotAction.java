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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.internal.OpenSnapshot;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.part.FileEditorInput;


public class OpenSnapshotAction extends Action implements IWorkbenchWindowActionDelegate
{
    private IWorkbenchWindow fWindow;

    public OpenSnapshotAction()
    {}


    public void run(IAction action)
    {
        run();
    }

    public void run()
    {
        boolean successful = new OpenSnapshot.Visitor()
        {

            @Override
            public void visit(IFileStore fileStore)
            {
                IEditorInput input = createEditorInput(fileStore);
                String editorId = getEditorId(fileStore);
                IWorkbenchPage page = fWindow.getActivePage();
                try
                {
                    page.openEditor(input, editorId);
                }
                catch (PartInitException e)
                {
                    MemoryAnalyserPlugin.log(e.getStatus());
                    String msg = MessageFormat.format("Error opening file {0}", fileStore.getName());
                    MessageDialog.openError(fWindow.getShell(), "Internal Error", msg);
                }
            }

        }.go(getWindow().getShell());

        if (successful && PlatformUI.getWorkbench().getIntroManager().getIntro() != null)
        {
            // if this action was called with open welcome page - set it to
            // standby mode.
            PlatformUI.getWorkbench().getIntroManager().setIntroStandby(
                            PlatformUI.getWorkbench().getIntroManager().getIntro(), true);
        }

    }

    static class FileLabelProvider extends LabelProvider
    {
        /*
         * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
         */
        public String getText(Object element)
        {
            if (element instanceof IFile)
            {
                IPath path = ((IFile) element).getFullPath();
                return path != null ? path.toString() : ""; //$NON-NLS-1$
            }
            return super.getText(element);
        }
    }

    public void dispose()
    {
        fWindow = null;
    }

    public void init(IWorkbenchWindow window)
    {
        fWindow = window;
    }

    public void selectionChanged(IAction action, ISelection selection)
    {}

    // //////////////////////////////////////////////////////////////
    // internal helper methods
    // //////////////////////////////////////////////////////////////
    

    private IWorkbenchWindow getWindow()
    {
        IWorkbench workbench = PlatformUI.getWorkbench();
        fWindow = workbench.getActiveWorkbenchWindow();
        return fWindow;
    }

    // //////////////////////////////////////////////////////////////
    // helpers to actually correctly open the editor window
    // //////////////////////////////////////////////////////////////

    private String getEditorId(IFileStore file)
    {
        IWorkbench workbench = fWindow.getWorkbench();
        IEditorRegistry editorRegistry = workbench.getEditorRegistry();
        IEditorDescriptor descriptor = editorRegistry.getDefaultEditor(file.getName(), getContentType(file));

        // check the OS for in-place editor (OLE on Win32)
        if (descriptor == null && editorRegistry.isSystemInPlaceEditorAvailable(file.getName()))
            descriptor = editorRegistry.findEditor(IEditorRegistry.SYSTEM_INPLACE_EDITOR_ID);

        // check the OS for external editor
        if (descriptor == null && editorRegistry.isSystemExternalEditorAvailable(file.getName()))
            descriptor = editorRegistry.findEditor(IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID);

        if (descriptor != null)
            return descriptor.getId();

        return EditorsUI.DEFAULT_TEXT_EDITOR_ID;
    }

    private IContentType getContentType(IFileStore fileStore)
    {
        if (fileStore == null)
            return null;

        InputStream stream = null;
        try
        {
            stream = fileStore.openInputStream(EFS.NONE, null);
            return Platform.getContentTypeManager().findContentTypeFor(stream, fileStore.getName());
        }
        catch (IOException x)
        {
            MemoryAnalyserPlugin.log(x);
            return null;
        }
        catch (CoreException x)
        {
            // Do not log FileNotFoundException (no access)
            if (!(x.getStatus().getException() instanceof FileNotFoundException))
                MemoryAnalyserPlugin.log(x);

            return null;
        }
        finally
        {
            try
            {
                if (stream != null)
                    stream.close();
            }
            catch (IOException x)
            {
                MemoryAnalyserPlugin.log(x);
            }
        }
    }

    private IEditorInput createEditorInput(IFileStore fileStore)
    {
        try
        {
            IFile workspaceFile = getWorkspaceFile(fileStore);
            if (workspaceFile != null)
                return new FileEditorInput(workspaceFile);
            return new PathEditorInput(new Path(fileStore.toLocalFile(EFS.NONE, new NullProgressMonitor())
                            .getAbsolutePath()));
        }
        catch (CoreException e)
        {
            throw new RuntimeException(e);
        }
    }

    private IFile getWorkspaceFile(IFileStore fileStore)
    {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IFile[] files = workspace.getRoot().findFilesForLocation(new Path(fileStore.toURI().getPath()));
        files = filterNonExistentFiles(files);
        if (files == null || files.length == 0)
            return null;
        if (files.length == 1)
            return files[0];
        return selectWorkspaceFile(files);
    }

    private IFile[] filterNonExistentFiles(IFile[] files)
    {
        if (files == null)
            return null;

        int length = files.length;
        ArrayList<IFile> existentFiles = new ArrayList<IFile>(length);
        for (int i = 0; i < length; i++)
        {
            if (files[i].exists())
                existentFiles.add(files[i]);
        }
        return existentFiles.toArray(new IFile[existentFiles.size()]);
    }

    private IFile selectWorkspaceFile(IFile[] files)
    {
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(fWindow.getShell(), new FileLabelProvider());
        dialog.setElements(files);
        dialog.setTitle("Select workspace");
        dialog.setMessage("The selected file is referenced by multiple linked resources in the workspace.\n"
                        + "Select a workspace resource to open the file.");
        if (dialog.open() == Window.OK)
            return (IFile) dialog.getFirstResult();
        return null;
    }

}
