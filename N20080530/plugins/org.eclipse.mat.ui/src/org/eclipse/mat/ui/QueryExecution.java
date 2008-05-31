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
package org.eclipse.mat.ui;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.impl.query.CommandLine;
import org.eclipse.mat.impl.query.HeapObjectContextArgument;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.impl.test.TestSuite;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.test.QuerySpec;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.test.Spec;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.internal.query.ArgumentContextProvider;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentsWizard;
import org.eclipse.mat.ui.internal.query.browser.QueryHistory;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.ui.PlatformUI;

public class QueryExecution
{

    public static void executeAgain(HeapEditor editor, PaneState state) throws SnapshotException
    {
        ArgumentSet argumentSet = CommandLine.parse(new ArgumentContextProvider(editor), state.getIdentifier());
        execute(editor, state.getParentPaneState(), state, argumentSet, false, true);
    }

    public static void executeCommandLine(HeapEditor editor, PaneState originator, String commandLine)
                    throws SnapshotException
    {
        ArgumentSet argumentSet = CommandLine.parse(new ArgumentContextProvider(editor), commandLine);
        execute(editor, originator, null, argumentSet, false, true);
    }

    public static void executeQuery(HeapEditor editor, QueryDescriptor query) throws SnapshotException
    {
        ArgumentSet argumentSet = query.createNewArgumentSet(new ArgumentContextProvider(editor));
        execute(editor, null, null, argumentSet, true, true);
    }

    public static void execute(HeapEditor editor, PaneState originator, PaneState stateToReopen, ArgumentSet set,
                    boolean promptUser, boolean isReproducable) throws SnapshotException
    {
        if (!set.isExecutable())
            promptUser = true;

        if (promptUser && !promp(editor, set))
            return;

        String cmdLine = set.writeToLine();

        if (isReproducable)
            QueryHistory.addQuery(cmdLine);   
       
        Job job = new ExecutionJob(editor, originator, stateToReopen, cmdLine, set, isReproducable);
        job.setUser(true);
        job.schedule();
    }

    private static boolean promp(HeapEditor editor, ArgumentSet set)
    {
        boolean hasUserArguments = false;
        boolean hasHeapObjectContextArguments = false;

        List<ArgumentDescriptor> arguments = set.getQueryDescriptor().getArguments();

        for (ArgumentDescriptor arg : arguments)
        {
            if (arg.isHeapObject())
            {
                hasHeapObjectContextArguments = set.getArgumentValue(arg) instanceof HeapObjectContextArgument;
                hasUserArguments = hasUserArguments || !hasHeapObjectContextArguments;
            }
            else
                hasUserArguments = hasUserArguments || !arg.isPrimarySnapshot();
        }

        if (hasUserArguments)
        {
            ArgumentsWizard wizard = new ArgumentsWizard(set);
            WizardDialog dialog = new WizardDialog(editor.getSite().getShell(), wizard);
            // this adds the image button to the lower left corner of the wizard
            // dialog
            dialog.setHelpAvailable(true);
            dialog.setBlockOnOpen(true);
            // dialog has to be created to be able to set help to its shell
            dialog.create();

            PlatformUI.getWorkbench().getHelpSystem().setHelp(dialog.getShell(), "org.eclipse.mat.ui.query.arguments");
            dialog.open();

            if (dialog.getReturnCode() == Window.CANCEL)
                return false;
        }

        return true;
    }

    public static void displayResult(final HeapEditor editor, final PaneState originator, final PaneState stateToReopen,
                    QueryResult result, final boolean isReproducable)
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

        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                doDisplayResult(editor, originator, stateToReopen, r, true, isReproducable);
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
        HeapEditor editor;
        ArgumentSet argumentSet;
        PaneState originator;
        PaneState stateToReopen;
        boolean isReproducable;

        public ExecutionJob(HeapEditor editor, PaneState originator, PaneState stateToReopen, String name,
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
                QueryResult result = argumentSet.execute(new ProgressMonitorWrapper(monitor));

                if (result != null && result.getSubject() != null)
                {
                    displayResult(editor, originator, stateToReopen, result, isReproducable);

                    return Status.OK_STATUS;
                }
                else
                {
                    ErrorHelper.showInfoMessage("Query did not return any result");
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
        }
    }

    // //////////////////////////////////////////////////////////////
    // private static helpers
    // //////////////////////////////////////////////////////////////

    private static void doDisplayResult(HeapEditor editor, PaneState originator, PaneState stateToReopen,
                    QueryResult result, boolean isFirst, boolean isReproducable)
    {
        if (result.isComposite())
        {
            List<CompositeResult.Entry> results = ((CompositeResult) result.getSubject()).getResultEntries();

            boolean first = true;
            for (CompositeResult.Entry r : results)
            {
                doDisplayResult(editor, originator, stateToReopen, new QueryResult(result, result.getQuery(), result
                                .getCommand(), r.getResult()), first, isReproducable);
                first = false;
            }
        }
        else
        {
            IResult subject = result.getSubject();

            AbstractEditorPane pane = createPane(subject, null);

            if (stateToReopen == null)
            {               
                // to keep to the tree hierarchy for the Composite result pane we need
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
                state.setImage(ImageHelper.getImage(result.getQuery()));
                pane.setPaneState(state);
            }
            else
            {
                pane.setPaneState(stateToReopen);
            }
            
            editor.addNewPage(pane, result, result.getTitle(), ImageHelper.getImage(result.getQuery()));
        }
    }

    // //////////////////////////////////////////////////////////////
    // convert result for display purposes
    // //////////////////////////////////////////////////////////////

    private static QueryResult convertToHtml(HeapEditor editor, QueryResult result, CompositeResult composite)
    {
        String name = composite.getName() != null ? composite.getName() : result.getTitle();
        SectionSpec section = new SectionSpec(name);

        int count = 1;

        for (CompositeResult.Entry r : composite.getResultEntries())
        {
            String label = r.getName();
            if (label == null)
                label = result.getCommand() + " " + count;

            QuerySpec spec = new QuerySpec(label);
            spec.setResult(r.getResult());
            section.add(spec);

            count++;
        }

        return convertToHtml(editor, result, section);
    }

    private static QueryResult convertToHtml(HeapEditor editor, QueryResult result, Spec section)
    {
        try
        {
            TestSuite suite = new TestSuite.Builder(section).snapshot(editor.getSnapshotInput().getSnapshot()).build();
            suite.execute(new VoidProgressListener());

            for (File f : suite.getResults())
            {
                if ("index.html".equals(f.getName()))
                    return new QueryResult(result.getQuery(), result.getCommand(), new DisplayFileResult(f));

            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        throw new RuntimeException(section.getName() + " did not produce any HTML output.");
    }

    // //////////////////////////////////////////////////////////////
    // read extension point
    // //////////////////////////////////////////////////////////////

    public static AbstractEditorPane createPane(IResult subject, Class<? extends AbstractEditorPane> ignore)
    {
        try
        {
            setupConfiguration();

            Class<?> clazz = subject.getClass();

            while (clazz != null && clazz != Object.class)
            {
                AbstractEditorPane template = map.get(clazz.getName());
                if (template != null && (ignore == null //
                                || (((Class<?>) template.getClass()) != ((Class<?>) ignore.getClass()))))
                    return template.getClass().newInstance();

                LinkedList<Class<?>> interf = new LinkedList<Class<?>>();
                for (Class<?> itf : clazz.getInterfaces())
                    interf.add(itf);

                while (!interf.isEmpty())
                {
                    Class<?> current = interf.removeFirst();
                    template = map.get(current.getName());
                    if (template != null && (ignore == null || !template.getClass().equals(ignore)))
                        return template.getClass().newInstance();

                    for (Class<?> itf : current.getInterfaces())
                        interf.add(itf);
                }

                clazz = clazz.getSuperclass();
            }

            return null;
        }
        catch (InstantiationException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, AbstractEditorPane> map;

    private static synchronized void setupConfiguration()
    {
        if (map != null)
            return;

        map = new HashMap<String, AbstractEditorPane>();

        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint(MemoryAnalyserPlugin.PLUGIN_ID + ".resultPanes"); //$NON-NLS-1$
        if (point != null)
        {
            IExtension[] extensions = point.getExtensions();
            for (int i = 0; i < extensions.length; i++)
            {
                IConfigurationElement confElements[] = extensions[i].getConfigurationElements();
                for (int jj = 0; jj < confElements.length; jj++)
                {
                    try
                    {
                        AbstractEditorPane pane = (AbstractEditorPane) confElements[jj]
                                        .createExecutableExtension("class");
                        for (IConfigurationElement child : confElements[jj].getChildren())
                            map.put(child.getAttribute("type"), pane);
                    }
                    catch (InvalidRegistryObjectException e)
                    {
                        MemoryAnalyserPlugin.log(e);
                    }
                    catch (CoreException e)
                    {
                        MemoryAnalyserPlugin.log(e);
                    }
                }
            }
        }
    }
}
