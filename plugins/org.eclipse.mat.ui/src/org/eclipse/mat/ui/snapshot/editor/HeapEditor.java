/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.editor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.GettingStartedWizard;
import org.eclipse.mat.ui.snapshot.ParseHeapDumpJob;
import org.eclipse.mat.ui.snapshot.views.SnapshotOutlinePage;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class HeapEditor extends MultiPaneEditor implements ISelectionProvider
{

    /**
     * Extension to the normal query context to allow access to the UI display.
     * Used as a safe way to get the display for RAP.
     */
    private static final class UISnapshotQueryContext extends SnapshotQueryContext
    {
        private Display display;

        private UISnapshotQueryContext(ISnapshot snapshot, EditorPart editor)
        {
            super(snapshot);
            display = editor.getSite().getShell().getDisplay();
        }

        @Override
        public boolean available(Class<?> type, Argument.Advice advice)
        {
            if (type.isAssignableFrom(Display.class)) { return true; }
            // The context will be checked and filled in by the policy
            if (type.isAssignableFrom(IContextObjectSet.class)) { return true; }
            return super.available(type, advice);
        }

        @Override
        public Object get(Class<?> type, Argument.Advice advice)
        {
            if (type.isAssignableFrom(Display.class)) { return type.cast(display); }
            return super.get(type, advice);
        }
    }

    static class SnapshotEditorInput implements ISnapshotEditorInput
    {
        private IPath path;
        private ISnapshot snapshot;
        private ISnapshot baseline;

        List<IChangeListener> listeners = new ArrayList<IChangeListener>();

        public SnapshotEditorInput(IPath osFilename)
        {
            this.path = osFilename;
        }

        public IPath getPath()
        {
            return path;
        }

        public ISnapshot getSnapshot()
        {
            return snapshot;
        }

        public void setSnapshot(ISnapshot snapshot)
        {
            SnapshotEditorInput.this.snapshot = snapshot;

            List<IChangeListener> listeners = new ArrayList<IChangeListener>(SnapshotEditorInput.this.listeners);

            for (IChangeListener listener : listeners)
                listener.onSnapshotLoaded(snapshot);
        }

        public boolean hasSnapshot()
        {
            return snapshot != null;
        }

        public synchronized ISnapshot getBaseline()
        {
            return baseline;
        }

        public synchronized void setBaseline(ISnapshot snapshot)
        {
            if (baseline != null)
                SnapshotFactory.dispose(baseline);

            this.baseline = snapshot;

            List<IChangeListener> listeners = new ArrayList<IChangeListener>(SnapshotEditorInput.this.listeners);

            for (IChangeListener listener : listeners)
                listener.onBaselineLoaded(snapshot);
        }

        public boolean hasBaseline()
        {
            return baseline != null;
        }

        public boolean exists()
        {
            return true;
        }

        public ImageDescriptor getImageDescriptor()
        {
            return null;
        }

        public String getName()
        {
            return path.toOSString();
        }

        public IPersistableElement getPersistable()
        {
            return null;
        }

        public String getToolTipText()
        {
            return path.toOSString();
        }

        @SuppressWarnings("unchecked")
        public Object getAdapter(Class adapter)
        {
            return null;
        }

        public void addChangeListener(IChangeListener listener)
        {
            this.listeners.add(listener);
        }

        public void removeChangeListener(IChangeListener listener)
        {
            this.listeners.remove(listener);
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof SnapshotEditorInput && ((SnapshotEditorInput) obj).path.equals(path);
        }

        @Override
        public int hashCode()
        {
            return path.hashCode();
        }

    }

    // //////////////////////////////////////////////////////////////////

    private ISnapshotEditorInput snapshotInput;
    private SnapshotOutlinePage snapshotOutlinePage;

    public IEditorInput getPaneEditorInput()
    {
        return snapshotInput;
    }

    public void init(IEditorSite site, IEditorInput input) throws PartInitException
    {
        super.init(site, input);

        if (input instanceof IFileEditorInput)
        {
            IFile file = ((IFileEditorInput) input).getFile();
            this.snapshotInput = new SnapshotEditorInput(file.getLocation());
            this.setPartName(file.getName());
        }
        else if (input instanceof IPathEditorInput)
        {
            IPath path = ((IPathEditorInput) input).getPath();
            this.snapshotInput = new SnapshotEditorInput(path);
            this.setPartName(path.lastSegment());
        }
        else if (input instanceof IURIEditorInput)
        {
            URI uri = ((IURIEditorInput) input).getURI();

            if ("file".equals(uri.getScheme())) //$NON-NLS-1$
            {
                IPath path = new Path(uri.getPath());
                this.snapshotInput = new SnapshotEditorInput(path);
                this.setPartName(path.lastSegment());
            }
            else
            {
                throw new PartInitException(MessageUtil.format(Messages.HeapEditor_UnsupportedScheme, uri
                                .toASCIIString()));
            }
        }
        else
        {
            throw new PartInitException(MessageUtil.format(Messages.HeapEditor_UnsupportedEditorInput, input.getClass()
                            .getName()));
        }
    }

    public void dispose()
    {
        super.dispose();

        if (snapshotInput.hasSnapshot())
            SnapshotFactory.dispose(snapshotInput.getSnapshot());

        if (snapshotInput.hasBaseline())
            SnapshotFactory.dispose(snapshotInput.getBaseline());

        // too many temporary items reference the heap editor -> cut the
        // connection to free memory
        snapshotInput = null;
    }

    @Override
    protected Job createInitializationJob()
    {
        return new ParseHeapDumpJob(snapshotInput.getPath())
        {
            @Override
            protected void finished(ISnapshot snapshot)
            {
                ((SnapshotEditorInput) snapshotInput).setSnapshot(snapshot);
                setQueryContext(new UISnapshotQueryContext(snapshot, HeapEditor.this));
            }
        };
    }

    @Override
    protected void createInitialPanes()
    {
        addNewPage("OverviewPane", null); //$NON-NLS-1$

        updateToolbar();

        // show getting started wizard if user did not hide it
        IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
        boolean hideWizard = prefs.getBoolean(GettingStartedWizard.HIDE_WIZARD_KEY);

        if (!hideWizard)
        {
            GettingStartedWizard wizard = new GettingStartedWizard(this);
            WizardDialog dialog = new WizardDialog(getSite().getShell(), wizard);
            // Normally use block on open = false, so overview page comes up.
            // If there is a Open Heap Dump dialog open then the main shell has focus.
            // We can get the wizard on top using setBlockOnOpen false
            // but then it hides the open heap dump dialog with focus.
            dialog.setBlockOnOpen(getSite().getShell().isFocusControl());
            dialog.create();
            dialog.open();
        }
    }

    public ISnapshotEditorInput getSnapshotInput()
    {
        return this.snapshotInput;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getAdapter(Class required)
    {
        if (IContentOutlinePage.class.equals(required))
        {
            if (snapshotOutlinePage == null)
                snapshotOutlinePage = new SnapshotOutlinePage.HeapEditorOutlinePage(this.getSnapshotInput());
            return snapshotOutlinePage;
        }
        return super.getAdapter(required);
    }

    // //////////////////////////////////////////////////////////////
    // selection provider
    // //////////////////////////////////////////////////////////////

    private List<ISelectionChangedListener> listeners = Collections
                    .synchronizedList(new ArrayList<ISelectionChangedListener>());

    private ISelectionChangedListener proxy = new ISelectionChangedListener()
    {
        public void selectionChanged(SelectionChangedEvent event)
        {
            forwardSelectionChangedEvent(event);
        }
    };

    private Set<AbstractEditorPane> registered = new HashSet<AbstractEditorPane>();

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
    }

    public ISelection getSelection()
    {
        AbstractEditorPane activeEditor = getActiveEditor();

        if (activeEditor instanceof ISelectionProvider)
            return ((ISelectionProvider) activeEditor).getSelection();

        return StructuredSelection.EMPTY;
    }

    public void setSelection(ISelection selection)
    {
        throw new UnsupportedOperationException();
    }

    private void forwardSelectionChangedEvent(SelectionChangedEvent event)
    {
        List<ISelectionChangedListener> l = new ArrayList<ISelectionChangedListener>(listeners);
        for (ISelectionChangedListener listener : l)
        {
            listener.selectionChanged(event);
        }
    }

    // //////////////////////////////////////////////////////////////
    // hook rewiring of selection listeners into multi pane editor
    // //////////////////////////////////////////////////////////////

    @Override
    protected void pageChange(int newPageIndex)
    {
        super.pageChange(newPageIndex);

        AbstractEditorPane activeEditor = getEditor(newPageIndex);
        if (activeEditor != null)
        {
            SelectionChangedEvent event = null;

            // compatibility mode: most panes just have the viewer
            // as selection provider. They do not have to care about
            // implementing a selection provider

            if (activeEditor instanceof ISelectionProvider)
            {
                ISelectionProvider provider = (ISelectionProvider) activeEditor;

                if (registered.add(activeEditor))
                    provider.addSelectionChangedListener(proxy);

                ISelection selection = provider.getSelection();
                event = new SelectionChangedEvent(provider, selection);
            }

            if (event != null)
                forwardSelectionChangedEvent(event);
        }
    }

    @Override
    protected void disposePart(IWorkbenchPart part)
    {
        registered.remove(part);
        super.disposePart(part);
    }

}
