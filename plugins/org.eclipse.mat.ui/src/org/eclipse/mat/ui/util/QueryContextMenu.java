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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.impl.query.CategoryDescriptor;
import org.eclipse.mat.impl.query.HeapObjectContextArgument;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.impl.query.QueryTarget;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.HeapEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.query.ArgumentContextProvider;
import org.eclipse.mat.ui.internal.query.CopyActions;


public class QueryContextMenu
{
    private HeapEditor editor;
    private List<ContextProvider> contextProvider;

    /** query result might not exist! */
    private QueryResult queryResult;

    public QueryContextMenu(HeapEditorPane pane, QueryResult result)
    {
        this((HeapEditor) ((MultiPaneEditorSite) pane.getEditorSite()).getMultiPageEditor(), result);
    }

    public QueryContextMenu(HeapEditor editor, QueryResult result)
    {
        this.editor = editor;
        this.queryResult = result;

        if (!(result.getSubject() instanceof IStructuredResult))
            throw new UnsupportedOperationException("Subject of QueryResult must be of type IStructuredResult");

        contextProvider = result.getResultMetaData().getContextProviders();
        if (contextProvider.isEmpty())
        {
            contextProvider = new ArrayList<ContextProvider>(1);
            contextProvider.add(result.getDefaultContextProvider());
        }
    }

    public QueryContextMenu(HeapEditor editor, ContextProvider provider)
    {
        this.editor = editor;
        contextProvider = new ArrayList<ContextProvider>(1);
        contextProvider.add(provider);
    }

    public QueryContextMenu(HeapEditorPane pane, ContextProvider provider)
    {
        this((HeapEditor) ((MultiPaneEditorSite) pane.getEditorSite()).getMultiPageEditor(), provider);
    }

    public final void addContextActions(PopupMenu manager, IStructuredSelection selection)
    {
        if (selection.isEmpty())
            return;

        String label = null;

        // prepare menus
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

            copyMenu(menu, menuContext, label);

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

    private void queryMenu(PopupMenu menu, List<IContextObject> menuContext, String label)
    {
        // type of commands
        boolean isSingleObject = menuContext.size() == 1 && !(menuContext.get(0) instanceof IContextObjectSet);
        QueryTarget target = isSingleObject ? QueryTarget.OBJECT : QueryTarget.OBJECT_SET;

        addCategories(menu, QueryRegistry.instance().getRootCategory(), menuContext, label, target);
    }

    private void addCategories(PopupMenu menu, CategoryDescriptor root, List<IContextObject> menuContext, String label,
                    QueryTarget target)
    {
        for (CategoryDescriptor sub : root.getSubCategories())
        {
            PopupMenu subManager = new PopupMenu(sub.getName());
            menu.add(subManager);
            addCategories(subManager, sub, menuContext, label, target);
        }

        for (QueryDescriptor query : root.getQueries())
        {
            if (query.accept(target))
            {
                menu.add(new QueryAction(editor, query, menuContext, label));
            }
        }
    }

    private void copyMenu(PopupMenu menu, List<IContextObject> menuContext, String label)
    {
        menu.addSeparator();
        PopupMenu copy = new PopupMenu("Copy");
        copy.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.COPY));
        menu.add(copy);

        copy.add(new CopyActions.Address(editor.getSnapshotInput().getSnapshot(), menuContext));
        copy.add(new CopyActions.FQClassName(editor.getSnapshotInput().getSnapshot(), menuContext));
        copy.add(new CopyActions.Value(editor.getSnapshotInput().getSnapshot(), menuContext));
    }

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
        HeapEditor editor;
        QueryDescriptor descriptor;
        List<IContextObject> context;
        String label;

        public QueryAction(HeapEditor editor, QueryDescriptor descriptor, List<IContextObject> context, String label)
        {
            super(descriptor.getName());
            this.editor = editor;
            this.descriptor = descriptor;
            this.context = context;
            this.label = label;

            this.setToolTipText(descriptor.getShortDescription());
            this.setImageDescriptor(ImageHelper.getImageDescriptor(descriptor));
        }

        @Override
        public void run()
        {
            try
            {
                ArgumentSet set = descriptor.createNewArgumentSet(new ArgumentContextProvider(editor));

                for (ArgumentDescriptor arg : set.getUnsetArguments())
                {
                    if (arg.isHeapObject())
                    {
                        set.setArgumentValue(arg, new HeapObjectContextArgument(context, label));
                    }
                }

                QueryExecution.execute(editor, set, !descriptor.isShallow(), false);
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }
    }

}
