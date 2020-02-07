/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.BreakIterator;

/**
 * A description of a query to be run on a snapshot, though this class is independent of the actual snapshot
 * and uses IQueryContext.
 */
public class QueryDescriptor extends AnnotatedObjectDescriptor
{
    protected final String category;
    protected final int sortOrder;

    protected final Class<? extends IQuery> subject;
    protected final List<QueryDescriptor> menuEntries;

    QueryDescriptor(String identifier, String name, String category, Class<? extends IQuery> subject, String usage,
                    URL icon, String help, String helpUrl, Locale helpLocale)
    {
    	super(identifier, name, usage, icon, help, helpUrl, helpLocale);
        if (name == null)
        {
            this.name = null;
            this.sortOrder = Integer.MAX_VALUE;
        }
        else
        {
            int p = name.indexOf('|');
            String name1 = p >= 0 ? name.substring(p + 1) : name;
            int sortOrder = 100;
            int end = p;
            // Babel pseudo-translation gives mat123456:101|Java Basics
            // Skip over trailing garbage introduced by pseudo-translation
            while (end > 0 && !Character.isDigit(name.charAt(end - 1))) --end;
            // Extract the longest number - useful for pseudo-translation
            for (int start = 0; start < end; ++start)
            {
                try
                {
                    sortOrder = Integer.parseInt(name.substring(start, end));
                    // Add pseudo-translation prefix to the name
                    name1 = name.substring(0, start) + name1;
                    break;
                }
                catch (NumberFormatException e)
                {}
            }
            this.sortOrder = sortOrder;
            this.name = name1;
        }

        this.category = category;
        this.subject = subject;
        this.usage = usage;

        this.menuEntries = new ArrayList<QueryDescriptor>();
    }

    /**
     * The menu category provided by {@link org.eclipse.mat.query.annotations.Category}.
     * @return the category as a translated string
     */
    public String getCategory()
    {
        return category;
    }

    /**
     * The type of the query object, to be instantiated and the arguments injected when the query is run.
     * @return the type, suitable for instantiation with {@link Class#newInstance()} or {@link java.lang.reflect.Constructor#newInstance()}
     */
    public Class<? extends IQuery> getCommandType()
    {
        return subject;
    }

    /**
     * Create ArgumentSet.
     * @param context The context holding data which could be supplied into the argument set for a query.
     * @return The ArgumentSet for a query holding the data from the context required for the query.
     * @throws SnapshotException if there is a problem creating an argument set from the context.
     */
    public ArgumentSet createNewArgumentSet(IQueryContext context) throws SnapshotException
    {
        return new ArgumentSet(this, context);
    }

    /**
     * A short description of the query - about 80 characters, truncated from the full help
     * at a sentence boundary.
     * @return a translated short description
     */
    public String getShortDescription()
    {
        final int numChars = 80;

        String description = null;

        if (help != null)
        {
            BreakIterator bb = BreakIterator.getSentenceInstance(helpLocale);
            bb.setText(help);
            int p = bb.next();
            if (p >= 0 && p <= numChars)
            {
                // Need trim to remove any trailing new lines which would appear in
                // the query browser as extra blank lines
                description = help.substring(0, p).trim();
            }
            else
            {
                if (help.length() > numChars)
                {
                    bb = BreakIterator.getWordInstance(helpLocale);
                    bb.setText(help);
                    for (int q; (q = bb.next()) <= numChars && q != BreakIterator.DONE;)
                        p = q;
                    if (p >= 0 && p <= numChars)
                        description = help.substring(0, p) + " ..."; //$NON-NLS-1$
                    else
                        description = help.substring(0, numChars) + " ..."; //$NON-NLS-1$
                }
                else
                {
                    description = help;
                }
            }
        }

        return description;
    }

    @Override
    public String toString()
    {
        return new StringBuilder(128).append(identifier).append(" (").append(subject.getName()).append(")").toString(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Can the query be satisfied by the current context, possibly with other user supplied arguments?
     * @param context The data that could be supplied, for example the selected objects.
     * @return true if the query is suitable for use with the current context
     */
    public boolean accept(IQueryContext context)
    {
        for (ArgumentDescriptor argument : arguments)
        {
            if (!context.available(argument.getType(), argument.getAdvice()) && //
                            !context.converts(argument.getType(), argument.getAdvice()))
            {
                if (ReportPlugin.getDefault().isDebugging())
                    ReportPlugin.log(IStatus.INFO, MessageUtil.format(Messages.QueryDescriptor_Error_IgnoringQuery,
                                    getIdentifier(), argument.getName()));
                return false;
            }
        }

        return true;
    }

    /**
     * Explain any parameters which cannot be filled in from the provided context.
     * @param context The data that could be supplied, for example the selected objects.
     * @return a description of the problem arguments
     */
    public String explain(IQueryContext context)
    {
        StringBuilder buf = new StringBuilder();

        for (ArgumentDescriptor argument : arguments)
        {
            if (!context.available(argument.getType(), argument.getAdvice()) && //
                            !context.converts(argument.getType(), argument.getAdvice()))
            {
                if (buf.length() > 0)
                    buf.append('\n');
                buf.append(MessageUtil.format(Messages.QueryDescriptor_Error_NotSupported, argument.toString()));
            }
        }

        return buf.toString();
    }

    public ArgumentDescriptor getArgumentByName(String name)
    {
        for (ArgumentDescriptor argument : arguments)
        {
            if (argument.getName().equals(name))
                return argument;
        }
        return null;
    }

    public List<QueryDescriptor> getMenuEntries()
    {
        return Collections.unmodifiableList(menuEntries);
    }

    /**
     * Whether to not prompt the user for further arguments.
     * @return false if the query is a standard query where the user can be asked for more arguments
     */
    public boolean isShallow()
    {
        return false;
    }

    // //////////////////////////////////////////////////////////////
    // package protected
    // //////////////////////////////////////////////////////////////

    /* package */void addMenuEntry(String label, String category, String help, String helpUrl, URL icon, String options)
    {
        menuEntries.add(new ShallowQueryDescriptor(this, label, category, icon, help, helpUrl, options));
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    /* package */ArgumentDescriptor byFlag(String name)
    {
        for (ArgumentDescriptor d : arguments)
        {
            if (name.equals(d.getFlag()))
                return d;
        }

        return null;
    }

    // //////////////////////////////////////////////////////////////
    // menu entry descriptors
    // //////////////////////////////////////////////////////////////

    /* package */static class ShallowQueryDescriptor extends QueryDescriptor
    {
        private String options;

        private ShallowQueryDescriptor(QueryDescriptor parent, String label, String category, URL icon, String help,
                        String helpUrl, String options)
        {
            super(parent.identifier, label, category, parent.subject, parent.usage, icon, help, helpUrl, parent.helpLocale);
            this.options = options;

            this.arguments.addAll(parent.arguments);
        }

        @Override
        public ArgumentSet createNewArgumentSet(IQueryContext contextProvider) throws SnapshotException
        {
            ArgumentSet answer = super.createNewArgumentSet(contextProvider);
            if (options.length() > 0)
                CommandLine.fillIn(answer, this.options);
            return answer;
        }

        /**
         * A shallow query is one generated by {@link org.eclipse.mat.query.annotations.Menu}.
         */
        @Override
        public boolean isShallow()
        {
            return true;
        }

        @Override
        public String getUsage(IQueryContext context)
        {
            if (options.length() > 0)
            {
                // Insert the options before the argument descriptions
                String cmd = super.getUsage(context);
                return identifier + " " + options + cmd.substring(identifier.length()); //$NON-NLS-1$
            }
            else
            {
                return super.getUsage(context);
            }
        }
    }

}
