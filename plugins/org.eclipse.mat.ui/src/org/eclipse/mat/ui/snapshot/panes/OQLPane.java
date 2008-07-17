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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;

public class OQLPane extends CompositeHeapEditorPane
{
    private StyledText queryString;

    private Action executeAction;
    private Action copyQueryStringAction;

    // //////////////////////////////////////////////////////////////
    // initialization methods
    // //////////////////////////////////////////////////////////////

    public void createPartControl(Composite parent)
    {
        SashForm sash = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);

        queryString = new StyledText(sash, SWT.MULTI);
        queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        queryString.setText("/* Press F1 for help */");
        queryString.selectAll();
        queryString.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent e)
            {
                queryString.setStyleRanges(new StyleRange[0]);
            }
        });

        PlatformUI.getWorkbench().getHelpSystem().setHelp(queryString, "org.eclipse.mat.ui.help.oql");
        queryString.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == '\r' && (e.stateMask & SWT.MOD1) != 0)
                {
                    executeAction.run();
                }
            }

        });
        queryString.addFocusListener(new FocusListener()
        {

            public void focusGained(FocusEvent e)
            {
                IActionBars actionBars = getEditor().getEditorSite().getActionBars();
                actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyQueryStringAction);
                actionBars.updateActionBars();
            }

            public void focusLost(FocusEvent e)
            {}

        });
        queryString.setFocus();

        createContainer(sash);

        sash.setWeights(new int[] { 1, 4 });

        makeActions();
        hookContextMenu();

    }

    private void makeActions()
    {
        executeAction = new ExecuteQueryAction();

        IWorkbenchWindow window = getEditorSite().getWorkbenchWindow();
        IWorkbenchAction globalAction = ActionFactory.COPY.create(window);
        copyQueryStringAction = new Action()
        {
            @Override
            public void run()
            {
               queryString.copy();
            }
        };
        copyQueryStringAction.setAccelerator(globalAction.getAccelerator());
    }

    protected int findInText(String query, int line, int column)
    {
        // index starts at 1
        // tabs count as 8

        int charAt = 0;

        while (line > 1)
        {
            while (charAt < query.length())
            {
                char c = query.charAt(charAt++);
                if (c == '\n')
                {
                    line--;
                    break;
                }
            }
        }

        while (column > 1 && charAt < query.length())
        {
            char c = query.charAt(charAt++);
            if (c == '\t')
                column -= 8;
            else
                column--;
        }

        return charAt;
    }

    private void hookContextMenu()
    {}

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        manager.add(executeAction);

        super.contributeToToolBar(manager);
    }

    @Override
    public void initWithArgument(final Object param)
    {
        if (param instanceof String)
        {
            queryString.setText((String) param);
            executeAction.run();
        }
        else if (param instanceof QueryResult)
        {
            QueryResult queryResult = (QueryResult) param;
            initQueryResult(queryResult, null);
        }
        else if (param instanceof PaneState)
        {
            queryString.setText(((PaneState) param).getIdentifier());
            new ExecuteQueryAction((PaneState) param).run();
        }
    }

    private void initQueryResult(QueryResult queryResult, PaneState state)
    {
        IOQLQuery.Result subject = (IOQLQuery.Result) (queryResult).getSubject();
        queryString.setText(subject.getOQLQuery());

        AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane(subject, this.getClass()); 

        if (state == null)
        {
            for (PaneState child : getPaneState().getChildren())
            {
                if (queryString.getText().equals(child.getIdentifier()))
                {
                    state = child;
                    break;
                }
            }

            if (state == null)
            {
                state = new PaneState(PaneType.COMPOSITE_CHILD, getPaneState(), queryString.getText(), true);
                state.setImage(getTitleImage());
            }
        }

        pane.setPaneState(state);

        createResultPane(pane, queryResult);
    }

    @Override
    public void setFocus()
    {
        queryString.setFocus();        
    }

    // //////////////////////////////////////////////////////////////
    // job to execute query
    // //////////////////////////////////////////////////////////////

    class OQLJob extends AbstractPaneJob
    {
        String queryString;
        PaneState state;

        public OQLJob(AbstractEditorPane pane, String queryString, PaneState state)
        {
            super(queryString.toString(), pane);
            this.queryString = queryString;
            this.state = state;
            this.setUser(true);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            try
            {
                QueryDescriptor descriptor = QueryRegistry.instance().getQuery("oql");
                ArgumentSet argumentSet = descriptor.createNewArgumentSet(getEditor().getQueryContext());
                argumentSet.setArgumentValue("queryString", queryString);
                final QueryResult result = argumentSet.execute(new ProgressMonitorWrapper(monitor));

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        initQueryResult(result, state);
                    }
                });
            }
            catch (final Exception e)
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            createExceptionPane(e, queryString);
                        }
                        catch (PartInitException pie)
                        {
                            ErrorHelper.logThrowable(pie);
                        }
                    }
                });
            }

            return Status.OK_STATUS;
        }
    }

    public void createExceptionPane(Exception cause, String queryString) throws PartInitException
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append("Executed Query:\n");
        buf.append(queryString);

        Throwable t = null;
        if (cause instanceof SnapshotException)
        {
            buf.append("\n\nProblem reported:\n");
            buf.append(cause.getMessage());
            t = cause.getCause();
        }
        else
        {
            t = cause;
        }

        if (t != null)
        {
            buf.append("\n\n");
            StringWriter w = new StringWriter();
            PrintWriter o = new PrintWriter(w);
            t.printStackTrace(o);
            o.flush();

            buf.append(w.toString());
        }

        try
        {
            AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane("TextViewPane");
            if (pane == null)
                throw new PartInitException("TextViewPane not found.");

            // no pane state -> do not include in navigation history
            createResultPane(pane, buf.toString());
        }
        catch (CoreException e)
        {
            throw new PartInitException(ErrorHelper.createErrorStatus(e));
        }
    }

    private class ExecuteQueryAction extends Action
    {
        private PaneState state;

        public ExecuteQueryAction()
        {
            this(null);
        }

        public ExecuteQueryAction(PaneState state)
        {
            this.state = state;
            setText("Execute Query");
            setImageDescriptor(MemoryAnalyserPlugin
                            .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXECUTE_QUERY));
        }

        @Override
        public void run()
        {
            try
            {
                String query = queryString.getSelectionText();
                Point queryRange = queryString.getSelectionRange();

                if ("".equals(query))
                {
                    query = queryString.getText();
                    queryRange = new Point(0, queryString.getCharCount());
                }

                try
                {
                    // force parsing of OQL query
                    SnapshotFactory.createQuery(query);
                    new OQLJob(OQLPane.this, query, state).schedule();
                }
                catch (final OQLParseException e)
                {
                    int start = findInText(query, e.getLine(), e.getColumn());

                    StyleRange style2 = new StyleRange();
                    style2.start = start + queryRange.x;
                    style2.length = queryRange.y - start;
                    style2.foreground = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_RED);
                    queryString.replaceStyleRanges(0, queryString.getCharCount(), new StyleRange[] { style2 });

                    createExceptionPane(e, query);
                }
                catch (Exception e)
                {
                    createExceptionPane(e, query);
                }
            }
            catch (PartInitException e1)
            {
                ErrorHelper.logThrowableAndShowMessage(e1, "Error executing query");
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // methods
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return "OQL";
    }

    @Override
    public Image getTitleImage()
    {
        return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.OQL);
    }
}
