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
package org.eclipse.mat.query.registry;

import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.internal.ReportPlugin;

public class QueryDescriptor
{
    protected final String identifier;
    protected final String name;
    protected final String category;
    protected String usage;
    protected final URL icon;
    protected final String help;
    protected final String helpUrl;
    protected final int sortOrder;

    protected final Class<? extends IQuery> subject;
    protected final List<ArgumentDescriptor> arguments;
    protected final List<QueryDescriptor> menuEntries;

    QueryDescriptor(String identifier, String name, String category, Class<? extends IQuery> subject, String usage,
                    URL icon, String help, String helpUrl)
    {
        this.identifier = identifier;

        if (name == null)
        {
            this.name = null;
            this.sortOrder = Integer.MAX_VALUE;
        }
        else
        {
            int p = name.indexOf('|');
            this.name = p >= 0 ? name.substring(p + 1) : name;
            this.sortOrder = p >= 0 ? Integer.parseInt(name.substring(0, p)) : 100;
        }

        this.category = category;
        this.subject = subject;
        this.usage = usage;
        this.icon = icon;
        this.help = help;
        this.helpUrl = helpUrl;

        this.arguments = new ArrayList<ArgumentDescriptor>();
        this.menuEntries = new ArrayList<QueryDescriptor>();
    }

    public String getIdentifier()
    {
        return identifier;
    }

    public String getName()
    {
        return name;
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

    public synchronized String getUsage(IQueryContext context)
    {
        if (usage != null)
            return usage;
        else
        {

            StringBuilder buf = new StringBuilder(256);

            buf.append(identifier);

            for (ArgumentDescriptor param : arguments)
            {
                if (context.available(param.getType(), param.getAdvice()))
                    continue;

                param.appendUsage(buf);
            }

            return usage = buf.toString();
        }
    }

    public URL getIcon()
    {
        return icon;
    }

    public String getHelp()
    {
        return help;
    }

    public String getHelpUrl()
    {
        return helpUrl;
    }

    /**
     * Indicates whether help is available for the query.
     * 
     * @return true if either the query or at least one of the arguments are
     *         annotated with @Help
     */
    public boolean isHelpAvailable()
    {
        if (help != null)
            return true;

        for (ArgumentDescriptor arg : arguments)
        {
            if (arg.getHelp() != null)
                return true;
        }

        return false;
    }

    public String getShortDescription()
    {
        final int numChars = 80;

        String description = null;

        if (help != null)
        {
            int p = help.indexOf('.');
            if (p >= 0 && p <= numChars)
            {
                description = help.substring(0, p + 1);
            }
            else
            {
                if (help.length() > numChars)
                {
                    p = help.lastIndexOf(' ', numChars);
                    if (p >= 0)
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
                    ReportPlugin.log(IStatus.INFO, MessageFormat.format(Messages.QueryDescriptor_Error_IgnoringQuery,
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
                buf.append(MessageFormat.format(Messages.QueryDescriptor_Error_NotSupported, argument.toString()));
            }
        }

        return buf.toString();
    }

    public List<ArgumentDescriptor> getArguments()
    {
        return Collections.unmodifiableList(arguments);
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

    /* package */void addParamter(ArgumentDescriptor descriptor)
    {
        arguments.add(descriptor);
    }

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
            super(parent.identifier, label, category, parent.subject, parent.usage, icon, help, helpUrl);
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
