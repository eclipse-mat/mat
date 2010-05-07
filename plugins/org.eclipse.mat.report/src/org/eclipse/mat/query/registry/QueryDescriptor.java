/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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
            this.name = p >= 0 ? name.substring(p + 1) : name;
            int sortOrder = 100;
            int end = p;
            // Skip over trailing garbage introduced by pseudo-translation
            while (end > 0 && !Character.isDigit(name.charAt(end - 1))) --end;
            // Extract the longest number - useful for pseudo-translation
            for (int start = 0; start < end; ++start)
            {
                try
                {
                    sortOrder = Integer.parseInt(name.substring(start, end));
                    break;
                }
                catch (NumberFormatException e)
                {}
            }
            this.sortOrder = sortOrder;
        }

        this.category = category;
        this.subject = subject;
        this.usage = usage;

        this.menuEntries = new ArrayList<QueryDescriptor>();
    }

    public String getCategory()
    {
        return category;
    }

    public Class<? extends IQuery> getCommandType()
    {
        return subject;
    }

    /**
     * Create ArgumentSet.
     * 
     * @throws SnapshotException
     */
    public ArgumentSet createNewArgumentSet(IQueryContext context) throws SnapshotException
    {
        return new ArgumentSet(this, context);
    }

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

        @Override
        public boolean isShallow()
        {
            return true;
        }
    }

}
