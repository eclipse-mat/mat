/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
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

        boolean shownSystem = false;
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

            if (!menuContext.isEmpty())
                queryMenu(menu, menuContext, label);

            if (!menuContext.isEmpty())
            {
                systemMenu(menu, control);
                shownSystem = true;
            }

            if (!menuContext.isEmpty())
                customMenu(menu, menuContext, p, label);
        }
        if (!shownSystem)
            systemMenu(manager, control);
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
        // Checking for suitable queries for a selection can take time, so set a deadline
        long deadline = System.currentTimeMillis() + 1000;
        addCategories(menu, QueryRegistry.instance().getRootCategory(), menuContext, label, policy, deadline);
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
                    IPolicy policy,
                    long deadline)
    {
        for (Object item : root.getChildren())
        {
            if (item instanceof CategoryDescriptor)
            {
                CategoryDescriptor sub = (CategoryDescriptor) item;
                PopupMenu subManager = new PopupMenu(sub.getName());
                menu.add(subManager);
                addCategories(subManager, sub, menuContext, label, policy, deadline);
            }
            else if (item instanceof QueryDescriptor)
            {
                QueryDescriptor query = (QueryDescriptor) item;
                if (policy.accept(query) && (System.currentTimeMillis() > deadline || !unsuitableSubjects(query, menuContext)))
                    menu.add(new QueryAction(editor, query, policy));

            }
        }
    }

    /**
     * Find the subjects from a query. See {@link Subjects} and {@link Subject}
     * @param query
     * @return a string array
     */
    private static String[] extractSubjects(QueryDescriptor query)
    {
        final String[] cls;
        Subjects subjects = query.getCommandType().getAnnotation(Subjects.class);
        if (subjects != null)
        {
            cls = subjects.value();
        }
        else
        {
            Subject s = query.getCommandType().getAnnotation(Subject.class);
            if (s != null)
            {
                cls = new String[] { s.value() };
            }
            else
            {
                cls = null;
            }
        }
        return cls;
    }

    /**
     * Instanceof test which works for simple objects and arrays
     * @param snapshot
     * @param o the object to be tested
     * @param className is an instance of this class?
     * @return true if an instance
     * @throws SnapshotException
     */
    public boolean instanceOf(ISnapshot snapshot, int o, String className) throws SnapshotException
    {
        IClass cls = snapshot.getClassOf(o);
        return instanceOf(snapshot, cls, className);
    }

    /**
     * Instanceof test which works for simple object and array types
     * @param snapshot
     * @param cls
     * @param className
     * @return true if an instance
     * @throws SnapshotException
     */
    public boolean instanceOf(ISnapshot snapshot, IClass cls, String className) throws SnapshotException
    {
        // simple test via subclasses
        if (cls.doesExtend(className))
            return true;
        /*
         * also consider arrays
         *    int[] instanceof Object
         *    Integer[] instanceof Object
         * are handled above.
         * Also handle:
         * Integer[] instanceof Object[]
         * Integer[][] instanceof Number[][]
         * Integer[][] instanceof Object[]
         * reduce left by array on right then does it extend?
         */
        while (className.endsWith("[]") && cls.isArrayType()) //$NON-NLS-1$
        {
            String n2 = cls.getName();
            String n3 = n2.substring(0, n2.length() - 2);
            className = className.substring(0, className.length() - 2);
            boolean found = false;
            Collection<IClass> classes = snapshot.getClassesByName(n3, false);
            if (classes == null)
                return false;
            for (IClass c2 : classes)
            {
                if (c2.getClassLoaderId() == cls.getClassLoaderId())
                {
                    cls = c2;
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
            if (cls.doesExtend(className))
                return true;
        }
        return false;
    }

    public boolean anyInstances(ISnapshot snapshot, String cn) throws SnapshotException
    {
        Collection<IClass> ss = snapshot.getClassesByName(cn, false);
        if (ss == null || ss.isEmpty())
            return false;
        for (IClass cls : ss)
        {
            if (cls.getNumberOfObjects() > 0)
                return true;
        }
        for (IClass cls : ss)
        {
            if (anySubInstances(cls))
                return true;
        }
        return false;
    }

    public boolean anySubInstances(IClass cls)
    {
        for (IClass cls2 : cls.getSubclasses())
        {
            if (cls2.getNumberOfObjects() > 0)
                return true;
        }
        for (IClass cls2 : cls.getSubclasses())
        {
            if (anySubInstances(cls2))
                return true;
        }
        return false;
    }

    /**
     * See if the menuContext cannot possibly satisfy the requirements of the query.
     * Needs to be reasonably efficient, so if the calculation would take too long
     * then it is okay to return false.
     * @param query
     * @param menuContext
     * @return true if the menuContext is unsuitable.
     */
    public boolean unsuitableSubjects(QueryDescriptor query, List<IContextObject> menuContext)
    {
        final String cls[];
        boolean skip;
        cls = extractSubjects(query);
        if (cls != null)
        {
            ISnapshot snapshot = (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null);
            int count = 0;
            for (String cn : cls)
            {
                try
                {
                    /*
                     * If the classes are not present at all no need to check the objects.
                     */
                    Collection<IClass> ss = snapshot.getClassesByName(cn, false);
                    if (ss == null || ss.isEmpty())
                        continue;
                    count += ss.size();
                    break;
                }
                catch (SnapshotException e)
                {}
            }
            if (count == 0)
                return true;

            // Some of the classes exists in the snapshot so see if the objects are in the selection
            // Don't spend too long checking
            int limit = 1000;
            int inspected = 0;
            boolean skipCheckObjectIds = false;
            List<IContextObject> menuContext2 = new ArrayList<IContextObject>(menuContext);
            Collections.shuffle(menuContext2);
            for (IContextObject ico : menuContext)
            {
                if (inspected >= limit)
                    return false;
                if (ico instanceof IContextObjectSet)
                {
                    IContextObjectSet icos = (IContextObjectSet) ico;
                    {
                        // Test for some well known IContextObjectSets
                        // Could be null
                        Class<?> enclosingClass = icos.getClass().getEnclosingClass();
                        if (Histogram.class.equals(enclosingClass)
                            || Histogram.SuperclassTree.class.equals(enclosingClass)
                            || Histogram.ClassLoaderTree.class.equals(enclosingClass)
                            || Histogram.PackageTree.class.equals(enclosingClass) && icos.getObjectId() != -1)
                        {
                            // Test the whole histogram record without
                            // looking at the items
                            int type = icos.getObjectId();
                            IObject io;
                            try
                            {
                                io = snapshot.getObject(type);
                            }
                            catch (SnapshotException e)
                            {
                                continue;
                            }
                            if (io instanceof IClass)
                            {
                                IClass ic = (IClass)io;
                                for (String cn : cls)
                                {
                                    try
                                    {
                                        if (instanceOf(snapshot, ic, cn))
                                        {
                                            // Class matches, check if we have >= 1 object
                                            if (Histogram.class.equals(enclosingClass))
                                            {
                                                // Whole snapshot histogram?
                                                if (icos.getOQL() != null)
                                                {
                                                    if (ic.getNumberOfObjects() > 0)
                                                        return false;
                                                }
                                            }
                                            if (Histogram.SuperclassTree.class.equals(enclosingClass))
                                            {
                                                // Whole snapshot histogram?
                                                if (icos.getOQL() != null)
                                                {
                                                    if (ic.getNumberOfObjects() > 0 || anySubInstances(ic))
                                                        return false;
                                                }
                                            }
                                            if (Histogram.ClassLoaderTree.class.equals(enclosingClass))
                                            {
                                                // Whole snapshot histogram?
                                                if (icos.getOQL() != null)
                                                {
                                                    if (ic.getNumberOfObjects() > 0)
                                                        return false;
                                                }
                                            }
                                            if (Histogram.PackageTree.class.equals(enclosingClass))
                                            {
                                                // Whole snapshot histogram?
                                                if (icos.getOQL() != null)
                                                {
                                                    if (ic.getNumberOfObjects() > 0)
                                                        return false;
                                                }
                                            }
                                            if (skipCheckObjectIds)
                                                return false;
                                            // Check there is at least one object in the histogram
                                            int os[] = icos.getObjectIds();
                                            if (os.length > 0)
                                                return false;
                                        }
                                        else if (Histogram.SuperclassTree.class.equals(enclosingClass))
                                        {
                                            // Superclass tree, didn't match the base class,
                                            // so test the subclasses too.
                                            for (IClass ic2 : ic.getAllSubclasses())
                                            {
                                                if (instanceOf(snapshot, ic2, cn))
                                                {
                                                    // Whole snapshot histogram?
                                                    if (icos.getOQL() != null)
                                                    {
                                                        if (ic2.getNumberOfObjects() > 0 || anySubInstances(ic2))
                                                            return false;
                                                    }
                                                    if (skipCheckObjectIds)
                                                        return false;
                                                    // Check there is at least one object in the histogram
                                                    int os[] = icos.getObjectIds();
                                                    if (os.length > 0)
                                                        return false;
                                                }
                                            }
                                        }
                                    }
                                    catch (SnapshotException e)
                                    {e.printStackTrace();}
                                }
                                ++inspected;
                            }
                            else if (io instanceof IClassLoader)
                            {
                                IClassLoader icl = (IClassLoader)io;
                                for (String cn : cls)
                                {
                                    try
                                    {
                                        for (IClass ic2 : icl.getDefinedClasses())
                                        {
                                            if (instanceOf(snapshot, ic2, cn))
                                            {
                                                // Whole snapshot histogram?
                                                if (icos.getOQL() != null)
                                                {
                                                    // Check an object is available in the whole dump
                                                    if (ic2.getNumberOfObjects() > 0)
                                                        return false;
                                                }
                                                // The object list could be too huge to examine.
                                                // Play safe, don't know if this class is in the selection.
                                                return false;
                                            }
                                        }
                                    }
                                    catch (SnapshotException e)
                                    {}
                                }
                                ++inspected;
                            }
                        }
                        else if (Histogram.PackageTree.class.equals(enclosingClass) && icos.getObjectId() == -1)
                        {
                            /*
                             * Histogram with the package.
                             * These can be very large.
                             * Enhancement: extract package matching string from getOQL, then get classes
                             * and see if the classes are possibly suitable.
                             */
                            // Extract the OQL pattern
                            String oql = icos.getOQL();
                            String oqlref = OQL.instancesByPattern(Pattern.compile(""), false); //$NON-NLS-1$
                            String oqlPrefix = oqlref.substring(0, oqlref.length() - 1);
                            String oqlSuffix = oqlref.substring(oqlref.length() - 1);
                            if (oql != null && oql.startsWith(oqlPrefix) && oql.endsWith(oqlSuffix) 
                                            && oql.substring(oqlPrefix.length(), oql.length() - oqlSuffix.length()).indexOf('\"') < 0)
                            {
                                String pat = oql.substring(oqlPrefix.length(), oql.length() - oqlSuffix.length());
                                try
                                {
                                    Collection<IClass> classes = snapshot.getClassesByName(Pattern.compile(pat), false);
                                    for (String cn : cls)
                                    {
                                        for (IClass ic2 : classes)
                                        {
                                            try
                                            {
                                                if (instanceOf(snapshot, ic2, cn))
                                                {
                                                    // Whole snapshot histogram?
                                                    if (icos.getOQL() != null)
                                                    {
                                                        // Check an object is available in the whole dump
                                                        if (ic2.getNumberOfObjects() > 0)
                                                            return false;
                                                    }
                                                    // The object list could be too huge to examine.
                                                    // Play safe, don't know if this class is in the selection.
                                                    return false;
                                                }
                                            }
                                            catch (SnapshotException e)
                                            {}
                                        }
                                    }
                                }
                                catch (PatternSyntaxException e)
                                {}
                                catch (SnapshotException e)
                                {}
                                // Not found, so continue to search
                                ++inspected;
                            }
                            else
                            {
                                return false;
                            }
                        }
                        else
                        {
                            // This can be expensive, so don't get it earlier
                            int os[];
                            try
                            {
                                os = icos.getObjectIds();
                            }
                            catch (RuntimeException e)
                            {
                                // For example a OQL select query can fail
                                return false;
                            }
                            inspected += os.length;
                            if (inspected >= limit)
                                return false;
                            for (String cn : cls)
                            {
                                for (int o : os)
                                {
                                    try
                                    {
                                        if (instanceOf(snapshot, o, cn))
                                            return false;
                                    }
                                    catch (SnapshotException e)
                                    {}
                                }
                            }
                        }
                    }
                }
                else
                {
                    // Single object in this line of the selection
                    int o = ico.getObjectId();
                    if (o >= 0)
                    {
                        for (String cn : cls)
                        {
                            try
                            {
                                if (instanceOf(snapshot, o, cn))
                                    return false;
                            }
                            catch (SnapshotException e)
                            {}
                        }
                    }
                    ++inspected;
                }
            }
            skip = true;
        }
        else
        {
            skip = false;
        }
        return skip;
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
            {
                menu = menu2;
            }
            else
            {
                menu2 = new PopupMenu(menuName);
                menu.add(menu2);
                menu = menu2;
            }
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
