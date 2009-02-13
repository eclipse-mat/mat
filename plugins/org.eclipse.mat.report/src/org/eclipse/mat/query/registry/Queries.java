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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class Queries
{

    public static Queries lookup(String name, IQueryContext context) throws SnapshotException
    {
        QueryDescriptor query = QueryRegistry.instance().getQuery(name);
        if (query == null)
            throw new SnapshotException(MessageUtil.format(Messages.Queries_Error_NotAvialable, name));

        if (!query.accept(context))
            throw new SnapshotException(query.explain(context));

        ArgumentSet arguments = query.createNewArgumentSet(context);

        return new Queries(arguments);
    }

    public static Queries parse(String commandLine, IQueryContext context) throws SnapshotException
    {
        ArgumentSet arguments = CommandLine.parse(context, commandLine);
        return new Queries(arguments);
    }

    private final QueryDescriptor query;
    private final ArgumentSet arguments;

    private Queries(ArgumentSet arguments)
    {
        this.query = arguments.getQueryDescriptor();
        this.arguments = arguments;
    }

    public Queries set(String name, Object value) throws SnapshotException
    {
        ArgumentDescriptor argument = query.getArgumentByName(name);
        if (argument == null)
            throw new SnapshotException(MessageUtil.format(Messages.Queries_Error_UnknownArgument, name, query
                            .getIdentifier()));

        arguments.setArgumentValue(argument, value);

        return this;
    }

    public QueryResult execute(IProgressListener listener) throws SnapshotException
    {
        return arguments.execute(listener);
    }

}
