/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.HeapObjectContextArgument;
import org.eclipse.mat.internal.snapshot.SnapshotArgument;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.browser.QueryHistory;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentsWizard;
import org.eclipse.mat.ui.snapshot.ParseHeapDumpJob;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.ui.PlatformUI;

public class QueryExecution
{

    public static void executeAgain(MultiPaneEditor editor, PaneState state) throws SnapshotException
    {
        ArgumentSet argumentSet = CommandLine.parse(editor.getQueryContext(), state.getIdentifier());
        execute(editor, state.getParentPaneState(), state, argumentSet, false, true);
    }

    public static void executeCommandLine(MultiPaneEditor editor, PaneState originator, String commandLine)
                    throws SnapshotException
    {
        ArgumentSet argumentSet = CommandLine.parse(editor.getQueryContext(), commandLine);
        execute(editor, originator, null, argumentSet, false, true);
    }

    public static void executeCommandLine(MultiPaneEditor editor, PaneState originator, String commandLine, boolean prompt)
                    throws SnapshotException
    {
        ArgumentSet argumentSet = CommandLine.parse(editor.getQueryContext(), commandLine);
        execute(editor, originator, null, argumentSet, prompt, true);
    }

    public static void executeQuery(MultiPaneEditor editor, QueryDescriptor query) throws SnapshotException
    {
        ArgumentSet argumentSet = query.createNewArgumentSet(editor.getQueryContext());
        execute(editor, null, null, argumentSet, true, true);
    }

    public static void execute(MultiPaneEditor editor, PaneState originator, PaneState stateToReopen, ArgumentSet set,
                    boolean forcePrompt, boolean isReproducable)
    {
        boolean promptUser = forcePrompt;
        if (!set.isExecutable())
            promptUser = true;

        if (promptUser && !promp(editor, set, forcePrompt))
            return;

        String cmdLine = set.writeToLine();

        if (isReproducable)
            QueryHistory.addQuery(cmdLine);

        Job job = new ExecutionJob(editor, originator, stateToReopen, cmdLine, set, isReproducable);
        job.setUser(true);
        job.schedule(Job.SHORT);
    }

    private static boolean promp(MultiPaneEditor editor, ArgumentSet arguments, boolean prompt)
    {
        boolean hasUserArguments = false;
        boolean hasEditableArguments = false;

        /*
         * Find unset arguments, or boolean arguments set to false
         * because CommandLine.parse might have added values for unset flag arguments
         */
        for (ArgumentDescriptor arg : arguments.getQueryDescriptor().getArguments())
        {
            hasUserArguments = hasUserArguments || arguments.getArgumentValue(arg) == null
                            || (arg.getType() == Boolean.class || arg.getType() == boolean.class) 
                            && Boolean.FALSE.equals(arguments.getArgumentValue(arg));
            hasEditableArguments |= !editor.getQueryContext().available(arg.getType(), arg.getAdvice())
                            && !(arguments.getArgumentValue(arg) instanceof HeapObjectContextArgument) ;
        }

        if (hasUserArguments || prompt && hasEditableArguments)
        {
            ArgumentsWizard wizard = new ArgumentsWizard(editor.getQueryContext(), arguments);
            WizardDialog dialog = new WizardDialog(editor.getSite().getShell(), wizard);
            // this adds the image button to the lower left corner of the wizard
            // dialog
            dialog.setHelpAvailable(true);
            dialog.setBlockOnOpen(true);
            // dialog has to be created to be able to set help to its shell
            dialog.create();

            PlatformUI.getWorkbench().getHelpSystem().setHelp(dialog.getShell(), "org.eclipse.mat.ui.query.arguments");//$NON-NLS-1$
            dialog.open();

            if (dialog.getReturnCode() == Window.CANCEL)
                return false;
        }

        return true;
    }

    public static void displayResult(final MultiPaneEditor editor, final PaneState originator,
                    final PaneState stateToReopen, QueryResult result, final boolean isReproducable)
    {
        if (result.getSubject() instanceof CompositeResult)
        {
            CompositeResult composite = (CompositeResult) result.getSubject();
            if (composite.asHtml())
                result = convertToHtml(editor, result, composite);
        }
        else if (result.getSubject() instanceof Spec)
        {
            result = convertToHtml(editor, result, (Spec) result.getSubject());
        }

        final QueryResult r = result;

        editor.getSite().getShell().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                doDisplayResult(editor, originator, stateToReopen, r, isReproducable);
            }
        });
    }

    private QueryExecution()
    {}

    // //////////////////////////////////////////////////////////////
    // query execution job
    // //////////////////////////////////////////////////////////////

    private static class ExecutionJob extends Job
    {
        MultiPaneEditor editor;
        ArgumentSet argumentSet;
        PaneState originator;
        PaneState stateToReopen;
        boolean isReproducable;

        public ExecutionJob(MultiPaneEditor editor, PaneState originator, PaneState stateToReopen, String name,
                        ArgumentSet argumentSet, boolean isReproducable)
        {
            super(name);
            this.editor = editor;
            this.originator = originator;
            this.stateToReopen = stateToReopen;
            this.argumentSet = argumentSet;
            this.isReproducable = isReproducable;
        }

        @Override
        protected IStatus run(IProgressMonitor monitor)
        {
            try
            {
                List<ISnapshot> secondarySnapshots = new ArrayList<ISnapshot>();
                preloadSnapshots(secondarySnapshots, monitor);
                QueryResult result;
                try
                {
                    result = argumentSet.execute(new ProgressMonitorWrapper(monitor));
                }
                finally
                {
                    disposePreloadedSnapshots(secondarySnapshots);
                }

                if (result != null && result.getSubject() != null)
                {
                    displayResult(editor, originator, stateToReopen, result, isReproducable);

                    return Status.OK_STATUS;
                }
                else
                {
                    ErrorHelper.showInfoMessage(Messages.QueryExecution_NoResult);
                    return Status.OK_STATUS;
                }
            }
            catch (Exception e)
            {
                // bad hack: if the job fails to early, a corrupt result
                // indicator is added and leads to NPEs inside eclipse
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException ignore)
                {
                    // $JL-EXC$
                }

                if (e instanceof IProgressListener.OperationCanceledException)
                {
                    return Status.CANCEL_STATUS;
                }
                else
                {
                    return ErrorHelper.createErrorStatus(e);
                }
            }
			finally
			{
				// release the references to the Editor and related panes
				// because if the job stays displayed in the UI, then it keeps
				// also the SnapsotImpl -> too bad if the heap dumps are huge
				// see Bug 312594
				monitor.done();
				cleanup();
			}
        }

        /**
         * Preload secondary snapshots.
         * {@link ArgumentSet#execute(IProgressListener)} won't show progress
         * parsing secondary snapshots, so load them in advance.
         * @param snapshots
         * @param mon
         * @return a list of secondary snapshots which need to be disposed.
         * @throws SnapshotException
         * @throws InterruptedException
         */
        private List<ISnapshot> preloadSnapshots(List<ISnapshot> snapshots, IProgressMonitor mon) throws SnapshotException, InterruptedException
        {
            // Open secondary snapshot and add to recently used list
            for (ArgumentDescriptor arg : argumentSet.getQueryDescriptor().getArguments())
            {
                if (arg.getAdvice() == Advice.SECONDARY_SNAPSHOT)
                {
                    Object value = argumentSet.getArgumentValue(arg);
                    if (value instanceof SnapshotArgument)
                    {
                        SnapshotArgument sa = (SnapshotArgument)value;
                        preloadSnapshot(mon, snapshots, sa);
                    }
                    else if (arg.isList())
                    {
                        // handle ArgumentFactory inside list
                        List<?> source = (List<?>) value;
                        for (int ii = 0; ii < source.size(); ii++)
                        {
                            Object v = source.get(ii);
                            if (v instanceof SnapshotArgument)
                            {
                                SnapshotArgument sa = (SnapshotArgument)v;
                                preloadSnapshot(mon, snapshots, sa);
                            }
                        }
                    }
                }
            }
            return snapshots;
        }

        /**
         * Preload a single snapshot.
         * @param mon for cancellation
         * @param snapshots to put the new snapshot into
         * @param sa the secondary snapshot to be loaded
         * @throws InterruptedException
         */
        private void preloadSnapshot(IProgressMonitor mon, List<ISnapshot> snapshots, SnapshotArgument sa)
                        throws InterruptedException
        {
            Path snappath = new Path(sa.getFilename());
            int size = snapshots.size();
            ParseHeapDumpJob job = new ParseHeapDumpJob(snappath)
            {
                protected void finished(ISnapshot snapshot)
                {
                    synchronized (snapshots)
                    {
                        snapshots.add(snapshot);
                        snapshots.notifyAll();
                    }
                }
            };
            job.schedule(Job.LONG);
            job.join(Long.MAX_VALUE, mon);
            IStatus status = job.getResult();
            if (status != null && status.isOK())
            {
                // finished is called by async(), so wait a little
                int count = 3;
                synchronized (snapshots)
                {
                    while (snapshots.size() == size && count++ < 3)
                    {
                        snapshots.wait(10000);
                    }
                }
            }
        }

        /**
         * Dispose of all the preloaded snapshots
         * @param secondarySnapshots a list of the snapshots
         */
        private void disposePreloadedSnapshots(List<ISnapshot> secondarySnapshots)
        {
            synchronized (secondarySnapshots)
            {
                for (ISnapshot snapshot : secondarySnapshots)
                {
                    SnapshotFactory.dispose(snapshot);
                }
            }
        }

        private void cleanup()
        {
            this.editor = null;
            this.originator = null;
            this.stateToReopen = null;
            this.argumentSet = null;
        }
    }

    // //////////////////////////////////////////////////////////////
    // private static helpers
    // //////////////////////////////////////////////////////////////

    private static void doDisplayResult(MultiPaneEditor editor, PaneState originator, PaneState stateToReopen,
                    QueryResult result, boolean isReproducable)
    {
        if (result.getSubject() instanceof CompositeResult)
        {
            List<CompositeResult.Entry> results = ((CompositeResult) result.getSubject()).getResultEntries();

            for (CompositeResult.Entry r : results)
            {
                QueryResult qr;
                if (r.getResult() instanceof CompositeResult && ((CompositeResult) r.getResult()).asHtml())
                {
                    qr = convertToHtml(editor, result, (CompositeResult) r.getResult());
                }
                else if (r.getResult() instanceof Spec)
                {
                    qr = convertToHtml(editor, result, (Spec) r.getResult());
                }
                else
                {
                    qr = new QueryResult(result, result.getQuery(), result.getCommand(), r.getResult());
                }
                doDisplayResult(editor, originator, stateToReopen, qr, isReproducable);
            }
        }
        else
        {
            IResult subject = result.getSubject();

            AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane(subject, null);

            if (stateToReopen == null)
            {
                // to keep to the tree hierarchy for the Composite result pane
                // we need
                // to add a new result state to an active COMPOSITE_CHILD
                if (originator != null && originator.getType() == PaneType.COMPOSITE_PARENT)
                {
                    for (PaneState child : originator.getChildren())
                    {
                        if (child.isActive())
                        {
                            originator = child;
                            break;
                        }
                    }
                }
                PaneState state;
                if (pane instanceof CompositeHeapEditorPane)
                    state = new PaneState(PaneType.COMPOSITE_PARENT, originator, pane.getTitle(), false);
                else
                    state = new PaneState(PaneType.QUERY, originator, result.getCommand(), isReproducable);
                state.setImage(MemoryAnalyserPlugin.getDefault().getImage(result.getQuery()));
                pane.setPaneState(state);
            }
            else
            {
                pane.setPaneState(stateToReopen);
            }

            editor.addNewPage(pane, result, result.getTitle(), MemoryAnalyserPlugin.getDefault().getImage(
                            result.getQuery()));
        }
    }

    // //////////////////////////////////////////////////////////////
    // convert result for display purposes
    // //////////////////////////////////////////////////////////////

    private static QueryResult convertToHtml(MultiPaneEditor editor, QueryResult result, CompositeResult composite)
    {
        String name = composite.getName() != null ? composite.getName() : result.getTitle();
        SectionSpec section = new SectionSpec(name);

        int count = 1;

        for (CompositeResult.Entry r : composite.getResultEntries())
        {
            String label = r.getName();
            if (label == null)
                label = result.getCommand() + " " + count;//$NON-NLS-1$

            QuerySpec spec = new QuerySpec(label);
            spec.setResult(r.getResult());
            section.add(spec);

            count++;
        }

        return convertToHtml(editor, result, section);
    }

    private static QueryResult convertToHtml(MultiPaneEditor editor, QueryResult result, Spec section)
    {
        try
        {
            TestSuite suite = new TestSuite.Builder(section).build(editor.getQueryContext());
            suite.execute(new VoidProgressListener());

            for (File f : suite.getResults())
            {
                if ("index.html".equals(f.getName()))//$NON-NLS-1$
                    return new QueryResult(result.getQuery(), result.getCommand(), new DisplayFileResult(f));

            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        throw new RuntimeException(section.getName() + Messages.QueryExecution_NoHTMLOutput);
    }

}
