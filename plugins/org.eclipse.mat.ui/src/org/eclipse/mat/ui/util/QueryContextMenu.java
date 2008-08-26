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
package org.eclipse.mat.ui.util;

import java.text.MessageFormat;
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
import org.eclipse.mat.internal.snapshot.HeapObjectContextArgument;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.MultiPaneEditor;

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
            throw new UnsupportedOperationException("Subject of QueryResult must be of type IStructuredResult");

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

    public final void addContextActions(PopupMenu manager, IStructuredSelection selection)
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

            customMenu(menu, menuContext, p, label);
        }
    }

    private String getLabel(IStructuredSelection selection)
    {
        String label;

        if (queryResult == null)
        {
            label = "context";
        }
        else if (selection.size() == 1)
        {
            IStructuredResult result = (IStructuredResult) queryResult.getSubject();
            Object value = result.getColumnValue(selection.getFirstElement(), 0);
            if (value == null)
            {
                label = "selection of "
                                + (queryResult.getQuery() != null ? queryResult.getQuery().getName() : queryResult
                                                .getCommand());
            }
            else
            {
                Column col = result.getColumns()[0];
                if (col.getFormatter() != null)
                    label = "selection of '" + col.getFormatter().format(value) + "'";
                else
                    label = "selection of '" + fixLabel(String.valueOf(value)) + "'";
            }
        }
        else
        {
            if (queryResult.getQuery() != null)
                label = "selection of " + queryResult.getQuery().getName();
            else
                label = "selection of " + queryResult.getCommand();
        }

        return label;
    }

    private static class Policy
    {
        private final boolean multiRowSelection;
        private final boolean multiObjectSelection;
        private Class<? extends IContextObject> type;

        public Policy(List<IContextObject> menuContext)
        {
            multiRowSelection = menuContext.size() > 1;
            multiObjectSelection = multiRowSelection || menuContext.get(0) instanceof IContextObjectSet;

            type = IContextObjectSet.class;
            for (IContextObject obj : menuContext)
            {
                if (!IContextObjectSet.class.isAssignableFrom(obj.getClass()))
                {
                    type = IContextObject.class;
                    break;
                }
            }
        }

        public boolean accept(QueryDescriptor query)
        {
            boolean heapObjectArgExists = false;
            boolean heapObjectArgIsMultiple = false;

            boolean contextObjectArgExists = false;
            boolean contextObjectArgIsMultiple = false;

            for (ArgumentDescriptor argument : query.getArguments())
            {
                if (isHeapObject(argument))
                {
                    heapObjectArgExists = true;
                    heapObjectArgIsMultiple = heapObjectArgIsMultiple || argument.isMultiple()
                                    || IHeapObjectArgument.class.isAssignableFrom(argument.getType());
                }
                else if (argument.getType().isAssignableFrom(type))
                {
                    contextObjectArgExists = true;
                    contextObjectArgIsMultiple = contextObjectArgIsMultiple || argument.isMultiple();
                }
            }

            if (!heapObjectArgExists && !contextObjectArgExists)
                return false;
            if (heapObjectArgExists && !heapObjectArgIsMultiple && multiObjectSelection)
                return false;
            if (contextObjectArgExists && !contextObjectArgIsMultiple && multiRowSelection)
                return false;

            return true;
        }

        private boolean isHeapObject(ArgumentDescriptor argument)
        {
            Class<?> argType = argument.getType();

            if (argType.isAssignableFrom(int.class) && argument.getAdvice() == Argument.Advice.HEAP_OBJECT)
                return true;
            if (argType.isAssignableFrom(IObject.class))
                return true;
            if (argType.isAssignableFrom(IHeapObjectArgument.class))
                return true;

            return false;
        }
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

                    menu.add(new Action(r.getLabel())
                    {
                        @Override
                        public void run()
                        {
                            new AbstractPaneJob(MessageFormat.format("Processing: {0}", r.getLabel()), null)
                            {

                                @Override
                                protected IStatus doRun(IProgressMonitor monitor)
                                {
                                    try
                                    {
                                        IResult result = r.getResult(firstElement, new ProgressMonitorWrapper(monitor));
                                        QueryResult qr = new QueryResult(null, MessageFormat.format("Details: {0}", r
                                                        .getLabel()), result);
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

    private void queryMenu(PopupMenu menu, List<IContextObject> menuContext, String label)
    {
        addCategories(menu, QueryRegistry.instance().getRootCategory(), menuContext, label, new Policy(menuContext));
    }

    private void addCategories(PopupMenu menu, //
                    CategoryDescriptor root, //
                    List<IContextObject> menuContext, //
                    String label, //
                    Policy policy)
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
                    menu.add(new QueryAction(editor, query, menuContext, label));

            }
        }
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

    private static final Pattern PATH_PATTERN = Pattern.compile("^[^ ]*\\.([A-Z][\\p{Alnum}$]([^ .])*.*)$");

    private static String fixLabel(String label)
    {
        Matcher matcher = PATH_PATTERN.matcher(label);
        label = matcher.matches() ? matcher.group(1) : label;
        return (label.length() > 100) ? label.substring(0, 100) + "..." : label;
    }

    private static final class QueryAction extends Action
    {
        MultiPaneEditor editor;
        QueryDescriptor query;
        List<IContextObject> context;
        String label;

        public QueryAction(MultiPaneEditor editor, //
                        QueryDescriptor query, //
                        List<IContextObject> context, //
                        String label)
        {
            super(query.getName());
            this.editor = editor;
            this.query = query;
            this.context = context;
            this.label = label;

            this.setToolTipText(query.getShortDescription());
            this.setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(query));
        }

        @Override
        public void run()
        {
            try
            {
                ArgumentSet set = query.createNewArgumentSet(editor.getQueryContext());
                fillInObjectArguments(set);
                QueryExecution.execute(editor, editor.getActiveEditor().getPaneState(), null, set, !query.isShallow(),
                                false);
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }

        private void fillInObjectArguments(ArgumentSet set)
        {
            ISnapshot snapshot = (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null);

            for (ArgumentDescriptor argument : query.getArguments())
            {
                if ((int.class.isAssignableFrom(argument.getType()) && argument.getAdvice() == Argument.Advice.HEAP_OBJECT) //
                                || IObject.class.isAssignableFrom(argument.getType()) //
                                || IHeapObjectArgument.class.isAssignableFrom(argument.getType()))
                {
                    set.setArgumentValue(argument, new HeapObjectContextArgument(snapshot, context, label));
                }
                else if (IContextObjectSet.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        set.setArgumentValue(argument, context);
                    }
                    else
                    {
                        set.setArgumentValue(argument, context.get(0));
                    }
                }
                else if (IContextObject.class.isAssignableFrom(argument.getType()))
                {
                    if (argument.isMultiple())
                    {
                        set.setArgumentValue(argument, context);
                    }
                    else
                    {
                        set.setArgumentValue(argument, context.get(0));
                    }
                }
            }

        }
    }
}
