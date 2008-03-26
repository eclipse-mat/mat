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
package org.eclipse.mat.impl.query;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.snapshot.SnapshotException;


public class QueryDescriptor implements Comparable<QueryDescriptor>
{
    protected final String identifier;
    protected final String name;
    protected final String category;
    protected String usage;
    protected final URL icon;
    protected final String help;
    protected final int sortOrder;

    protected final Class<? extends IQuery> subject;
    protected final List<ArgumentDescriptor> arguments;
    protected final List<QueryDescriptor> menuEntries;

    protected ArgumentDescriptor unflaggedArgument;
    protected ArgumentDescriptor primarySnapshot;

    QueryDescriptor(String identifier, String name, String category, Class<? extends IQuery> subject, String usage,
                    URL icon, String help)
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
            this.sortOrder = p >= 0 ? Integer.parseInt(name.substring(0, p)) : Integer.MAX_VALUE;
        }

        this.category = category;
        this.subject = subject;
        this.usage = usage;
        this.icon = icon;
        this.help = help;

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

    public ArgumentDescriptor getPrimarySnapshotArgument()
    {
        return primarySnapshot;
    }

    public ArgumentDescriptor getUnflaggedArgument()
    {
        return unflaggedArgument;
    }

    public ArgumentSet createNewArgumentSet(IArgumentContextProvider contextProvider) throws SnapshotException
    {
        return new ArgumentSet(this, contextProvider);
    }

    public synchronized String getUsage()
    {
        if (usage != null)
            return usage;
        else
        {

            StringBuilder buf = new StringBuilder(256);

            buf.append(identifier);

            for (ArgumentDescriptor param : arguments)
            {
                if (param == primarySnapshot)
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
                        description = help.substring(0, p) + " ...";
                    else
                        description = help.substring(0, numChars) + " ...";
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
        return new StringBuilder(128).append(name).append(" (").append(subject.getName()).append(")").toString();
    }

    public boolean accept(QueryTarget target)
    {
        // TODO (ab) proper implementation needed

        boolean heapObjectArgExists = false;
        boolean heapObjectArgIsMultiple = false;
        boolean heapObjectArgIsMandatory = false;

        for (ArgumentDescriptor arg : arguments)
        {
            if (arg.isHeapObject())
            {
                heapObjectArgExists = true;
                heapObjectArgIsMultiple = heapObjectArgIsMultiple || arg.isMultiple()
                                || IHeapObjectArgument.class.isAssignableFrom(arg.getType());
                heapObjectArgIsMandatory = heapObjectArgIsMandatory || arg.isMandatory();
            }
        }

        switch (target)
        {
            case SNAPSHOT:
                return !heapObjectArgExists || (heapObjectArgExists && !heapObjectArgIsMandatory);
            case OBJECT_SET:
                return heapObjectArgIsMultiple;
            case OBJECT:
                return heapObjectArgExists;
        }

        return false;
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

    public int compareTo(QueryDescriptor o)
    {
        if (sortOrder < o.sortOrder)
            return -1;
        else if (sortOrder > o.sortOrder)
            return 1;
        else
            return name.compareTo(o.name);
    }

    // //////////////////////////////////////////////////////////////
    // package protected
    // //////////////////////////////////////////////////////////////

    /* package */void addParamter(ArgumentDescriptor descriptor)
    {
        arguments.add(descriptor);
    }

    /* package */void setPrimarySnapshotArgument(ArgumentDescriptor primarySnapshotArgument)
    {
        this.primarySnapshot = primarySnapshotArgument;
    }

    /* package */void setUnflaggedArgument(ArgumentDescriptor mandatoryArgument)
    {
        this.unflaggedArgument = mandatoryArgument;
    }

    /* package */void addMenuEntry(String label, String category, String help, URL icon, String options)
    {
        menuEntries.add(new ShallowQueryDescriptor(this, label, category, icon, help, options));
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
                        String options)
        {
            super(parent.identifier, label, category, parent.subject, parent.usage, icon, help);
            this.options = options;

            this.arguments.addAll(parent.arguments);
            this.primarySnapshot = parent.primarySnapshot;
            this.unflaggedArgument = parent.unflaggedArgument;
        }

        @Override
        public ArgumentSet createNewArgumentSet(IArgumentContextProvider contextProvider) throws SnapshotException
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
