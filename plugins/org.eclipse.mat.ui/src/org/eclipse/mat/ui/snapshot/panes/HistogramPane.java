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
package org.eclipse.mat.ui.snapshot.panes;

import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
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
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.query.HistogramResult;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.snapshot.ParseHeapDumpJob;
import org.eclipse.mat.ui.snapshot.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ErrorHelper;
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
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.ide.ResourceUtil;

public class HistogramPane extends QueryResultPane
{
    public enum Grouping
    {
        BY_CLASS("Group by class", Icons.CLASS), //
        BY_CLASSLOADER("Group by class loader", Icons.CLASSLOADER_INSTANCE), //
        BY_PACKAGE("Group by package", Icons.PACKAGE);

        String label;
        URL icon;

        private Grouping(String label, URL icon)
        {
            this.label = label;
            this.icon = icon;
        }

        public URL getIcon()
        {
            return icon;
        }

        public String toString()
        {
            return label;
        }
    }

    /**
     * the underlying histogram, possibly different from the histogram displayed
     * because of the delta action
     */
    private Histogram histogram;
    private Grouping groupedBy;

    private Action deltaAction;

    @Override
    public String getTitle()
    {
        if (histogram != null && histogram.getLabel() != null)
            return histogram.getLabel();
        return super.getTitle();
    }

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
                QueryDescriptor histogram = QueryRegistry.instance().getQuery("histogram");
                argument = histogram.createNewArgumentSet(getEditor().getQueryContext()) //
                                .execute(new VoidProgressListener());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        else if (argument instanceof QueryResult //
                        && ((QueryResult) argument).getSubject() instanceof HistogramResult)
        {
            QueryResult previous = (QueryResult) argument;
            argument = new QueryResult(previous.getQuery(), previous.getCommand(), //
                            ((HistogramResult) previous.getSubject()).getHistogram());
        }

        super.initWithArgument(argument);

        IResult subject = ((QueryResult) argument).getSubject();
        if (subject instanceof Histogram)
            groupedBy = Grouping.BY_CLASS;
        else if (subject instanceof Histogram.ClassLoaderTree)
            groupedBy = Grouping.BY_CLASSLOADER;
        else if (subject instanceof Histogram.PackageTree)
            groupedBy = Grouping.BY_PACKAGE;

        histogram = unwrapHistogram(subject);
        firePropertyChange(IWorkbenchPart.PROP_TITLE);

        // the default histogram has the retained size column visible by default
        // (as values might have been stored)
        if (histogram.isDefaultHistogram())
            viewer.showDerivedDataColumn(viewer.getQueryResult().getDefaultContextProvider(),
                            RetainedSizeDerivedData.APPROXIMATE);

    }

    private Histogram unwrapHistogram(IResult subject)
    {
        if (subject instanceof Histogram)
            return (Histogram) subject;
        else if (subject instanceof Histogram.ClassLoaderTree)
            return ((Histogram.ClassLoaderTree) subject).getHistogram();
        else if (subject instanceof Histogram.PackageTree)
            return ((Histogram.PackageTree) subject).getHistogram();
        else
            throw new RuntimeException(MessageFormat.format("Illegal type for HistogramPane: {0}", //
                            subject.getClass().getName()));
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
                for (Grouping g : Grouping.values())
                {
                    Action action = new GroupingAction(g);
                    action.setEnabled(g != groupedBy);
                    action.setChecked(g == groupedBy);
                    menu.add(action);
                }
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

                    Histogram baseline = ((ISnapshotEditorInput) getEditorInput()).getBaseline().getHistogram(
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
                            switch (HistogramPane.this.groupedBy)
                            {
                                case BY_CLASS:
                                    qr = new QueryResult(null, "[diff]", delta);
                                    break;
                                case BY_CLASSLOADER:
                                    qr = new QueryResult(null, "[diff]", delta.groupByClassLoader());
                                    break;
                                case BY_PACKAGE:
                                    qr = new QueryResult(null, "[diff]", delta.groupByPackage());
                                    break;
                                default:
                                    throw new RuntimeException(MessageFormat
                                                    .format("Illegal type for HistogramPane: {0}",
                                                                    HistogramPane.this.groupedBy));
                            }

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
        private Grouping groupBy;

        public GroupingAction(Grouping groupBy)
        {
            super(groupBy.toString(), AS_CHECK_BOX);
            this.groupBy = groupBy;

            setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(groupBy.getIcon()));
        }

        public void run()
        {
            // do not run the same action twice - selection was not changed
            if (!isChecked())
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

            final Histogram current = unwrapHistogram(viewer.getResult().unwrap());

            new Job(getText())
            {
                protected IStatus run(IProgressMonitor monitor)
                {
                    IStructuredResult result;

                    switch (groupBy)
                    {
                        case BY_CLASS:
                            result = current;
                            break;
                        case BY_CLASSLOADER:
                            result = current.groupByClassLoader();
                            break;
                        case BY_PACKAGE:
                            result = current.groupByPackage();
                            break;
                        default:
                            throw new RuntimeException(MessageFormat.format("Illegal type for HistogramPane: {0}",
                                            groupBy));

                    }

                    final boolean isDeltaHistogram = current != HistogramPane.this.histogram;

                    final QueryResult queryResult = isDeltaHistogram ? new QueryResult(null, "[diff]", result)
                                    : new QueryResult(QueryRegistry.instance().getQuery("histogram"),
                                                    "histogram -groupBy " + groupBy.name(), result);

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            HistogramPane.this.groupedBy = groupBy;

                            RefinedResultViewer v = createViewer(queryResult);

                            activateViewer(v);

                            if (!isDeltaHistogram && histogram.isDefaultHistogram())
                                v.showDerivedDataColumn(v.getQueryResult().getDefaultContextProvider(),
                                                RetainedSizeDerivedData.APPROXIMATE);

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
                ISnapshotEditorInput snapshotInput = ((ISnapshotEditorInput) getEditorInput());
                if (!snapshotInput.hasBaseline())
                {
                    selectBaseline();
                }
                else
                {
                    ISnapshot baseline = snapshotInput.getBaseline();

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

                IResult result;

                switch (groupedBy)
                {
                    case BY_CLASS:
                        result = histogram;
                        break;
                    case BY_CLASSLOADER:
                        result = histogram.groupByClassLoader();
                        break;
                    case BY_PACKAGE:
                        result = histogram.groupByPackage();
                        break;
                    default:
                        throw new RuntimeException(MessageFormat.format("Illegal type for HistogramPane: {0}",
                                        groupedBy));
                }

                QueryResult qr = new QueryResult(QueryRegistry.instance().getQuery("histogram"), //
                                "histogram -groupBy " + groupedBy.name(), //
                                result);

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
                        ISnapshotEditorInput snapshotInput = ((ISnapshotEditorInput) getEditorInput());
                        if (snapshotInput.hasBaseline())
                        {
                            ISnapshot previous = snapshotInput.getBaseline();
                            if (previous != snapshot)
                            {
                                SnapshotFactory.dispose(previous);
                            }
                        }

                        snapshotInput.setBaseline(snapshot);
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
                                else if (input instanceof IURIEditorInput)
                                    resources.add(new Path(((IURIEditorInput) input).getURI().getRawPath()));
                                else if (input instanceof IPathEditorInput)
                                    resources.add(((IPathEditorInput) input).getPath());
                            }
                        }
                        catch (PartInitException ignore)
                        {
                            // do not include into list of dumps
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
