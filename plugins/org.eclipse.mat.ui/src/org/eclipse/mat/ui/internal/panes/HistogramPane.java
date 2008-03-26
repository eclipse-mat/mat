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
package org.eclipse.mat.ui.internal.panes;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.inspections.query.HistogramQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.Icons;
import org.eclipse.mat.query.QueryUtil;
import org.eclipse.mat.query.results.HistogramResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.ParseHeapDumpJob;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.MessageBox;
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


public class HistogramPane extends QueryResultPane
{
    boolean isGrouped = false;
    Histogram histogram;

    Action deltaAction;

    @Override
    protected void makeActions()
    {
        super.makeActions();

        deltaAction = new DeltaAction();
    }

    @Override
    public void initWithArgument(Object argument)
    {
        if (argument == null)
        {
            try
            {
                HistogramQuery query = new HistogramQuery();
                query.snapshot = getSnapshotInput().getSnapshot();

                IResult result = QueryUtil.execute(query, new VoidProgressListener());

                QueryDescriptor descriptor = QueryRegistry.instance().getQuery(HistogramQuery.class);
                argument = new QueryResult(descriptor, descriptor.getIdentifier(), result);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        else if (argument instanceof QueryResult && ((QueryResult) argument).getSubject() instanceof HistogramResult)
        {
            QueryResult previous = (QueryResult) argument;
            argument = new QueryResult(previous.getQuery(), previous.getCommand(), ((HistogramResult) previous
                            .getSubject()).asTable());
        }

        super.initWithArgument(argument);

        IResult subject = ((QueryResult) argument).getSubject();
        histogram = subject instanceof Histogram ? (Histogram) subject //
                        : ((Histogram.ClassLoaderTree) subject).getHistogram();

        // the default histogram has the retained size column visible by default
        // (as values might have been stored)
        if (histogram.isDefaultHistogram())
            viewer.showRetainedSizeColumn(viewer.getQueryResult().getDefaultContextProvider());
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        addGroupingOptions(manager);
        super.contributeToToolBar(manager);

        manager.add(new Separator());

        if (histogram.isDefaultHistogram())
            manager.add(deltaAction);
    }

    private void addGroupingOptions(IToolBarManager manager)
    {
        Action groupingAction = new EasyToolBarDropDown("Group result by...", //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.GROUPING), this)
        {
            @Override
            public void contribute(PopupMenu menu)
            {
                Action action = new GroupingAction(false);
                action.setText("Group by class");
                action.setImageDescriptor(ImageHelper.getImageDescriptor(Icons.CLASS));
                action.setEnabled(isGrouped);
                action.setChecked(!isGrouped);
                menu.add(action);

                action = new GroupingAction(true);
                action.setText("Group by class loader");
                action.setImageDescriptor(ImageHelper.getImageDescriptor(Icons.CLASSLOADER_INSTANCE));
                action.setEnabled(!isGrouped);
                action.setChecked(isGrouped);
                menu.add(action);
            }
        };

        manager.add(groupingAction);

    }

    private void updateDeltaHistogram()
    {
        Job job = new Job("Calculating intersecting histograms")
        {

            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                try
                {
                    Histogram baseline = getSnapshotInput().getBaseline().getHistogram(
                                    new ProgressMonitorWrapper(monitor));
                    if (baseline == null)
                    {
                        deltaAction.setChecked(false);
                        return Status.CANCEL_STATUS;
                    }

                    final Histogram delta = histogram.diffWithBaseline(baseline);

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            QueryResult qr;
                            if (HistogramPane.this.isGrouped)
                                qr = new QueryResult(null, "[diff]", delta.groupByClassLoader());
                            else
                                qr = new QueryResult(null, "[diff]", delta);

                            RefinedResultViewer v = createViewer(qr);

                            activateViewer(v);
                        }
                    });
                }
                catch (SnapshotException e)
                {
                    deltaAction.setChecked(false);
                    return ErrorHelper.createErrorStatus(e);
                }

                return Status.OK_STATUS;
            }

        };

        job.setUser(true);
        job.schedule();
    }

    // //////////////////////////////////////////////////////////////
    // grouping action
    // //////////////////////////////////////////////////////////////

    private class GroupingAction extends Action
    {
        private boolean doGroup;

        public GroupingAction(boolean doGroup)
        {
            super("Group", AS_CHECK_BOX);
            this.doGroup = doGroup;
        }

        public void run()
        {
            if (!isChecked())// do not run the same action twice - selection
                                // was not changed
                return;
            if (viewer.getResult().hasActiveFilter())
            {
                StringBuilder buf = new StringBuilder();
                buf.append("The original table is filtered. The WHOLE tree will be grouped.");

                MessageBox msg = new MessageBox(viewer.getControl().getShell(), SWT.OK | SWT.CANCEL);
                msg.setText("Info");
                msg.setMessage(buf.toString());

                if (msg.open() != SWT.OK)
                    return;
            }

            IStructuredResult unwrapped = viewer.getResult().unwrap();
            final Histogram current = unwrapped instanceof Histogram ? (Histogram) unwrapped
                            : ((Histogram.ClassLoaderTree) unwrapped).getHistogram();

            new Job(getText())
            {
                protected IStatus run(IProgressMonitor monitor)
                {
                    IStructuredResult result;

                    if (doGroup)
                        result = current.groupByClassLoader();
                    else
                        result = current;

                    final QueryResult queryResult = current == HistogramPane.this.histogram ? new QueryResult(
                                    QueryRegistry.instance().getQuery(HistogramQuery.class), //
                                    "show_histogram" + (doGroup ? " -byclassloader" : ""), result) : // 
                                    new QueryResult(null, "[diff]", result);

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            HistogramPane.this.isGrouped = doGroup;

                            RefinedResultViewer v = createViewer(queryResult);

                            activateViewer(v);
                        }

                    });

                    return Status.OK_STATUS;
                }
            }.schedule();
        }
    }

    // //////////////////////////////////////////////////////////////
    // delta action
    // //////////////////////////////////////////////////////////////

    private class DeltaAction extends Action
    {
        private DeltaAction()
        {
            super("Compare to another Heap Dump", IAction.AS_CHECK_BOX);
            setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.COMPARE));
        }

        public void run()
        {
            if (this.isChecked())
            {
                if (!getSnapshotInput().hasBaseline())
                {
                    selectBaseline();
                }
                else
                {
                    ISnapshot baseline = getSnapshotInput().getBaseline();

                    MessageBox box = new MessageBox(getSite().getShell(), SWT.OK | SWT.CANCEL);
                    box.setText("Select Baseline");
                    box.setMessage(MessageFormat.format("Compare against ''{0}''?", baseline.getSnapshotInfo()
                                    .getPath()));

                    if (box.open() == SWT.OK)
                        updateDeltaHistogram();
                    else
                        selectBaseline();
                }
            }
            else
            {
                deactivateViewer();

                QueryResult qr = new QueryResult(QueryRegistry.instance().getQuery(HistogramQuery.class), //
                                "show_histogram" + (isGrouped ? " -byclassloader" : ""), //
                                isGrouped ? histogram.groupByClassLoader() : histogram);

                activateViewer(createViewer(qr));
            }
        }

        private void selectBaseline()
        {
            IPath selected = askForFilename();

            if (selected != null)
            {
                new ParseHeapDumpJob(selected)
                {
                    protected void finished(ISnapshot snapshot)
                    {
                        if (getSnapshotInput().hasBaseline())
                        {
                            ISnapshot previous = getSnapshotInput().getBaseline();
                            if (previous != snapshot)
                            {
                                SnapshotFactory.dispose(previous);
                            }
                        }

                        getSnapshotInput().setBaseline(snapshot);
                        updateDeltaHistogram();
                    }

                }.schedule();
            }
            else
            {
                this.setChecked(false);
            }
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

                            if (e instanceof MultiPaneEditor
                                            && e != ((MultiPaneEditorSite) getSite()).getMultiPageEditor())
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
                MessageDialog.openInformation(getSite().getShell(), "Select baseline",
                                "No other heap dump opened in editor.");
                return null;
            }

            ListDialog dialog = new ListDialog(getSite().getShell());
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
                public Object[] getElements(Object inputElement)
                {
                    return ((List<?>) inputElement).toArray();
                }

                public void dispose()
                {}

                public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
                {}

            });
            dialog.setInput(resources);
            dialog.setTitle("Select baseline");
            dialog.setMessage("Select a heap dump from the open editors as baseline");
            dialog.open();

            Object[] result = dialog.getResult();

            return result == null ? null : (IPath) result[0];
        }

    }

}
