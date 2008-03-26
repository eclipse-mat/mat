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
package org.eclipse.mat.ui.internal.query;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.impl.query.IArgumentContextProvider;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.ide.ResourceUtil;


public class ArgumentContextProvider implements IArgumentContextProvider
{
    HeapEditor editor;
    
    public ArgumentContextProvider(HeapEditor editor)
    {
        this.editor = editor;
    }

    public ISnapshot getPrimarySnapshot()
    {
        return editor.getSnapshotInput().getSnapshot();
    }

    public ISnapshot resolveSnapshot(String argument) throws SnapshotException
    {
        boolean hasBaseline = editor.getSnapshotInput().hasBaseline();

        if ("&base".equals(argument) && hasBaseline)
        {
            return editor.getSnapshotInput().getBaseline();
        }
        else if (argument.charAt(0) == '&') // select baseline
        {
            IPath path = askForFilename();

            if (path != null)
            {
                return SnapshotFactory.openSnapshot(path.toFile(), new VoidProgressListener());
            }
        }
        else if (argument.charAt(0) == '?') // open dialog
        {
            // TODO (elena) trigger open snapshot dialog
            // (currently in rcp plugin -> move to core; but
            // registration to actionSet only in rcp because it
            // should not be there if we run embedded into the
            // IDE)
            return null;
        }
        else
        {
            // TODO (ab) snapshot is NOT disposed. It is
            // unclear when to do it -> if the argument
            // instance is disposed?
            File file = new File(argument);
            return SnapshotFactory.openSnapshot(file, new VoidProgressListener());
        }
        return null;
    }

    IPath askForFilename()
    {
        final List<IPath> resources = new ArrayList<IPath>();

        IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        for (int i = 0; i < windows.length; i++)
        {
            IWorkbenchWindow window = windows[i];
            IWorkbenchPage[] pages = window.getPages();
            for (int j = 0; j < pages.length; j++)
            {
                IWorkbenchPage page = pages[j];
                IEditorReference[] editors = page.getEditorReferences();
                for (int k = 0; k < editors.length; k++)
                {
                    try
                    {
                        IEditorReference editor = editors[k];
                        IEditorPart e = editor.getEditor(true);

                        if (e instanceof MultiPaneEditor && e != this.editor)
                        {
                            IEditorInput input = editor.getEditorInput();
                            if (input == null)
                                continue;

                            IFile file = ResourceUtil.getFile(input);
                            if (file != null)
                                resources.add(file.getLocation());
                            else if (input instanceof IPathEditorInput)
                                resources.add(((IPathEditorInput) input).getPath());
                        }
                    }
                    catch (PartInitException ignore)
                    {
                        // $JL-EXC$
                    }
                }
            }
        }

        if (resources.isEmpty())
        {
            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Select base line",
                            "Currently no other heap dumps open.");
            return null;
        }

        ListDialog dialog = new ListDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
        dialog.setLabelProvider(new LabelProvider()
        {

            public Image getImage(Object element)
            {
                return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HEAP);
            }

            public String getText(Object element)
            {
                IPath path = (IPath) element;
                return path.lastSegment() + " (" + path.toOSString() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            }
        });
        dialog.setContentProvider(new IStructuredContentProvider()
        {
            @SuppressWarnings("unchecked")
            public Object[] getElements(Object inputElement)
            {
                return ((List) inputElement).toArray();
            }

            public void dispose()
            {}

            public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
            {}

        });
        dialog.setInput(resources);
        dialog.setTitle("Select baseline");
        dialog.setMessage("Select a baseline to compare the heap dump with.");
        dialog.open();

        Object[] result = dialog.getResult();

        return result == null ? null : (IPath) result[0];
    }

}
