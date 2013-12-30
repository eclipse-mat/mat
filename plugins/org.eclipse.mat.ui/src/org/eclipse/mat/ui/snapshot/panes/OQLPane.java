/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG., IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Filippo Pacifici - content assistant and syntax highlighting
 *    Andrew Johnson - undo/redo
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.snapshot.panes.oql.OQLTextViewerConfiguration;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ColorProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.OQLPartitionScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.PatchedFastPartitioner;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;

public class OQLPane extends CompositeHeapEditorPane
{
    private SourceViewer queryViewer;
    private StyledText queryString;

    private Color commentCol;
    private Color keywordCol;

    private Action executeAction;
    private Action copyQueryStringAction;
    private Action contentAssistAction;
    
    private static final int UNDO_LEVEL = 10;
    private TextViewerUndoManager undoManager;
    private Action undo;
    private Action redo;
    
    private Map<String, QueryViewAction> actions = new HashMap<String, QueryViewAction>();

    // //////////////////////////////////////////////////////////////
    // initialization methods
    // //////////////////////////////////////////////////////////////

    public void createPartControl(Composite parent)
    {
        SashForm sash = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);

        queryViewer = new SourceViewer(sash, null, SWT.MULTI | SWT.WRAP);
        queryString = queryViewer.getTextWidget();
        queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
        ITheme current = themeManager.getCurrentTheme();
        ColorRegistry colorRegistry = current.getColorRegistry();
        commentCol = colorRegistry.get(ColorProvider.COMMENT_COLOR_PREF);
        keywordCol = colorRegistry.get(ColorProvider.KEYWORD_COLOR_PREF);
        IDocument d = createDocument();
        d.set(Messages.OQLPane_F1ForHelp);

        undoManager = new TextViewerUndoManager(UNDO_LEVEL);
        undoManager.connect(queryViewer);
        queryViewer.setUndoManager(undoManager);
        queryViewer.addSelectionChangedListener(new ISelectionChangedListener()
        {
            public void selectionChanged(SelectionChangedEvent event)
            {
                updateActions();
            }
        });

        queryViewer.setDocument(d);
        queryViewer.configure(new OQLTextViewerConfiguration(getSnapshot(), commentCol, keywordCol));
        queryString.selectAll();

        PlatformUI.getWorkbench().getHelpSystem().setHelp(queryString, "org.eclipse.mat.ui.help.oql"); //$NON-NLS-1$
        queryString.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == '\r' && (e.stateMask & SWT.MOD1) != 0)
                {
                    executeAction.run();
                    e.doit = false;
                }
                else if (e.keyCode == ' ' && (e.stateMask & SWT.CTRL) != 0)
                {
                    //ctrl space combination for content assist
                    contentAssistAction.run();
                }
                else if (e.keyCode == SWT.F5)
                {
                    executeAction.run();
                    e.doit = false;
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

    @Override
    public void dispose()
    {
    }

    /**
     * Creates the document to be associated to the SourceViewer for OQL queries
     * @return the Document instance
     */
    private IDocument createDocument()
    {
        IDocument doc = new Document();
        IDocumentPartitioner partitioner = 
                        new PatchedFastPartitioner(
                                        new OQLPartitionScanner(), 
                                        new String[] {
                                            IDocument.DEFAULT_CONTENT_TYPE,
                                            OQLPartitionScanner.SELECT_CLAUSE,
                                            OQLPartitionScanner.FROM_CLAUSE,
                                            OQLPartitionScanner.WHERE_CLAUSE,
                                            OQLPartitionScanner.UNION_CLAUSE,
                                            OQLPartitionScanner.COMMENT_CLAUSE
                                        });
        partitioner.connect(doc);
        doc.setDocumentPartitioner(partitioner);
        return doc;
    }

    private ISnapshot getSnapshot()
    {
        IQueryContext context = getEditor().getQueryContext();
        ISnapshot snapshot = (ISnapshot)context.get(ISnapshot.class, null);
        return snapshot;
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

        contentAssistAction = new Action()
        {
            @Override
            public void run()
            {
                queryViewer.doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
            }
        };
        // Install the standard text actions.
        addAction(ActionFactory.CUT, ITextOperationTarget.CUT, "org.eclipse.ui.edit.cut");//$NON-NLS-1$
        addAction(ActionFactory.COPY, ITextOperationTarget.COPY, "org.eclipse.ui.edit.copy");//$NON-NLS-1$
        addAction(ActionFactory.PASTE, ITextOperationTarget.PASTE, "org.eclipse.ui.edit.paste");//$NON-NLS-1$
        addAction(ActionFactory.DELETE, ITextOperationTarget.DELETE, "org.eclipse.ui.edit.delete");//$NON-NLS-1$
        addAction(ActionFactory.SELECT_ALL, ITextOperationTarget.SELECT_ALL, "org.eclipse.ui.edit.selectAll");//$NON-NLS-1$
        undo = addAction(ActionFactory.UNDO, ITextOperationTarget.UNDO, "org.eclipse.ui.edit.undo");//$NON-NLS-1$
        redo = addAction(ActionFactory.REDO, ITextOperationTarget.REDO, "org.eclipse.ui.edit.redo");//$NON-NLS-1$
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

    private void updateActions()
    {
        for (QueryViewAction a : actions.values())
            a.setEnabled(queryViewer.canDoOperation(a.actionId));
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                textEditorContextMenuAboutToShow(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(queryViewer.getControl());
        queryString.setMenu(menu);
    }

    private void textEditorContextMenuAboutToShow(IMenuManager manager)
    {
        if (queryString != null)
        {
            undo.setEnabled(undoManager.undoable());
            redo.setEnabled(undoManager.redoable());
            manager.add(undo);
            manager.add(redo);
            manager.add(new Separator());

            manager.add(getAction(ActionFactory.CUT.getId()));
            manager.add(getAction(ActionFactory.COPY.getId()));
            manager.add(getAction(ActionFactory.PASTE.getId()));
            manager.add(new Separator());
            manager.add(getAction(ActionFactory.DELETE.getId()));
            manager.add(getAction(ActionFactory.SELECT_ALL.getId()));
        }
    }

    private Action getAction(String actionID)
    {
        return actions.get(actionID);
    }

    private class QueryViewAction extends Action
    {
        private int actionId;

        QueryViewAction(int actionId, String actionDefinitionId)
        {
            this.actionId = actionId;
            this.setActionDefinitionId(actionDefinitionId);
        }

        @Override
        public boolean isEnabled()
        {
            return queryViewer.canDoOperation(actionId);
        }

        public void run()
        {
            queryViewer.doOperation(actionId);
        }

    }
    
    private Action addAction(ActionFactory actionFactory, int textOperation, String actionDefinitionId)
    {
        IWorkbenchWindow window = getEditorSite().getWorkbenchWindow();
        IWorkbenchAction globalAction = actionFactory.create(window);

        // Create our text action.
        QueryViewAction action = new QueryViewAction(textOperation, actionDefinitionId);
        actions.put(actionFactory.getId(), action);
        // Copy its properties from the global action.
        action.setText(globalAction.getText());
        action.setToolTipText(globalAction.getToolTipText());
        action.setDescription(globalAction.getDescription());
        action.setImageDescriptor(globalAction.getImageDescriptor());

        action.setDisabledImageDescriptor(globalAction.getDisabledImageDescriptor());
        action.setAccelerator(globalAction.getAccelerator());

        // Register our text action with the global action handler.
        IActionBars actionBars = getEditorSite().getActionBars();
        actionBars.setGlobalActionHandler(actionFactory.getId(), action);
        return action;
    }

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
            queryViewer.getDocument().set((String) param);
            executeAction.run();
        }
        else if (param instanceof QueryResult)
        {
            QueryResult queryResult = (QueryResult) param;
            initQueryResult(queryResult, null);
        }
        else if (param instanceof PaneState)
        {
            queryViewer.getDocument().set(((PaneState) param).getIdentifier());
            new ExecuteQueryAction((PaneState) param).run();
        }
    }

    private void initQueryResult(QueryResult queryResult, PaneState state)
    {
        IOQLQuery.Result subject = (IOQLQuery.Result) (queryResult).getSubject();
        queryViewer.getDocument().set(subject.getOQLQuery());

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
                QueryDescriptor descriptor = QueryRegistry.instance().getQuery("oql");//$NON-NLS-1$
                ArgumentSet argumentSet = descriptor.createNewArgumentSet(getEditor().getQueryContext());
                argumentSet.setArgumentValue("queryString", queryString);//$NON-NLS-1$
                final QueryResult result = argumentSet.execute(new ProgressMonitorWrapper(monitor));

                OQLPane.this.queryString.getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        initQueryResult(result, state);
                    }
                });
            }
            catch (final Exception e)
            {
                OQLPane.this.queryString.getDisplay().asyncExec(new Runnable()
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
        buf.append(Messages.OQLPane_ExecutedQuery);
        buf.append(queryString);

        Throwable t = null;
        if (cause instanceof SnapshotException)
        {
            buf.append(Messages.OQLPane_ProblemReported);
            buf.append(cause.getMessage());
            t = cause.getCause();
        }
        else
        {
            t = cause;
        }

        if (t != null)
        {
            buf.append("\n\n");//$NON-NLS-1$
            StringWriter w = new StringWriter();
            PrintWriter o = new PrintWriter(w);
            t.printStackTrace(o);
            o.flush();

            buf.append(w.toString());
        }

        try
        {
            AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane("TextViewPane");//$NON-NLS-1$
            if (pane == null)
                throw new PartInitException(Messages.OQLPane_PaneNotFound);

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
            setText(Messages.OQLPane_ExecuteQuery);
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

                if ("".equals(query))//$NON-NLS-1$
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
                    style2.foreground = JFaceResources.getColorRegistry().get(JFacePreferences.ERROR_COLOR);
                    style2.underline = true;
                    style2.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
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
                ErrorHelper.logThrowableAndShowMessage(e1, Messages.OQLPane_ErrorExecutingQuery);
            }
        }

    }

    // //////////////////////////////////////////////////////////////
    // methods
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return "OQL";//$NON-NLS-1$
    }

    @Override
    public Image getTitleImage()
    {
        return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.OQL);
    }

    @Override
    public AbstractEditorPane getEmbeddedPane()
    {
        return super.getEmbeddedPane();
    }
}
