/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - move Policy to org.eclipse.mat.ui.internal.browser
 *******************************************************************************/
package org.eclipse.mat.ui.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.internal.browser.Policy;
import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.widgets.Control;

public class QueryContextMenu
{
    private MultiPaneEditor editor;
    private List<ContextProvider> contextProvider;
    private List<DetailResultProvider> resultProvider;

    /** query result might not exist! */
    private QueryResult queryResult;

    public QueryContextMenu(AbstractEditorPane pane, QueryResult result)
    {
        this(pane.getEditor(), result);
    }

    public QueryContextMenu(MultiPaneEditor editor, QueryResult result)
    {
        this.editor = editor;
        this.queryResult = result;

        if (!(result.getSubject() instanceof IStructuredResult))
            throw new UnsupportedOperationException(Messages.QueryContextMenu_SubjectMustBeOfType);

        resultProvider = result.getResultMetaData().getDetailResultProviders();

        contextProvider = result.getResultMetaData().getContextProviders();
        if (contextProvider.isEmpty())
        {
            contextProvider = new ArrayList<ContextProvider>(1);
            contextProvider.add(result.getDefaultContextProvider());
        }
    }

    public QueryContextMenu(MultiPaneEditor editor, ContextProvider provider)
    {
        this.editor = editor;
        resultProvider = new ArrayList<DetailResultProvider>(0);

        contextProvider = new ArrayList<ContextProvider>(1);
        contextProvider.add(provider);
    }

    public QueryContextMenu(AbstractEditorPane pane, ContextProvider provider)
    {
        this(pane.getEditor(), provider);
    }

    public final void addContextActions(PopupMenu manager, IStructuredSelection selection, Control control)
    {
        if (selection.isEmpty())
            return;

        // context result
        resultMenu(manager, selection);

        String label = null;

        // context calculation
        for (ContextProvider p : contextProvider)
        {
            List<IContextObject> menuContext = new ArrayList<IContextObject>();

            for (Iterator<?> iter = selection.iterator(); iter.hasNext();)
            {
                Object selected = iter.next();

                IContextObject ctx = p.getContext(selected);
                if (ctx != null)
                {
                    menuContext.add(ctx);
                }
            }

            if (menuContext.isEmpty())
                continue;

            PopupMenu menu = manager;

            String menuLabel = p.getLabel();

            if (menuLabel != null)
            {
                menu = new PopupMenu(menuLabel);
                URL url = p.getIcon();
                if (url != null)
                {
                    menu.setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(url));
                }
                manager.add(menu);
            }

            // assignment must be lazy, otherwise "foreign" items (like the
            // totals row in the table) that doesn't have any context would
            // cause problems while creating the label
            if (label == null)
            {
                label = getLabel(selection);
            }

            queryMenu(menu, menuContext, label);

            systemMenu(menu, control);

            customMenu(menu, menuContext, p, label);
        }
    }

    private String getLabel(IStructuredSelection selection)
    {
        String label;

        if (queryResult == null)
        {
            label = Messages.QueryContextMenu_context;
        }
        else if (selection.size() == 1)
        {
            IStructuredResult result = (IStructuredResult) queryResult.getSubject();
            Object value = result.getColumnValue(selection.getFirstElement(), 0);
            if (value == null)
            {
                label = MessageUtil.format(Messages.QueryContextMenu_selectionOf,
                                (queryResult.getQuery() != null ? queryResult.getQuery().getName() : queryResult
                                                .getCommand()));
            }
            else
            {
                Column col = result.getColumns()[0];
                String sval;
                if (col.getFormatter() != null)
                {
                    try
                    {
                        sval = col.getFormatter().format(value);
                    }
                    catch (IllegalArgumentException e)
                    {
                        // For example, numeric column with a text value
                        sval = fixLabel(String.valueOf(value));
                    }
                }
                else
                    sval = fixLabel(String.valueOf(value));
                label = MessageUtil.format(Messages.QueryContextMenu_selectionOf,
                                "'" + sval + "'"); //$NON-NLS-2$ //$NON-NLS-1$
            }
        }
        else
        {
            if (queryResult.getQuery() != null)
                label = MessageUtil.format(Messages.QueryContextMenu_selectionOf, queryResult.getQuery().getName());
            else
                label = MessageUtil.format(Messages.QueryContextMenu_selectionOf, queryResult.getCommand());
        }

        return label;
    }

    private void resultMenu(PopupMenu menu, IStructuredSelection selection)
    {
        if (selection.size() == 1)
        {
            boolean hasContextResultProviderMenu = false;
            for (final DetailResultProvider r : resultProvider)
            {
                final Object firstElement = selection.getFirstElement();
                boolean hasResult = r.hasResult(firstElement);
                if (hasResult)
                {
                    final PaneState originator = editor.getActiveEditor().getPaneState();

                    menu.add(new Action(r.getLabel(), r.getIcon() != null ? MemoryAnalyserPlugin.getDefault().getImageDescriptor(r.getIcon()) : null)
                    {
                        @Override
                        public void run()
                        {
                            new AbstractPaneJob(MessageUtil.format(Messages.QueryContextMenu_Processing, r.getLabel()),
                                            null)
                            {

                                @Override
                                protected IStatus doRun(IProgressMonitor monitor)
                                {
                                    try
                                    {
                                        IResult result = r.getResult(firstElement, new ProgressMonitorWrapper(monitor));
                                        QueryResult qr = new QueryResult(null, MessageUtil.format(
                                                        Messages.QueryContextMenu_Details, r.getLabel()), result);
                                        QueryExecution.displayResult(editor, originator, null, qr, false);
                                        return Status.OK_STATUS;
                                    }
                                    catch (SnapshotException e)
                                    {
                                        return ErrorHelper.createErrorStatus(e);
                                    }
                                }

                            }.schedule();
                        }
                    });

                    hasContextResultProviderMenu = true;
                }
            }

            if (hasContextResultProviderMenu)
                menu.addSeparator();
        }
    }

    private void queryMenu(PopupMenu menu, final List<IContextObject> menuContext, String label)
    {
        final IPolicy policy = new Policy(menuContext, label);
        addCategories(menu, QueryRegistry.instance().getRootCategory(), menuContext, label, policy);
        Action queryBrowser = new Action(Messages.QueryDropDownMenuAction_SearchQueries)
        {
            @Override
            public void run()
            {
                new QueryBrowserPopup(editor, false, policy).open();
            }
        };
        queryBrowser.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY));
        queryBrowser.setToolTipText(Messages.QueryDropDownMenuAction_SeachQueriesByName);
        queryBrowser.setActionDefinitionId("org.eclipse.mat.ui.query.browser.QueryBrowser2"); //$NON-NLS-1$
        menu.add(queryBrowser);
    }

    private void addCategories(PopupMenu menu, //
                    CategoryDescriptor root, //
                    List<IContextObject> menuContext, //
                    String label, //
                    IPolicy policy)
    {
        for (Object item : root.getChildren())
        {
            if (item instanceof CategoryDescriptor)
            {
                CategoryDescriptor sub = (CategoryDescriptor) item;
                PopupMenu subManager = new PopupMenu(sub.getName());
                menu.add(subManager);
                addCategories(subManager, sub, menuContext, label, policy);
            }
            else if (item instanceof QueryDescriptor)
            {
                QueryDescriptor query = (QueryDescriptor) item;
                if (policy.accept(query))
                    menu.add(new QueryAction(editor, query, policy));

            }
        }
    }

    private void systemMenu(PopupMenu menu, final Control control)
    {
        // check for null -> copy action might not exist!
        if (control == null)
            return;
        // Choose the same category as the copy address query
        QueryDescriptor qd = QueryRegistry.instance().getQuery("address"); //$NON-NLS-1$
        if (qd != null)
        {
            // Remove any prefix e.g. 101|Copy. Works for pseudo-translated category names
            String menuName = QueryRegistry.instance().getRootCategory().resolve(qd.getCategory()).getName();
            PopupMenu menu2 = menu.getChildMenu(menuName);
            if (menu2 != null)
                menu = menu2;
        }

        Action copySelectionAction = new Action()
        {
            @Override
            public String getText()
            {
                return Messages.QueryContextMenu_Selection;
            }

            @Override
            public void run()
            {
                Copy.copyToClipboard(control);
            }

        };
        copySelectionAction.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.COPY));
        copySelectionAction.setToolTipText(Messages.QueryContextMenu_CopySelectionToTheClipboard);
        menu.add(copySelectionAction);
    }

    /**
     * To be overwritten by sub-classes.
     * 
     * @param menu
     * @param menuContext
     * @param provider
     * @param label
     */
    protected void customMenu(PopupMenu menu, List<IContextObject> menuContext, ContextProvider provider, String label)
    {
    // do nothing, to be overwritten
    }

    /**
     * Matches not spaces, then dot, then capital A-Z, then alphanumeric, then not dot or space, then anything.
     * Strips of the initial part before the capital.
     * Could be used to strip off package names to convert
     * java.lang.String @ 0x12345678 contents
     * to
     * String @ 0x12345678 contents
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("^[^ ]*\\.([A-Z][\\p{Alnum}$]([^ .])*.*)$"); //$NON-NLS-1$

    private static String fixLabel(String label)
    {
        Matcher matcher = PATH_PATTERN.matcher(label);
        label = matcher.matches() ? matcher.group(1) : label;
        return (label.length() > 100) ? label.substring(0, 100) + "..." : label; //$NON-NLS-1$
    }

    public static final class QueryAction extends Action
    {
        MultiPaneEditor editor;
        QueryDescriptor query;
        IPolicy policy;

        public QueryAction(MultiPaneEditor editor, //
                        QueryDescriptor query, //
                        IPolicy policy)
        {
            super(query.getName());
            this.editor = editor;
            this.query = query;
            this.policy = policy;

            this.setToolTipText(query.getShortDescription());
            this.setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(query));
        }

        @Override
        public void run()
        {
            try
            {
                ISnapshot snapshot = (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null);
                ArgumentSet set = query.createNewArgumentSet(editor.getQueryContext());
                policy.fillInObjectArguments(snapshot, query, set);
                QueryExecution.execute(editor, editor.getActiveEditor().getPaneState(), null, set, !query.isShallow(),
                                false);
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }

    }
}
