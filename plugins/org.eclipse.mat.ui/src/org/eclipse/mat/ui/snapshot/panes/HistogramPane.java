/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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
import org.eclipse.mat.ui.Messages;
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
import org.eclipse.mat.util.MessageUtil;
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
        BY_CLASS(Messages.HistogramPane_GroupByClass, Icons.CLASS), //
        BY_SUPERCLASS(Messages.HistogramPane_GroupBySuperclass, Icons.SUPERCLASS),
        BY_CLASSLOADER(Messages.HistogramPane_GroupByClassLoader, Icons.CLASSLOADER_INSTANCE), //
        BY_PACKAGE(Messages.HistogramPane_GroupByPackage, Icons.PACKAGE);

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
                QueryDescriptor histogram = QueryRegistry.instance().getQuery("histogram"); //$NON-NLS-1$
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
        else if (subject instanceof Histogram.SuperclassTree)
            groupedBy = Grouping.BY_SUPERCLASS;
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
        else if (subject instanceof Histogram.SuperclassTree)
            return ((Histogram.SuperclassTree) subject).getHistogram();
        else if (subject instanceof Histogram.ClassLoaderTree)
            return ((Histogram.ClassLoaderTree) subject).getHistogram();
        else if (subject instanceof Histogram.PackageTree)
            return ((Histogram.PackageTree) subject).getHistogram();
        else
            throw new RuntimeException(MessageUtil.format(Messages.HistogramPane_IllegalType, //
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
        Action groupingAction = new EasyToolBarDropDown(Messages.TableResultPane_GroupResultBy, //
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
        Job job = new Job(Messages.HistogramPane_CalculatingIntersectingHistograms)
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

                    top.getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            QueryResult qr;
                            switch (HistogramPane.this.groupedBy)
                            {
                                case BY_CLASS:
                                    qr = new QueryResult(null, "[diff]", delta);//$NON-NLS-1$
                                    break;
                                case BY_SUPERCLASS:
                                    ISnapshot snapshot = ((ISnapshotEditorInput) getEditorInput()).getSnapshot();
                                    qr = new QueryResult(null, "[diff]", delta.groupBySuperclass(snapshot));//$NON-NLS-1$
                                    break;
                                case BY_CLASSLOADER:
                                    qr = new QueryResult(null, "[diff]", delta.groupByClassLoader());//$NON-NLS-1$
                                    break;
                                case BY_PACKAGE:
                                    qr = new QueryResult(null, "[diff]", delta.groupByPackage());//$NON-NLS-1$
                                    break;
                                default:
                                    throw new RuntimeException(MessageUtil.format(Messages.HistogramPane_IllegalType,
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
    
    public Histogram getHistogram()
    {
    	return histogram;
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
                buf.append(Messages.DominatorPane_WholeTreeWillBeGrouped);

                MessageBox msg = new MessageBox(viewer.getControl().getShell(), SWT.OK | SWT.CANCEL);
                msg.setText(Messages.DominatorPane_Info);
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
                        case BY_SUPERCLASS:
                            ISnapshotEditorInput snapshotInput = ((ISnapshotEditorInput) getEditorInput());
                            result = current.groupBySuperclass(snapshotInput.getSnapshot());
                            break;
                        case BY_CLASSLOADER:
                            result = current.groupByClassLoader();
                            break;
                        case BY_PACKAGE:
                            result = current.groupByPackage();
                            break;
                        default:
                            throw new RuntimeException(MessageUtil
                                            .format(Messages.HistogramPane_IllegalType, groupBy));

                    }

                    final boolean isDeltaHistogram = current != HistogramPane.this.histogram;

                    final QueryResult queryResult = isDeltaHistogram ? new QueryResult(null, "[diff]", result)//$NON-NLS-1$
                                    : new QueryResult(QueryRegistry.instance().getQuery("histogram"),//$NON-NLS-1$
                                                    "histogram -groupBy " + groupBy.name(), result);//$NON-NLS-1$

                    top.getDisplay().asyncExec(new Runnable()
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
            super(Messages.HistogramPane_CompareToAnotherHeapDump, IAction.AS_CHECK_BOX);
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
                    box.setText(Messages.HistogramPane_SelectBaseline);
                    box.setMessage(MessageUtil.format(Messages.HistogramPane_CompareAgainst, baseline.getSnapshotInfo()
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
                    case BY_SUPERCLASS:
                        ISnapshotEditorInput snapshotInput = ((ISnapshotEditorInput) getEditorInput());
                        result = histogram.groupBySuperclass(snapshotInput.getSnapshot());
                        break;
                    case BY_CLASSLOADER:
                        result = histogram.groupByClassLoader();
                        break;
                    case BY_PACKAGE:
                        result = histogram.groupByPackage();
                        break;
                    default:
                        throw new RuntimeException(MessageUtil.format(Messages.HistogramPane_IllegalType, groupedBy));
                }

                QueryResult qr = new QueryResult(QueryRegistry.instance().getQuery("histogram"), //$NON-NLS-1$
                                "histogram -groupBy " + groupedBy.name(), //$NON-NLS-1$
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
                MessageDialog.openInformation(getSite().getShell(), Messages.HistogramPane_SelectBaseline,
                                Messages.HistogramPane_NoOtherHeapDump);
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
            dialog.setTitle(Messages.HistogramPane_SelectBaseline);
            dialog.setMessage(Messages.HistogramPane_SelectHeapDump);
            dialog.open();

            Object[] result = dialog.getResult();

            return result == null ? null : (IPath) result[0];
        }

    }

}
